package com.vasiliyrogozhin85.posphost;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import androidx.core.content.FileProvider;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.vasiliyrogozhin85.posphost.USB_PERMISSION";
    private static final int REQ_FIRMWARE = 3106;

    private UsbManager usb;
    private TextView status, modeBadge, readinessView, log;
    private ProgressBar readinessBar;
    private Button btnConnect, btnCollect, btnReadiness, btnBootloaderReport, btnBackupPlan, btnRebootBootloader,
            btnFastbootd, btnRecovery, btnFastbootInfo, btnUnlockResearch, btnUnlock, btnRescueSlot, btnFirmware, btnBundle, btnShare;
    private UsbDevice selected;
    private AdbUsbClient adb;
    private FastbootUsbClient fastboot;
    private File lastReport, lastBootloaderReport, lastUnlockAdbReport, lastUnlockFastbootReport, lastBackupPlan, lastBundle;
    private ReadinessReport readiness = new ReadinessReport();
    private final StringBuilder operationLog = new StringBuilder();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false) && d != null) { selected=d; connectSelected(); }
            else append("USB-разрешение не выдано");
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        usb=(UsbManager)getSystemService(USB_SERVICE);
        IntentFilter f = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(usbReceiver, f);
        buildUi(); setButtons();
        append("POSP Host v0.7 готов. Подключите S97 Pro через USB-OTG.");
    }

    private void buildUi() {
        ScrollView page=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(14),dp(16),dp(28)); root.setBackgroundColor(Color.rgb(244,247,250)); page.addView(root);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tb=new LinearLayout(this); tb.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this); title.setText("POSP Host"); title.setTextSize(26); title.setTextColor(Color.rgb(19,45,68));
        TextView sub=new TextView(this); sub.setText("v0.7 • расширенный опрос загрузчика и безопасная подготовка unlock"); sub.setTextSize(13); sub.setTextColor(Color.rgb(94,111,126));
        tb.addView(title); tb.addView(sub); header.addView(tb,new LinearLayout.LayoutParams(0,-2,1));
        Button help=new Button(this); help.setText("? Справка"); help.setAllCaps(false); help.setOnClickListener(v->startActivity(new Intent(this,HelpActivity.class))); header.addView(help); root.addView(header);

        LinearLayout sc=card(); modeBadge=new TextView(this); modeBadge.setText("● НЕ ПОДКЛЮЧЕНО"); modeBadge.setTextSize(13); modeBadge.setTextColor(Color.rgb(160,62,50)); sc.addView(modeBadge);
        status=new TextView(this); status.setText("Подключите целевой телефон через OTG"); status.setTextSize(16); status.setTextColor(Color.rgb(32,51,68)); status.setPadding(0,dp(8),0,0); sc.addView(status); root.addView(sc,cardParams());

        root.addView(sectionTitle("Быстрый старт","Безопасная последовательность действий"));
        LinearLayout q=card();
        btnConnect=actionButton(q,"1  Подключить устройство","Автоопределение ADB или Fastboot",v->scanUsb(),false);
        btnReadiness=actionButton(q,"2  Проверить готовность","S97 Pro / MT6785 / A/B / AVB / bootloader",v->checkReadiness(),false);
        btnCollect=actionButton(q,"3  Собрать полный ADB-рапорт","ZIP с raw-данными и manifest.json",v->collect(),false);
        btnBundle=actionButton(q,"4  Создать диагностический пакет","Объединить отчёты и журнал в один ZIP",v->buildBundle(),false);
        root.addView(q,cardParams());

        root.addView(sectionTitle("Готовность устройства","Оценка текущего состояния"));
        LinearLayout rc=card(); readinessView=new TextView(this); readinessView.setText("Проверка ещё не выполнялась"); readinessView.setTextSize(14); readinessView.setTextColor(Color.rgb(57,75,91)); rc.addView(readinessView);
        readinessBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); readinessBar.setMax(100); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(12)); rp.setMargins(0,dp(12),0,0); rc.addView(readinessBar,rp); root.addView(rc,cardParams());

        root.addView(sectionTitle("Исследование и резервирование","Только чтение и планирование"));
        LinearLayout research=card();
        btnUnlockResearch=actionButton(research,"Подробный опрос для разблокировки","ADB/Fastboot evidence report без изменения устройства",v->unlockResearch(),false);
        btnBootloaderReport=actionButton(research,"Bootloader Analyzer","Расширенный getvar + слоты + partition metadata",v->bootloaderReport(),false);
        btnBackupPlan=actionButton(research,"Создать план резервной копии","Карта разделов и критические области без записи",v->backupPlan(),false);
        root.addView(research,cardParams());

        root.addView(sectionTitle("Переходы режима","ADB/Fastboot → bootloader, fastbootd или recovery"));
        LinearLayout modes=card();
        btnRebootBootloader=actionButton(modes,"Перезагрузить в Bootloader","ADB reboot bootloader",v->adbReboot("bootloader"),false);
        btnFastbootd=actionButton(modes,"Перейти в Fastbootd","Используется для dynamic partitions",v->goFastbootd(),false);
        btnRecovery=actionButton(modes,"Перейти в Recovery","Без прошивки",v->goRecovery(),false);
        root.addView(modes,cardParams());

        root.addView(sectionTitle("Bootloader и восстановление","Изменяющие состояние действия требуют подтверждения"));
        LinearLayout boot=card();
        btnFastbootInfo=actionButton(boot,"Проверить Fastboot / Bootloader","Только чтение переменных",v->fastbootInfo(),false);
        btnUnlock=actionButton(boot,"Разблокировать загрузчик","Только штатный fastboot flashing unlock • стирает данные",v->confirmUnlock(),true);
        btnRescueSlot=actionButton(boot,"Rescue: переключить A/B слот","Только после подтверждения; без прошивки разделов",v->confirmRescueSlot(),true);
        root.addView(boot,cardParams());

        root.addView(sectionTitle("POSP OS","Проверка пакета перед будущей установкой"));
        LinearLayout os=card();
        btnFirmware=actionButton(os,"Проверить пакет POSP OS","Модель/board/hardware + SHA-256 файлов",v->chooseFirmwarePackage(),false);
        btnShare=actionButton(os,"Поделиться последним пакетом","Отправить диагностический ZIP",v->share(),false);
        root.addView(os,cardParams());

        root.addView(sectionTitle("Журнал операций","Сохраняется в диагностический пакет"));
        LinearLayout lc=card(); log=new TextView(this); log.setTextSize(12); log.setTextColor(Color.rgb(45,61,75)); log.setTextIsSelectable(true); log.setMinHeight(dp(220)); lc.addView(log); root.addView(lc,cardParams());
        TextView footer=new TextView(this); footer.setText("POSP Host v0.7 • unlock questionnaire только читает данные; preloader/NVRAM/NVDATA/NVCFG автоматически не стираются и не прошиваются"); footer.setTextSize(11); footer.setTextColor(Color.rgb(110,124,136)); footer.setGravity(Gravity.CENTER); footer.setPadding(0,dp(14),0,0); root.addView(footer);
        setContentView(page);
    }

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));bg.setStroke(dp(1),Color.rgb(224,230,236));c.setBackground(bg);return c;}
    private LinearLayout.LayoutParams cardParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(12));return p;}
    private TextView sectionTitle(String a,String b){TextView t=new TextView(this);t.setText(a+"\n"+b);t.setTextSize(16);t.setTextColor(Color.rgb(28,55,77));t.setPadding(dp(4),dp(8),0,0);return t;}
    private Button actionButton(LinearLayout p,String a,String b,View.OnClickListener l,boolean danger){Button x=new Button(this);x.setAllCaps(false);x.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);x.setText(a+"\n"+b);x.setTextSize(14);x.setTextColor(danger?Color.rgb(143,42,35):Color.rgb(24,61,89));x.setOnClickListener(l);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));p.addView(x,lp);return x;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}

    private void append(String s){String line=stamp()+"  "+s; synchronized(operationLog){operationLog.append(line).append('\n');} runOnUiThread(()->{if(log!=null)log.append(line+"\n");if(status!=null)status.setText(s);});}
    private String stamp(){return new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date());}
    private void setMode(String text,int color){runOnUiThread(()->{modeBadge.setText("● "+text);modeBadge.setTextColor(color);});}
    private void setButtons(){runOnUiThread(()->{boolean a=adb!=null,f=fastboot!=null;btnCollect.setEnabled(a);btnReadiness.setEnabled(a||f);btnBackupPlan.setEnabled(a);btnRebootBootloader.setEnabled(a);btnFastbootInfo.setEnabled(f);btnUnlockResearch.setEnabled(a||f);btnBootloaderReport.setEnabled(f);btnUnlock.setEnabled(f);btnRescueSlot.setEnabled(f);btnFastbootd.setEnabled(a||f);btnRecovery.setEnabled(a||f);btnFirmware.setEnabled(a);btnBundle.setEnabled(lastReport!=null||lastUnlockAdbReport!=null||lastUnlockFastbootReport!=null||lastBootloaderReport!=null||lastBackupPlan!=null);btnShare.setEnabled(lastBundle!=null||lastReport!=null);});}

    private void scanUsb(){closeClients();setMode("ПОИСК USB…",Color.rgb(116,90,20));Map<String,UsbDevice> ds=usb.getDeviceList();if(ds.isEmpty()){append("USB-устройства не найдены. Проверьте OTG и кабель.");setMode("НЕ ПОДКЛЮЧЕНО",Color.rgb(160,62,50));return;}for(UsbDevice d:ds.values()){for(int i=0;i<d.getInterfaceCount();i++){UsbInterface ui=d.getInterface(i);if(AdbUsbClient.isAdbInterface(ui)||FastbootUsbClient.isFastbootInterface(ui)){selected=d;if(!usb.hasPermission(d)){int flags=Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0;PendingIntent pi=PendingIntent.getBroadcast(this,0,new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName()),flags);usb.requestPermission(d,pi);append("Разрешите POSP Host доступ к USB-устройству.");}else connectSelected();return;}}}append("ADB/Fastboot интерфейс не найден.");setMode("РЕЖИМ НЕ ОПОЗНАН",Color.rgb(160,62,50));}
    private void connectSelected(){worker.execute(()->{try{closeClients();boolean a=false,f=false;for(int i=0;i<selected.getInterfaceCount();i++){a|=AdbUsbClient.isAdbInterface(selected.getInterface(i));f|=FastbootUsbClient.isFastbootInterface(selected.getInterface(i));}if(a){setMode("ADB",Color.rgb(39,123,74));append("ADB найден. Выполняю RSA-авторизацию…");adb=AdbUsbClient.open(this,usb,selected);adb.connect();append("ADB подключён: "+adb.shell("getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.hardware; getprop ro.product.board",15000).trim().replace('\n',' '));checkReadinessWorker();}else if(f){fastboot=FastbootUsbClient.open(usb,selected);setMode("FASTBOOT",Color.rgb(45,89,148));append("FASTBOOT подключён: "+summary(selected));checkReadinessWorker();}}catch(Exception e){append("Ошибка подключения: "+e.getMessage());closeClients();setMode("ОШИБКА ПОДКЛЮЧЕНИЯ",Color.rgb(160,62,50));}setButtons();});}

    private void checkReadiness(){worker.execute(this::checkReadinessWorker);}
    private void checkReadinessWorker(){ReadinessReport r=new ReadinessReport();try{if(adb!=null){String product=clean(adb.shell("getprop ro.product.name",8000));String model=clean(adb.shell("getprop ro.product.model",8000));String hw=clean(adb.shell("getprop ro.hardware",8000));String board=clean(adb.shell("getprop ro.product.board",8000));String slot=clean(adb.shell("getprop ro.boot.slot_suffix",8000));String dyn=clean(adb.shell("getprop ro.boot.dynamic_partitions",8000));String vb=clean(adb.shell("getprop ro.boot.verifiedbootstate",8000));String locked=clean(adb.shell("getprop ro.boot.flash.locked",8000));r.add("model","DOOGEE S97 Pro",model.toLowerCase(Locale.US).contains("s97"),model+" / "+product);r.add("hardware","MediaTek MT6785",hw.toLowerCase(Locale.US).contains("mt6785"),hw);r.add("board","Board k85v1_64",board.equalsIgnoreCase("k85v1_64"),board);r.add("ab","A/B slot доступен",!slot.isEmpty(),slot);r.add("dynamic","Dynamic partitions","true".equalsIgnoreCase(dyn),dyn);r.add("avb","AVB состояние определено",!vb.isEmpty(),vb);r.add("bootloader","Bootloader state определён",!locked.isEmpty(),"flash_locked="+locked);r.add("adb","ADB авторизован",true,"shell доступен");}else if(fastboot!=null){String p=fastboot.getVar("product"),u=fastboot.getVar("unlocked"),s=fastboot.getVar("secure"),slot=fastboot.getVar("current-slot");r.add("fastboot","Fastboot доступен",true,summary(selected));r.add("product","Product определён",!p.contains("FAIL"),p);r.add("unlock","Unlock state определён",!u.contains("FAIL"),u);r.add("secure","Secure state определён",!s.contains("FAIL"),s);r.add("slot","Текущий слот определён",!slot.contains("FAIL"),slot);}else r.add("connection","Устройство подключено",false,"Нет ADB/Fastboot");}catch(Exception e){r.add("error","Проверка завершена",false,e.getMessage());}readiness=r;runOnUiThread(()->{readinessView.setText(r.toText());readinessBar.setProgress(r.percent());});append("Проверка готовности: "+r.percent()+"%");}

    private void collect(){if(adb==null)return;worker.execute(()->{try{append("Начинаю расширенный ADB-сбор…");lastReport=ReportCollector.collect(this,adb,summary(selected),this::append,readiness);append("ADB-рапорт готов: "+lastReport.getName()+" SHA-256="+ReportCollector.sha256(lastReport));}catch(Exception e){append("Ошибка сбора: "+e.getMessage());}setButtons();});}
    private void unlockResearch(){if(adb==null&&fastboot==null)return;worker.execute(()->{try{if(adb!=null){append("Unlock Research: собираю ADB-сигналы OEM unlock / AVB / slots…");lastUnlockAdbReport=UnlockResearchCollector.collectAdb(this,adb,summary(selected));append("ADB unlock-опрос готов: "+lastUnlockAdbReport.getName());}else{append("Unlock Research: подробный опрос Fastboot без unlock/flash команд…");lastUnlockFastbootReport=UnlockResearchCollector.collectFastboot(this,fastboot,summary(selected));append("Fastboot unlock-опрос готов: "+lastUnlockFastbootReport.getName());}}catch(Exception e){append("Unlock Research ошибка: "+e.getMessage());}setButtons();});}

    private void bootloaderReport(){if(fastboot==null)return;worker.execute(()->{try{append("Bootloader Analyzer: чтение fastboot-переменных…");lastBootloaderReport=BootloaderAnalyzer.collect(this,fastboot,summary(selected));append("Bootloader-рапорт: "+lastBootloaderReport.getName());}catch(Exception e){append("Bootloader Analyzer ошибка: "+e.getMessage());}setButtons();});}
    private void backupPlan(){if(adb==null)return;worker.execute(()->{try{append("Создаю карту разделов и план резервирования…");lastBackupPlan=BackupPlanner.create(this,adb);append("План резервной копии: "+lastBackupPlan.getName());}catch(Exception e){append("Backup Planner ошибка: "+e.getMessage());}setButtons();});}

    private void adbReboot(String mode){if(adb==null)return;worker.execute(()->{try{adb.shell("reboot "+mode,15000);append("Отправлено: adb reboot "+mode);closeClients();setMode("ОЖИДАНИЕ ПЕРЕПОДКЛЮЧЕНИЯ",Color.rgb(116,90,20));}catch(Exception e){append("ADB reboot ошибка: "+e.getMessage());}setButtons();});}
    private void goFastbootd(){if(adb!=null){adbReboot("fastboot");return;}if(fastboot!=null)worker.execute(()->{try{append("Fastboot: reboot fastboot");append(fastboot.command("reboot fastboot",20000));closeClients();setMode("ОЖИДАНИЕ FASTBOOTD",Color.rgb(116,90,20));}catch(Exception e){append("Fastbootd ошибка: "+e.getMessage());}setButtons();});}
    private void goRecovery(){if(adb!=null){adbReboot("recovery");return;}if(fastboot!=null)worker.execute(()->{try{append("Fastboot: reboot recovery");append(fastboot.command("reboot recovery",20000));closeClients();setMode("ОЖИДАНИЕ RECOVERY",Color.rgb(116,90,20));}catch(Exception e){append("Recovery ошибка: "+e.getMessage());}setButtons();});}
    private void fastbootInfo(){if(fastboot==null)return;worker.execute(()->{try{StringBuilder s=new StringBuilder();for(String v:BootloaderAnalyzer.VARS)s.append(v).append(": ").append(fastboot.getVar(v)).append('\n');append(s.toString());checkReadinessWorker();}catch(Exception e){append("Fastboot ошибка: "+e.getMessage());}});}

    private void confirmUnlock(){if(lastUnlockFastbootReport==null){new AlertDialog.Builder(this).setTitle("Сначала выполните опрос").setMessage("В v0.7 перед unlock рекомендуется выполнить «Подробный опрос для разблокировки» в ADB, затем в Fastboot. Это сохранит исходное состояние и поможет определить штатный путь разблокировки именно этого S97 Pro.").setNegativeButton("Отмена",null).setPositiveButton("Продолжить всё равно",(d,w)->confirmUnlockStage2()).show();return;}confirmUnlockStage2();}
    private void confirmUnlockStage2(){new AlertDialog.Builder(this).setTitle("Разблокировать загрузчик?").setMessage("Операция обычно полностью удаляет данные. Перед продолжением включите OEM unlocking и сохраните важные данные. POSP Host отправит только штатную fastboot flashing unlock и не будет выполнять BROM/FRP обходы.").setNegativeButton("Отмена",null).setPositiveButton("Продолжить",(d,w)->new AlertDialog.Builder(this).setTitle("Последнее подтверждение").setMessage("Подтвердите действие также на экране S97 Pro, если загрузчик это запросит.").setNegativeButton("Отмена",null).setPositiveButton("Отправить unlock",(d2,w2)->doUnlock()).show()).show();}
    private void doUnlock(){if(fastboot==null)return;worker.execute(()->{try{String u=fastboot.getVar("unlocked");append("unlocked до команды: "+u);if(u.toLowerCase(Locale.US).matches(".*(yes|true).*")){append("Загрузчик уже разблокирован.");return;}String r=fastboot.command("flashing unlock",90000);append("Ответ unlock: "+r);if(r.contains("FAIL"))append("Команда отклонена. Автоматические обходы защиты не выполняются.");}catch(Exception e){append("Unlock ошибка: "+e.getMessage());}});}

    private void confirmRescueSlot(){if(fastboot==null)return;worker.execute(()->{try{String cur=fastboot.getVar("current-slot");String c=extractValue(cur);String target="a".equalsIgnoreCase(c)?"b":"a";runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Переключить активный слот?").setMessage("Текущий слот: "+c+"\nБудет выбран: "+target+"\n\nИспользуйте это как Rescue только если второй слот содержит рабочую систему. Разделы не прошиваются.").setNegativeButton("Отмена",null).setPositiveButton("Переключить",(d,w)->setActiveSlot(target)).show());}catch(Exception e){append("Не удалось определить слот: "+e.getMessage());}});}
    private void setActiveSlot(String slot){worker.execute(()->{try{String r=fastboot.command("set_active:"+slot,20000);append("set_active:"+slot+" → "+r);}catch(Exception e){append("Rescue slot ошибка: "+e.getMessage());}});}

    private void chooseFirmwarePackage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_FIRMWARE);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_FIRMWARE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();worker.execute(()->validateFirmwareUri(u));}}
    private void validateFirmwareUri(Uri uri){try{File tmp=new File(getCacheDir(),"candidate_posp_os.zip");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){byte[]b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}String p=clean(adb.shell("getprop ro.product.name",8000));String h=clean(adb.shell("getprop ro.hardware",8000));String b=clean(adb.shell("getprop ro.product.board",8000));FirmwarePackageValidator.Result r=FirmwarePackageValidator.validate(tmp,p,h,b);append("Проверка POSP OS: "+(r.valid?"OK":"НЕ ПРОЙДЕНА")+"\n"+r.message+"\nZIP SHA-256: "+r.sha256);}catch(Exception e){append("Ошибка проверки пакета: "+e.getMessage());}}

    private void buildBundle(){worker.execute(()->{try{List<File> files=new ArrayList<>();files.add(lastReport);files.add(lastUnlockAdbReport);files.add(lastUnlockFastbootReport);files.add(lastBootloaderReport);files.add(lastBackupPlan);String text; synchronized(operationLog){text=operationLog.toString();}lastBundle=DiagnosticBundle.build(this,files,text);append("Диагностический пакет готов: "+lastBundle.getName());}catch(Exception e){append("Ошибка создания пакета: "+e.getMessage());}setButtons();});}
    private void share(){File f=lastBundle!=null?lastBundle:lastReport;if(f==null||!f.exists())return;Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",f);Intent s=new Intent(Intent.ACTION_SEND);s.setType("application/zip");s.putExtra(Intent.EXTRA_STREAM,uri);s.setClipData(ClipData.newRawUri("POSP Host bundle",uri));s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(s,"Отправить POSP Host пакет"));}

    private String extractValue(String s){if(s==null)return "";String x=s.trim();int p=x.lastIndexOf(' ');if(p>=0)x=x.substring(p+1);return x.replace("OKAY","").trim();}
    private String clean(String s){return s==null?"":s.trim();}
    private String summary(UsbDevice d){return d==null?"unknown":String.format(Locale.US,"VID=%04x PID=%04x %s",d.getVendorId(),d.getProductId(),d.getDeviceName());}
    private void closeClients(){try{if(adb!=null)adb.close();}catch(Exception ignored){}try{if(fastboot!=null)fastboot.close();}catch(Exception ignored){}adb=null;fastboot=null;}
    @Override protected void onDestroy(){closeClients();worker.shutdownNow();try{unregisterReceiver(usbReceiver);}catch(Exception ignored){}super.onDestroy();}
}
