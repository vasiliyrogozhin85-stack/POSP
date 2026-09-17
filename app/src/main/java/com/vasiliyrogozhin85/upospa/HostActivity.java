package com.vasiliyrogozhin85.upospa;

import android.app.*;
import android.os.*;
import android.hardware.usb.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;

public class HostActivity extends Activity {
    private static final String USB_PERMISSION="com.vasiliyrogozhin85.upospa.USB_PERMISSION";

    private UsbManager usb;
    private UsbDevice device;
    private AdbUsbClient adb;
    private AoaHostConnection aoa;
    private TextView status, log, live;
    private ReportLogger reporter;
    private File lastClientReport;
    private boolean rec;

    private final BroadcastReceiver usbRx=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            report("USB event "+i.getAction());
            if(USB_PERMISSION.equals(i.getAction())) report("USB permission="+i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false));
            findDevice();
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        UiKit.screen(this);
        reporter=new ReportLogger(this,"Host_v0.8 beta");
        usb=(UsbManager)getSystemService(USB_SERVICE);
        setContentView(ui());
        registerUsb();
        report("onCreate");
        findDevice();
    }

    @Override protected void onResume(){super.onResume();findDevice();}
    @Override protected void onStop(){reporter.exportToDownloads("autosave");super.onStop();}
    @Override protected void onDestroy(){
        closeLinks();
        if(rec)unregisterReceiver(usbRx);
        super.onDestroy();
    }

    private View ui(){
        ScrollView sv=new ScrollView(this); sv.setBackgroundColor(UiKit.BG);
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,28));
        sv.addView(r);

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=UiKit.tv(this,"‹",38,UiKit.HOST,true); back.setGravity(Gravity.CENTER); back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,38),UiKit.dp(this,50)));
        TextView h=UiKit.header(this,"uPOSPa • HOST",UiKit.HOST);
        top.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        TextView gear=UiKit.tv(this,"⚙",25,UiKit.MUTED,false);
        gear.setOnClickListener(v->showHelp());
        top.addView(gear);
        r.addView(top);
        r.addView(UiKit.tv(this,"USB / ADB / Fastboot / Client Link",13,UiKit.MUTED,false));

        r.addView(UiKit.button(this,"✦  WIZARD — автоматический сбор Client",UiKit.CLIENT,v->startActivity(new Intent(this,WizardActivity.class))));

        status=UiKit.tv(this,"●  Поиск подключённого устройства…",15,UiKit.TEXT,true);
        r.addView(UiKit.card(this,status),UiKit.top(this,12));

        live=UiKit.tv(this,"USB ○   ADB ○   Client Link ○   Fastboot ○",12,UiKit.MUTED,true);
        r.addView(UiKit.card(this,live),UiKit.top(this,8));

        r.addView(UiKit.section(this,"Подключение"));
        r.addView(UiKit.button(this,"⌕  1. Найти устройство",UiKit.HOST,v->findDevice()));
        r.addView(UiKit.button(this,"🔗  2. Подключить ADB",UiKit.HOST,v->connectAdb()));
        r.addView(UiKit.button(this,"▶  3. Проверить ADB",UiKit.HOST,
                v->adbShell("echo UPOSPA_ADB_OK && getprop ro.product.manufacturer && getprop ro.product.model",false)));

        r.addView(UiKit.section(this,"Управление через ADB"));
        r.addView(UiKit.darkButton(this,"Android  •  Перезагрузить в Android",v->confirmAdb("Перезагрузка Android","reboot")));
        r.addView(UiKit.darkButton(this,"Layers  •  Перезагрузить в Bootloader",v->confirmAdb("Bootloader","reboot bootloader")));
        r.addView(UiKit.darkButton(this,"↻  Перезагрузить в Recovery",v->confirmAdb("Recovery","reboot recovery")));
        r.addView(UiKit.darkButton(this,">_  Перезагрузить в Fastbootd",v->confirmAdb("Fastbootd","reboot fastboot")));
        r.addView(UiKit.darkButton(this,"▯  Запустить uPOSPa Client",
                v->adbShell("am force-stop com.vasiliyrogozhin85.upospa; sleep 1; am start -n com.vasiliyrogozhin85.upospa/.ClientActivity",false)));

        r.addView(UiKit.section(this,"Fastboot — диагностика"));
        r.addView(UiKit.darkButton(this,"⚡  Прочитать Fastboot getvar",v->queryFastboot()));
        r.addView(UiKit.button(this,"▦  SERVICE: разделы / A-B / Direct IMG",UiKit.WARN,
                v->startActivity(new Intent(this,ServiceActivity.class))));
        r.addView(UiKit.tv(this,"v0.8 beta: Fastboot diagnostics, Partition Map, Slot Manager и подтверждённая запись отдельного IMG.",12,UiKit.MUTED,false));

        r.addView(UiKit.section(this,"Client Link"));
        r.addView(UiKit.button(this,"USB  4. Запустить Client USB (AOA)",UiKit.HOST,v->startAoa()));
        r.addView(UiKit.button(this,"🔗  5. Открыть Client Link",UiKit.HOST,v->openAoa()));
        r.addView(UiKit.button(this,"▮▮▮  6. PING",UiKit.HOST,v->sendAoa(Protocol.PING)));
        r.addView(UiKit.button(this,"▤  7. Запросить отчёт Client",UiKit.HOST,v->sendAoa(Protocol.COLLECT_REPORT)));

        r.addView(UiKit.section(this,"Отчёты"));
        r.addView(UiKit.darkButton(this,"▤  Открыть единый отчёт Host / последнего Client",
                v->openUnifiedReport()));
        r.addView(UiKit.darkButton(this,"↥  Экспортировать журнал Host",
                v->info("Журнал Host",reporter.exportToDownloads("manual"))));

        LinearLayout logCard=UiKit.card(this);
        LinearLayout lh=new LinearLayout(this);
        lh.addView(UiKit.tv(this,"Журнал (Host)",14,UiKit.HOST,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=UiKit.tv(this,"Очистить",12,UiKit.HOST,true);
        clear.setOnClickListener(v->log.setText(""));
        lh.addView(clear);
        logCard.addView(lh);
        log=UiKit.tv(this,"",11,UiKit.MUTED,false); log.setTextIsSelectable(true);
        logCard.addView(log,UiKit.top(this,6));
        r.addView(logCard,UiKit.top(this,10));
        return sv;
    }

    private void registerUsb(){
        IntentFilter f=new IntentFilter();
        f.addAction(USB_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(usbRx,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(usbRx,f);
        rec=true;
    }

    private void findDevice(){
        device=null;
        if(usb==null){status.setText("USB service недоступен");return;}
        for(UsbDevice d:usb.getDeviceList().values()){
            String desc=UsbModeDetector.describe(d);
            report("USB "+hex(d.getVendorId())+":"+hex(d.getProductId())+" "+desc+" ifaces="+d.getInterfaceCount());
            if(UsbModeDetector.hasAdb(d)){device=d;break;}
            if(device==null&&(UsbModeDetector.isAccessory(d)||UsbModeDetector.hasFastboot(d)))device=d;
            if(device==null)device=d;
        }
        if(device==null){
            status.setText("●  Телефон не найден\nПроверьте OTG, кабель и USB debugging.");
            status.setTextColor(UiKit.MUTED);
            live.setText("USB ○   ADB ○   Client Link ○   Fastboot ○");
            return;
        }
        String mode=UsbModeDetector.describe(device);
        status.setText("●  Устройство найдено: "+hex(device.getVendorId())+":"+hex(device.getProductId())+
                "\n"+mode+"   USB permission="+usb.hasPermission(device));
        status.setTextColor(UiKit.HOST);
        updateLive();
        if(!usb.hasPermission(device))requestPermission();
    }

    private void updateLive(){
        boolean has=device!=null;
        boolean ad=has&&UsbModeDetector.hasAdb(device);
        boolean ac=has&&UsbModeDetector.isAccessory(device);
        boolean fb=has&&UsbModeDetector.hasFastboot(device);
        live.setText("USB "+(has?"●":"○")+"   ADB "+(ad?"●":"○")+"   Client Link "+(ac?"●":"○")+"   Fastboot "+(fb?"●":"○"));
        live.setTextColor(has?UiKit.HOST:UiKit.MUTED);
    }

    private void requestPermission(){
        if(device==null)return;
        int flags=Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0;
        PendingIntent pi=PendingIntent.getBroadcast(this,0,new Intent(USB_PERMISSION).setPackage(getPackageName()),flags);
        usb.requestPermission(device,pi);
        report("USB permission requested");
    }

    private void connectAdb(){
        if(device==null||!UsbModeDetector.hasAdb(device)){info("ADB не найден","Включите «Отладку по USB» на Client и переподключите устройство.");return;}
        if(!usb.hasPermission(device)){requestPermission();return;}
        runBg("ADB connect",()->{
            if(adb!=null)adb.close();
            adb=AdbUsbClient.open(this,usb,device);
            adb.connect();
            String target=adb.shell("getprop ro.product.manufacturer; getprop ro.product.model",15000).trim().replace('\n',' ');
            report("ADB CONNECTED target="+target);
            runOnUiThread(()->{
                status.setText("●  ADB подключён\nTarget: "+(target.isEmpty()?"Android device":target));
                status.setTextColor(UiKit.CLIENT);
                live.setText("USB ●   ADB ●   Client Link ○   Fastboot ○");
                live.setTextColor(UiKit.CLIENT);
            });
        });
    }

    private void adbShell(String cmd,boolean noResult){
        runBg("ADB shell "+cmd,()->{
            if(adb==null)throw new IOException("Сначала подключите ADB.");
            String out=adb.shell(cmd,20000);
            report("ADB result: "+out.replace('\n',' '));
            if(!noResult)runOnUiThread(()->info("ADB ответ",out.trim().isEmpty()?"Команда отправлена.":out));
        });
    }

    private void confirmAdb(String title,String cmd){
        new AlertDialog.Builder(this).setTitle(title)
                .setMessage("Команда будет отправлена подключённому телефону:\n\n"+cmd+"\n\nПродолжить?")
                .setNegativeButton("Отмена",null).setPositiveButton("Да",(d,w)->adbShell(cmd,true)).show();
    }

    private void queryFastboot(){
        findDevice();
        if(device==null||!UsbModeDetector.hasFastboot(device)){info("Fastboot не найден","Переведите подключённый телефон в Bootloader/Fastboot или Fastbootd.");return;}
        if(!usb.hasPermission(device)){requestPermission();return;}
        runBg("Fastboot getvar",()->{
            try(FastbootUsbClient fb=FastbootUsbClient.open(usb,device)){
                Map<String,String> vars=fb.querySummary();
                StringBuilder s=new StringBuilder();
                for(Map.Entry<String,String> e:vars.entrySet())s.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                report("FASTBOOT "+s.toString().replace('\n',' '));
                runOnUiThread(()->info("Fastboot данные",s.toString()));
            }
        });
    }

    private void startAoa(){
        if(device==null){info("Нет устройства","Сначала найдите подключённый телефон.");return;}
        if(!usb.hasPermission(device)){requestPermission();return;}
        if(UsbModeDetector.isAccessory(device)){openAoa();return;}
        runBg("AOA start",()->{
            UsbDeviceConnection c=null;
            try{
                c=usb.openDevice(device); if(c==null)throw new IOException("openDevice=null");
                byte[] p=new byte[2];
                int n=c.controlTransfer(0xC0,51,0,0,p,2,1000);
                if(n<0)throw new IOException("Android Open Accessory не поддерживается или отклонён.");
                // Keep old POSP identity for backward compatibility with POSP Client.
                aoaStr(c,0,"POSP");
                aoaStr(c,1,"POSP Client Link");
                aoaStr(c,2,"uPOSPa unified diagnostics bridge");
                aoaStr(c,3,"0.8-beta");
                aoaStr(c,4,"");
                aoaStr(c,5,"UPOSPA-HOST");
                if(c.controlTransfer(0x40,53,0,0,null,0,1000)<0)throw new IOException("AOA START rejected");
                report("AOA START sent");
                runOnUiThread(()->info("Client USB","Client переподключится в Accessory-режиме. Подождите 2–5 секунд, затем нажмите «Найти устройство» и «Открыть Client Link»."));
            }finally{if(c!=null)c.close();}
        });
    }

    private void aoaStr(UsbDeviceConnection c,int i,String s)throws Exception{
        byte[]b=(s+"\0").getBytes("UTF-8");
        if(c.controlTransfer(0x40,52,0,i,b,b.length,1000)<0)throw new IOException("AOA string "+i);
    }

    private void openAoa(){
        findDevice();
        if(device==null||!UsbModeDetector.isAccessory(device)){info("Accessory не найден","Сначала запустите Client USB (AOA) и дождитесь переподключения.");return;}
        if(!usb.hasPermission(device)){requestPermission();return;}
        runBg("AOA open",()->{
            if(aoa!=null)aoa.close();
            aoa=AoaHostConnection.open(usb,device);
            File base=getExternalFilesDir(null); if(base==null)base=getFilesDir();
            File dir=new File(base,"received_client_reports");
            aoa.start(dir,new AoaHostConnection.Listener(){
                public void onLine(String s){report("CLIENT "+s);}
                public void onReport(File f){lastClientReport=f;report("CLIENT REPORT "+f.getAbsolutePath());runOnUiThread(()->info("Отчёт Client получен",f.getAbsolutePath()+"\nТеперь «Единый отчёт» откроет данные этого Client."));}
                public void onError(String s){report("AOA ERROR "+s);}
            });
            report("AOA link opened");
            runOnUiThread(()->{live.setText("USB ●   ADB ○   Client Link ●   Fastboot ○");live.setTextColor(UiKit.CLIENT);});
            sendAoa(Protocol.PING);
        });
    }

    private void openUnifiedReport(){
        Intent i=new Intent(this,ReportActivity.class);
        if(lastClientReport!=null&&lastClientReport.isFile())i.putExtra("report_path",lastClientReport.getAbsolutePath());
        startActivity(i);
    }

    private void sendAoa(String s){
        runBg("AOA send "+s,()->{
            if(aoa==null)throw new IOException("Сначала откройте Client Link.");
            aoa.sendLine(s); report("HOST->CLIENT "+s);
        });
    }

    private void runBg(String name,Task t){
        new Thread(()->{
            try{report(name+" START");t.run();report(name+" OK");}
            catch(Exception e){reporter.throwable(name,e);runOnUiThread(()->info("Ошибка",name+"\n"+(e.getMessage()==null?e.toString():e.getMessage())));}
        },name).start();
    }
    private interface Task{void run()throws Exception;}

    private void closeLinks(){
        try{if(adb!=null)adb.close();}catch(Exception ignored){}
        try{if(aoa!=null)aoa.close();}catch(Exception ignored){}
    }

    private void report(String s){
        reporter.line(s);
        runOnUiThread(()->{if(log!=null){log.append("["+new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"] "+s+"\n");}});
    }

    private void showHelp(){
        info("uPOSPa Host","1. Подключите Client через OTG.\n2. Найдите устройство.\n3. Подключите ADB и подтвердите RSA на Client.\n4. Для отчётов можно переключить Client в AOA.\n5. В v0.8 beta раздел SERVICE умеет карту разделов, Slot Manager и подтверждённую запись отдельного IMG. ZIP/payload автоматически не прошиваются.");
    }

    private void info(String t,String m){new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Понятно",null).show();}
    private String hex(int x){return String.format(Locale.US,"%04X",x);}
}
