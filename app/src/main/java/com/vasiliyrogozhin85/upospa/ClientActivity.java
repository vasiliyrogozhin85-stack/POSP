package com.vasiliyrogozhin85.upospa;

import android.app.*;
import android.os.*;
import android.hardware.usb.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class ClientActivity extends Activity {
    private static final String USB_ACCESSORY_PERMISSION="com.vasiliyrogozhin85.upospa.USB_ACCESSORY_PERMISSION";

    private UsbManager usb;
    private UsbAccessory acc;
    private ParcelFileDescriptor pfd;
    private FileInputStream in;
    private FileOutputStream out;
    private volatile boolean running;
    private TextView status,log,deviceData,wizardStatus;
    private LinearLayout wizardCard;
    private File lastReport;
    private ReportLogger reporter;
    private boolean receiverRegistered;

    private final BroadcastReceiver permissionReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(USB_ACCESSORY_PERMISSION.equals(i.getAction())){
                boolean granted=i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false);
                report("Accessory permission="+granted);
                if(granted)detect(getIntent());
            }
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        UiKit.screen(this);
        reporter=new ReportLogger(this,"Client_v0.8 beta");
        usb=(UsbManager)getSystemService(USB_SERVICE);
        registerPermissionReceiver();
        setContentView(ui());
        report("onCreate");
        handleWizardIntent(getIntent());
        refreshLocalPreview();
        detect(getIntent());
    }

    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);report("onNewIntent "+i.getAction());handleWizardIntent(i);detect(i);}
    @Override protected void onResume(){super.onResume();if(!running)detect(getIntent());}
    @Override protected void onStop(){reporter.exportToDownloads("autosave");super.onStop();}
    @Override protected void onDestroy(){close();if(receiverRegistered)unregisterReceiver(permissionReceiver);super.onDestroy();}

    private View ui(){
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(UiKit.BG);
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,28));
        sv.addView(r);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=UiKit.tv(this,"‹",38,UiKit.HOST,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,38),UiKit.dp(this,50)));
        top.addView(UiKit.header(this,"uPOSPa • CLIENT",UiKit.CLIENT),new LinearLayout.LayoutParams(0,-2,1));
        TextView gear=UiKit.tv(this,"⚙",25,UiKit.MUTED,false);
        gear.setOnClickListener(v->info("uPOSPa Client","Client собирает локальные данные устройства и передаёт отчёт Host через Android Open Accessory. Root не требуется."));
        top.addView(gear);
        r.addView(top);
        r.addView(UiKit.tv(this,"диагностика устройства + связь с Host",13,UiKit.MUTED,false));

        status=UiKit.tv(this,"●  Ожидание uPOSPa Host",15,UiKit.TEXT,true);
        r.addView(UiKit.card(this,status),UiKit.top(this,12));

        wizardCard=UiKit.card(this);
        wizardStatus=UiKit.tv(this,"Wizard Client",14,UiKit.TEXT,true);
        wizardCard.addView(wizardStatus);
        wizardCard.setVisibility(View.GONE);
        r.addView(wizardCard,UiKit.top(this,8));

        r.addView(UiKit.section(this,"Локальная диагностика"));
        r.addView(UiKit.button(this,"▤  Собрать полный отчёт устройства",UiKit.CLIENT,v->collect(false)));
        r.addView(UiKit.button(this,"↥  Собрать и отправить отчёт Host",UiKit.CLIENT,v->collect(true)));
        r.addView(UiKit.button(this,"▤  Экспортировать журнал Client",UiKit.CLIENT,
                v->info("Журнал Client",reporter.exportToDownloads("manual"))));

        r.addView(UiKit.section(this,"Данные отчёта"));
        deviceData=UiKit.tv(this,"Сбор предварительных данных…",13,UiKit.MUTED,false);
        r.addView(UiKit.card(this,deviceData));

        LinearLayout note=UiKit.card(this);
        note.addView(UiKit.tv(this,"ⓘ  Bootloader / Recovery / Fastbootd запускаются Host через ADB.",13,UiKit.MUTED,false));
        r.addView(note,UiKit.top(this,10));

        r.addView(UiKit.darkButton(this,"▤  Открыть единый отчёт и совместимость",
                v->startActivity(new Intent(this,ReportActivity.class))));

        LinearLayout logCard=UiKit.card(this);
        LinearLayout lh=new LinearLayout(this);
        lh.addView(UiKit.tv(this,"Журнал (Client)",14,UiKit.CLIENT,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=UiKit.tv(this,"Очистить",12,UiKit.HOST,true);clear.setOnClickListener(v->log.setText(""));
        lh.addView(clear);logCard.addView(lh);
        log=UiKit.tv(this,"",11,UiKit.MUTED,false);log.setTextIsSelectable(true);logCard.addView(log,UiKit.top(this,6));
        r.addView(logCard,UiKit.top(this,10));
        return sv;
    }

    private void handleWizardIntent(Intent i){
        if(i==null)return;
        final String session=i.getStringExtra("wizard_session");
        final String scenario=i.getStringExtra("wizard_scenario");
        if(i.getBooleanExtra("wizard_waiting",false)){
            showWizardCard("✦  WIZARD • CLIENT\nГотов к автоматическому сбору. Подключите этот телефон к Host кабелем и оставьте uPOSPa открытым.",UiKit.CLIENT);
        }
        if(i.getBooleanExtra("wizard_collect",false)){
            showWizardCard("✦  WIZARD • СБОР ДАННЫХ\nHost запросил локальный DeviceSnapshot. Идёт безопасный сбор технических параметров…",UiKit.HOST);
            collectWizardSnapshot(session==null?"unknown":session,scenario==null?"unknown":scenario);
        }
        if(i.hasExtra("wizard_complete")){
            boolean ok=i.getBooleanExtra("wizard_complete",false);
            String msg=i.getStringExtra("wizard_message");
            if(msg==null||msg.trim().isEmpty())msg=ok?"Сбор данных завершён. Отчёт готов на Host.":"Wizard завершён с ошибкой.";
            showWizardCard((ok?"✓  WIZARD ЗАВЕРШЁН":"!  WIZARD ПРЕРВАН")+"\n"+msg,ok?UiKit.CLIENT:UiKit.ERROR);
            showWizardNotification(ok,msg);
        }
    }

    private void showWizardCard(String text,int color){
        runOnUiThread(()->{
            if(wizardCard!=null){wizardCard.setVisibility(View.VISIBLE);}
            if(wizardStatus!=null){wizardStatus.setText(text);wizardStatus.setTextColor(color);}
        });
    }

    private void collectWizardSnapshot(String session,String scenario){
        new Thread(()->{
            try{
                JSONObject j=DeviceSnapshot.collect(this);
                JSONObject w=new JSONObject();
                w.put("session",session);
                w.put("scenario",scenario);
                w.put("role","client");
                w.put("captured_ms",System.currentTimeMillis());
                j.put("wizard",w);
                File base=getExternalFilesDir(null);
                if(base==null)throw new IOException("external files unavailable");
                File dir=new File(base,"wizard");dir.mkdirs();
                File tmp=new File(dir,"snapshot.json.tmp");
                File outFile=new File(dir,"snapshot.json");
                try(FileOutputStream o=new FileOutputStream(tmp)){o.write(j.toString(2).getBytes("UTF-8"));}
                if(outFile.exists())outFile.delete();
                if(!tmp.renameTo(outFile)){
                    try(FileInputStream in=new FileInputStream(tmp);FileOutputStream o=new FileOutputStream(outFile)){
                        byte[]b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);
                    }
                    tmp.delete();
                }
                try(FileOutputStream o=new FileOutputStream(new File(dir,"ready.txt"))){o.write(session.getBytes("UTF-8"));}
                report("wizard snapshot ready session="+session+" bytes="+outFile.length());
                showWizardCard("✓  WIZARD • ЛОКАЛЬНЫЙ SNAPSHOT ГОТОВ\nHost продолжает автоматическую процедуру. Не отключайте кабель.",UiKit.CLIENT);
            }catch(Exception e){
                reporter.throwable("wizardSnapshot",e);
                showWizardCard("!  WIZARD • ОШИБКА ЛОКАЛЬНОГО SNAPSHOT\n"+e.getMessage()+"\nHost продолжит ADB-диагностику.",UiKit.ERROR);
            }
        },"upospa-wizard-client-snapshot").start();
    }

    private void showWizardNotification(boolean ok,String message){
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(nm==null)return;
            String channel="upospa_wizard";
            if(Build.VERSION.SDK_INT>=26){
                NotificationChannel ch=new NotificationChannel(channel,"uPOSPa Wizard",NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Завершение автоматической диагностики uPOSPa");
                nm.createNotificationChannel(ch);
            }
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                report("Wizard notification permission not granted; in-app result shown");
                return;
            }
            Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
            b.setSmallIcon(R.drawable.ic_upospa)
                    .setContentTitle(ok?"uPOSPa Wizard завершён":"uPOSPa Wizard прерван")
                    .setContentText(message)
                    .setAutoCancel(true);
            nm.notify(8001,b.build());
        }catch(Exception e){report("Wizard notification error: "+e);}
    }

    private void refreshLocalPreview(){
        new Thread(()->{
            try{
                JSONObject j=DeviceSnapshot.collect(this);
                CompatibilityEvaluator.Result ev=CompatibilityEvaluator.evaluate(j);
                JSONObject d=j.optJSONObject("device");
                JSONObject a=j.optJSONObject("android");
                JSONObject m=j.optJSONObject("memory");
                JSONObject s=j.optJSONObject("storage");
                JSONObject b=j.optJSONObject("battery");
                JSONObject p=j.optJSONObject("system_properties");
                String manufacturer=d==null?"—":d.optString("manufacturer","—");
                String model=d==null?"—":d.optString("model","—");
                String android=a==null?"—":a.optString("release","—");
                long ram=m==null?0:m.optLong("total_bytes",0);
                long total=s==null?0:s.optLong("total_bytes",0);
                int cap=b==null?-1:b.optInt("capacity_percent",-1);
                String treble=p==null?"—":p.optString("ro.treble.enabled","—");
                boolean ab=false;
                if(p!=null){
                    ab="true".equalsIgnoreCase(p.optString("ro.build.ab_update",""))||
                       !p.optString("ro.boot.slot_suffix","").isEmpty()||
                       !p.optString("ro.boot.slot","").isEmpty();
                }
                String txt="Производитель:   "+manufacturer+
                        "\nМодель:              "+model+
                        "\nAndroid:             "+android+
                        "\nRAM:                 "+CompatibilityEvaluator.fmtBytes(ram)+
                        "\nПамять:              "+CompatibilityEvaluator.fmtBytes(total)+
                        "\nБатарея:             "+(cap<0?"—":cap+"%")+
                        "\nTreble:              "+treble+
                        "\nA/B:                 "+UiKit.yesNo(ab)+
                        "\n\n"+ev.summary;
                runOnUiThread(()->deviceData.setText(txt));
            }catch(Exception e){runOnUiThread(()->deviceData.setText("Не удалось получить предварительные данные: "+e.getMessage()));}
        }).start();
    }

    private void registerPermissionReceiver(){
        IntentFilter f=new IntentFilter(USB_ACCESSORY_PERMISSION);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(permissionReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(permissionReceiver,f);
        receiverRegistered=true;
    }

    private void detect(Intent i){
        UsbAccessory a=null;
        if(i!=null&&UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(i.getAction())){
            if(Build.VERSION.SDK_INT>=33)a=i.getParcelableExtra(UsbManager.EXTRA_ACCESSORY,UsbAccessory.class);
            else a=i.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        }
        if(a==null&&usb!=null){
            UsbAccessory[]list=usb.getAccessoryList();
            if(list!=null&&list.length>0)a=list[0];
        }
        acc=a;
        if(acc==null){
            status.setText("●  Client готов. Ожидание Host/Android Accessory.");
            status.setTextColor(UiKit.MUTED);
            return;
        }
        if(!usb.hasPermission(acc)){requestAccessoryPermission();return;}
        if(!running)open();
    }

    private void requestAccessoryPermission(){
        int flags=Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0;
        PendingIntent pi=PendingIntent.getBroadcast(this,1,new Intent(USB_ACCESSORY_PERMISSION).setPackage(getPackageName()),flags);
        usb.requestPermission(acc,pi);
        status.setText("●  Требуется разрешение USB Accessory");
        status.setTextColor(UiKit.WARN);
    }

    private void open(){
        close();
        try{
            pfd=usb.openAccessory(acc);if(pfd==null)throw new IOException("openAccessory=null");
            FileDescriptor fd=pfd.getFileDescriptor();
            in=new FileInputStream(fd);out=new FileOutputStream(fd);running=true;
            status.setText("🔗  ●  Client Link подключён к Host\nЧерез USB (Android Accessory)");
            status.setTextColor(UiKit.CLIENT);
            report("Accessory opened "+acc.getManufacturer()+"/"+acc.getModel());
            sendLine(Protocol.HELLO+" CLIENT "+Build.MANUFACTURER+" "+Build.MODEL);
            new Thread(this::listen,"upospa-client-link").start();
        }catch(Exception e){
            reporter.throwable("openAccessory",e);
            status.setText("Ошибка Client Link: "+e.getMessage());
            status.setTextColor(UiKit.ERROR);
        }
    }

    private void listen(){
        try(BufferedReader br=new BufferedReader(new InputStreamReader(in))){
            String s;
            while(running&&(s=br.readLine())!=null){
                String cmd=s.trim();
                report("HOST->CLIENT "+cmd);
                if(Protocol.PING.equals(cmd))sendLine(Protocol.PONG);
                else if(Protocol.COLLECT_REPORT.equals(cmd))collect(true);
            }
        }catch(Exception e){if(running)reporter.throwable("listen",e);}
        finally{
            running=false;
            runOnUiThread(()->{status.setText("●  Связь с Host потеряна");status.setTextColor(UiKit.MUTED);});
        }
    }

    private void collect(boolean send){
        new Thread(()->{
            try{
                lastReport=DeviceSnapshot.collectToFile(this);
                report("report saved "+lastReport.length()+" bytes");
                refreshLocalPreview();
                if(send)sendReport();
                runOnUiThread(()->Toast.makeText(this,"Отчёт собран",Toast.LENGTH_SHORT).show());
            }catch(Exception e){
                reporter.throwable("collect",e);
                runOnUiThread(()->info("Ошибка",e.toString()));
            }
        },"upospa-report").start();
    }

    private void sendReport(){
        if(lastReport==null||!lastReport.exists()){runOnUiThread(()->info("Нет отчёта","Сначала соберите отчёт."));return;}
        if(!running||out==null){runOnUiThread(()->info("Host не подключён","Отчёт сохранён локально, Client Link не активен."));return;}
        File f=lastReport;
        try{
            sendLine(Protocol.REPORT_BEGIN+" "+f.length());
            try(FileInputStream x=new FileInputStream(f)){
                byte[]b=new byte[16384];int n;
                while((n=x.read(b))>0)out.write(b,0,n);
                out.flush();
            }
            sendLine(Protocol.REPORT_END);
            report("report sent "+f.length()+" bytes");
        }catch(Exception e){reporter.throwable("sendReport",e);}
    }

    private synchronized void sendLine(String s)throws Exception{
        if(out==null)throw new IOException("out=null");
        out.write(Protocol.line(s));out.flush();
    }

    private void close(){
        running=false;
        try{if(in!=null)in.close();}catch(Exception ignored){}
        try{if(out!=null)out.close();}catch(Exception ignored){}
        try{if(pfd!=null)pfd.close();}catch(Exception ignored){}
        in=null;out=null;pfd=null;
    }

    private void report(String s){
        reporter.line(s);
        runOnUiThread(()->{if(log!=null)log.append("["+new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"] "+s+"\n");});
    }
    private void info(String t,String m){new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Понятно",null).show();}
}
