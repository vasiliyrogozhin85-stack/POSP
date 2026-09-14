package com.vasiliyrogozhin85.posp.host;
import android.app.*;
import android.os.*;
import android.hardware.usb.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import com.vasiliyrogozhin85.posp.common.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final String USB_PERMISSION="com.vasiliyrogozhin85.posp.host.USB_PERMISSION";
    private final int BG=Color.rgb(7,17,31),SURF=Color.rgb(13,27,42),ACC=Color.rgb(45,168,255),TEXT=Color.rgb(242,247,255),MUTED=Color.rgb(169,184,199);
    private UsbManager usb; private UsbDevice device; private AdbUsbClient adb; private AoaHostConnection aoa;
    private TextView status,log,step; private ReportLogger reporter; private boolean rec;
    private Thread.UncaughtExceptionHandler oldCrash;

    private final BroadcastReceiver usbRx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){report("USB event "+i.getAction());findDevice();}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b); reporter=new ReportLogger(this,"Host_v0.9"); installCrashHandler();
        usb=(UsbManager)getSystemService(USB_SERVICE); setContentView(ui()); registerUsb(); report("onCreate"); findDevice();
    }
    @Override protected void onStart(){super.onStart();report("onStart");}
    @Override protected void onStop(){report("onStop");String p=reporter.exportToDownloads("autosave");report("autosave="+p);super.onStop();}
    @Override protected void onDestroy(){report("onDestroy");closeLinks();if(rec)unregisterReceiver(usbRx);super.onDestroy();}

    private void installCrashHandler(){oldCrash=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler((t,e)->{reporter.throwable("uncaught/"+t.getName(),e);reporter.exportToDownloads("CRASH");if(oldCrash!=null)oldCrash.uncaughtException(t,e);});}

    private View ui(){
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(BG);LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(16),dp(16),dp(16),dp(24));sv.addView(r);
        r.addView(tv("▲ POSP Host",28,TEXT,true));r.addView(tv("v0.9 • мастер подключения и диагностики",13,MUTED,false));
        step=tv("ПОРЯДОК: 1) Client запущен на DOOGEE → 2) Host: Найти DOOGEE → 3) Подключить ADB → 4) принять RSA на DOOGEE → 5) Проверка ADB → дальше нужная команда.",15,TEXT,true);r.addView(card(step),top(12));
        status=tv("Поиск устройства…",15,TEXT,true);r.addView(card(status),top(8));

        r.addView(section("Шаг 1 — подготовка"));
        r.addView(btn("1. Найти DOOGEE",v->findDevice()));
        r.addView(btn("2. Подключить ADB",v->connectAdb()));
        r.addView(btn("3. Проверить ADB",v->adbShell("echo POSP_ADB_OK && getprop ro.product.model",false)));

        r.addView(section("Управление DOOGEE через ADB"));
        r.addView(btn("Перезагрузить DOOGEE в Android",v->confirmAdb("Перезагрузка DOOGEE","reboot")));
        r.addView(btn("Перезагрузить в Bootloader",v->confirmAdb("Bootloader","reboot bootloader")));
        r.addView(btn("Перезагрузить в Recovery",v->confirmAdb("Recovery","reboot recovery")));
        r.addView(btn("Перезагрузить в Fastbootd",v->confirmAdb("Fastbootd","reboot fastboot")));
        r.addView(btn("Перезапустить POSP Client",v->adbShell("am force-stop com.vasiliyrogozhin85.posp.client; sleep 1; am start -n com.vasiliyrogozhin85.posp.client/.MainActivity",false)));

        r.addView(section("Client Link — отчёты по USB"));
        r.addView(tv("Используйте после проверки ADB. На некоторых телефонах AOA меняет USB-режим. Если ADB пропал, нажмите «Найти DOOGEE» и «Подключить ADB» снова.",13,MUTED,false));
        r.addView(btn("4. Запустить Client USB (AOA)",v->startAoa()));
        r.addView(btn("5. Открыть Client Link",v->openAoa()));
        r.addView(btn("6. Проверить связь PING",v->sendAoa(Protocol.PING)));
        r.addView(btn("7. Запросить отчёт Client",v->sendAoa(Protocol.COLLECT_REPORT)));

        r.addView(section("Диагностика"));
        r.addView(btn("Экспортировать отчёт Host в Download/POSPReports",v->{String p=reporter.exportToDownloads("manual");info("Отчёт Host",p);}));
        r.addView(btn("? Подробная инструкция",v->showHelp()));

        log=tv("Журнал:\n",12,MUTED,false);log.setTextIsSelectable(true);r.addView(card(log),top(8));
        return sv;
    }

    private void registerUsb(){IntentFilter f=new IntentFilter();f.addAction(USB_PERMISSION);f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);if(Build.VERSION.SDK_INT>=33)registerReceiver(usbRx,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(usbRx,f);rec=true;}
    private void findDevice(){
        device=null;
        for(UsbDevice d:usb.getDeviceList().values()){
            String desc=UsbModeDetector.describe(d);report("USB "+hex(d.getVendorId())+":"+hex(d.getProductId())+" "+desc+" ifaces="+d.getInterfaceCount());
            if(UsbModeDetector.hasAdb(d)){device=d;break;}
            if(device==null&&(UsbModeDetector.isAccessory(d)||UsbModeDetector.hasFastboot(d)))device=d;
        }
        if(device==null){status.setText("● DOOGEE не найден. Проверьте OTG/кабель/USB debugging.");status.setTextColor(MUTED);return;}
        status.setText("● USB найден: "+hex(device.getVendorId())+":"+hex(device.getProductId())+"\n"+UsbModeDetector.describe(device)+"\nUSB permission="+usb.hasPermission(device));status.setTextColor(ACC);
        if(!usb.hasPermission(device))requestPermission();
    }
    private void requestPermission(){if(device==null)return;PendingIntent pi=PendingIntent.getBroadcast(this,0,new Intent(USB_PERMISSION),PendingIntent.FLAG_IMMUTABLE);usb.requestPermission(device,pi);report("USB permission requested");}
    private void connectAdb(){
        if(device==null||!UsbModeDetector.hasAdb(device)){info("ADB не найден","Убедитесь, что на DOOGEE включена «Отладка по USB». После AOA может потребоваться переподключение.");return;}
        if(!usb.hasPermission(device)){requestPermission();return;}
        runBg("ADB connect",()->{if(adb!=null)adb.close();adb=AdbUsbClient.open(this,usb,device);adb.connect();report("ADB CONNECTED");runOnUiThread(()->{status.setText("● ADB подключён. Теперь работают перезагрузка и команды.");status.setTextColor(ACC);info("ADB подключён","Если на DOOGEE появлялся RSA-запрос, его нужно разрешить. Кнопки перезагрузки теперь активны.");});});
    }
    private void adbShell(String cmd,boolean noResult){runBg("ADB shell "+cmd,()->{if(adb==null)throw new IOException("Сначала нажмите «2. Подключить ADB»");String out=adb.shell(cmd,15000);report("ADB result: "+out.replace('\n',' '));if(!noResult)runOnUiThread(()->info("ADB ответ",out.trim().isEmpty()?"Команда отправлена.":out));});}
    private void confirmAdb(String title,String cmd){new AlertDialog.Builder(this).setTitle(title).setMessage("Команда будет отправлена по ADB: "+cmd+"\n\nПродолжить?").setNegativeButton("Отмена",null).setPositiveButton("Да",(d,w)->adbShell(cmd,true)).show();}
    private void startAoa(){
        if(device==null){info("Нет устройства","Нажмите «1. Найти DOOGEE».");return;}if(!usb.hasPermission(device)){requestPermission();return;}
        if(UsbModeDetector.isAccessory(device)){openAoa();return;}
        runBg("AOA start",()->{UsbDeviceConnection c=null;try{c=usb.openDevice(device);if(c==null)throw new IOException("openDevice=null");byte[]p=new byte[2];int n=c.controlTransfer(0xC0,51,0,0,p,2,1000);if(n<0)throw new IOException("GET_PROTOCOL rejected");aoaStr(c,0,"POSP");aoaStr(c,1,"POSP Client Link");aoaStr(c,2,"POSP diagnostics bridge");aoaStr(c,3,"1.0");aoaStr(c,4,"");aoaStr(c,5,"POSP-HOST");if(c.controlTransfer(0x40,53,0,0,null,0,1000)<0)throw new IOException("AOA START rejected");report("AOA START sent");runOnUiThread(()->info("Client USB","DOOGEE переподключится. Подождите 2–5 секунд, затем нажмите «1. Найти DOOGEE» и «5. Открыть Client Link»."));}finally{if(c!=null)c.close();}});
    }
    private void aoaStr(UsbDeviceConnection c,int i,String s)throws Exception{byte[]b=(s+"\0").getBytes("UTF-8");if(c.controlTransfer(0x40,52,0,i,b,b.length,1000)<0)throw new IOException("AOA string "+i);}
    private void openAoa(){findDevice();if(device==null||!UsbModeDetector.isAccessory(device)){info("Accessory не найден","Сначала нажмите «4. Запустить Client USB (AOA)», подождите переподключения и повторите.");return;}if(!usb.hasPermission(device)){requestPermission();return;}runBg("AOA open",()->{if(aoa!=null)aoa.close();aoa=AoaHostConnection.open(usb,device);File base=getExternalFilesDir(null);if(base==null)base=getFilesDir();File dir=new File(base,"client_reports");aoa.start(dir,new AoaHostConnection.Listener(){public void onLine(String s){report("CLIENT "+s);}public void onReport(File f){report("CLIENT REPORT "+f.getAbsolutePath());runOnUiThread(()->info("Отчёт Client получен",f.getName()+"\nОн сохранён внутри Host. Для анализа проще также экспортировать отчёт из самого Client в Download/POSPReports."));}public void onError(String s){report("AOA ERROR "+s);}});report("AOA link opened");sendAoa(Protocol.PING);});}
    private void sendAoa(String s){runBg("AOA send "+s,()->{if(aoa==null)throw new IOException("Сначала нажмите «5. Открыть Client Link»");aoa.sendLine(s);report("HOST->CLIENT "+s);});}
    private void runBg(String name,Task t){new Thread(()->{try{report(name+" START");t.run();report(name+" OK");}catch(Exception e){reporter.throwable(name,e);runOnUiThread(()->info("Ошибка",name+"\n"+e.getMessage()));}},name).start();}
    private interface Task{void run()throws Exception;}
    private void closeLinks(){try{if(adb!=null)adb.close();}catch(Exception ignored){}try{if(aoa!=null)aoa.close();}catch(Exception ignored){}}
    private void report(String s){reporter.line(s);runOnUiThread(()->{if(log!=null)log.append(s+"\n");});}
    private void showHelp(){info("Порядок работы","НА DOOGEE:\n1. Включите «Отладка по USB».\n2. Запустите POSP Client и оставьте его открытым.\n\nНА HOST:\n3. Подключите телефоны OTG-кабелем.\n4. Нажмите «1. Найти DOOGEE».\n5. Нажмите «2. Подключить ADB».\n6. Если DOOGEE покажет запрос RSA — отметьте «Всегда разрешать» и нажмите OK.\n7. Нажмите «3. Проверить ADB».\n\nПЕРЕЗАГРУЗКА:\n8. После успешной проверки ADB можно нажимать «Перезагрузить DOOGEE…», Bootloader, Recovery или Fastbootd.\n\nОТЧЁТЫ CLIENT LINK:\n9. Нажмите «4. Запустить Client USB (AOA)».\n10. Подождите переподключения USB.\n11. Нажмите «1. Найти DOOGEE».\n12. Нажмите «5. Открыть Client Link».\n13. Нажмите «6. Проверить связь PING».\n14. Нажмите «7. Запросить отчёт Client».\n\nОТЧЁТЫ ДЛЯ АНАЛИЗА:\nHost и Client автоматически создают сессионный отчёт при запуске и сохраняют снимок при уходе приложения в фон. В обоих приложениях нажмите «Экспортировать отчёт». Затем загрузите сюда файлы из Download/POSPReports.");}
    private void info(String t,String m){new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Понятно",null).show();}
    private View card(View v){LinearLayout l=new LinearLayout(this);l.setPadding(dp(14),dp(14),dp(14),dp(14));l.setBackgroundColor(SURF);l.addView(v);return l;}
    private TextView section(String s){TextView v=tv(s,17,TEXT,true);v.setPadding(0,dp(18),0,dp(6));return v;}
    private Button btn(String s,View.OnClickListener c){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEXT);b.setBackgroundColor(Color.rgb(18,40,58));b.setOnClickListener(c);b.setLayoutParams(top(6));return b;}
    private TextView tv(String s,int z,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private LinearLayout.LayoutParams top(int n){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(n);return p;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
    private String hex(int x){return String.format(Locale.US,"%04X",x);}
}
