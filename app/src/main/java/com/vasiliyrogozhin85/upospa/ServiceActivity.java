package com.vasiliyrogozhin85.upospa;

import android.app.*;
import android.os.*;
import android.content.*;
import android.hardware.usb.*;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;

public class ServiceActivity extends Activity {
    private static final int PICK_IMAGE=71;
    private static final String USB_PERMISSION="com.vasiliyrogozhin85.upospa.SERVICE_USB_PERMISSION";

    private UsbManager usb;
    private UsbDevice device;
    private TextView status, details, progress;
    private ProgressBar bar;
    private Uri selectedUri;
    private FirmwareAnalyzer.Result selectedInfo;
    private String expectedDevice="", expectedProduct="";
    private boolean rec;

    private final BroadcastReceiver rx=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){scan();}
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        UiKit.screen(this);
        usb=(UsbManager)getSystemService(USB_SERVICE);
        setContentView(ui());
        registerUsb();
        scan();
        expectedDevice=clean(getIntent().getStringExtra("expected_device"));
        expectedProduct=clean(getIntent().getStringExtra("expected_product"));
        String incoming=getIntent().getStringExtra("image_uri");
        if(incoming!=null&&!incoming.isEmpty()){
            selectedUri=Uri.parse(incoming);
            analyzeIncomingImage();
        }
    }

    @Override protected void onDestroy(){if(rec)unregisterReceiver(rx);super.onDestroy();}

    private View ui(){
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(UiKit.BG);
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,28));sv.addView(r);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=UiKit.tv(this,"‹",38,UiKit.HOST,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,38),UiKit.dp(this,50)));
        top.addView(UiKit.header(this,"uPOSPa • SERVICE",UiKit.WARN),new LinearLayout.LayoutParams(0,-2,1));
        r.addView(top);
        r.addView(UiKit.tv(this,"Fastboot / Partition Map / Slot Manager / Direct IMG",13,UiKit.MUTED,false));

        status=UiKit.tv(this,"Поиск Fastboot-устройства…",15,UiKit.TEXT,true);
        r.addView(UiKit.card(this,status),UiKit.top(this,12));

        r.addView(UiKit.section(this,"Fastboot target"));
        r.addView(UiKit.button(this,"⌕  Обновить подключение",UiKit.HOST,v->scan()));
        r.addView(UiKit.darkButton(this,"⚡  Прочитать основные getvar",v->readSummary()));
        r.addView(UiKit.darkButton(this,"▦  Построить карту разделов",v->readPartitions()));

        r.addView(UiKit.section(this,"Slot Manager A/B"));
        r.addView(UiKit.darkButton(this,"A/B  Прочитать состояние слотов",v->readSlots()));
        LinearLayout slots=new LinearLayout(this);slots.setOrientation(LinearLayout.HORIZONTAL);
        Button a=UiKit.button(this,"Сделать активным A",UiKit.SURF2,v->confirmSlot("a"));
        Button bb=UiKit.button(this,"Сделать активным B",UiKit.SURF2,v->confirmSlot("b"));
        slots.addView(a,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.leftMargin=UiKit.dp(this,8);slots.addView(bb,bp);
        r.addView(slots,UiKit.top(this,6));

        r.addView(UiKit.section(this,"Install Engine v0.8 beta — Direct IMG"));
        r.addView(UiKit.button(this,"▤  Выбрать .img",UiKit.HOST,v->pickImage()));
        r.addView(UiKit.button(this,"⚠  ПРОВЕРИТЬ И ПРОШИТЬ ОБРАЗ",UiKit.WARN,v->preflightFlash()));
        r.addView(UiKit.tv(this,"uPOSPa определяет раздел по имени образа, повторно читает product/unlocked и требует двойное подтверждение. ZIP/payload здесь не записываются.",12,UiKit.MUTED,false));

        bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(0);
        r.addView(bar,UiKit.top(this,10));
        progress=UiKit.tv(this,"Ожидание операции",12,UiKit.MUTED,true);r.addView(progress,UiKit.top(this,5));

        details=UiKit.tv(this,"Данные Fastboot появятся здесь.",12,UiKit.MUTED,false);details.setTextIsSelectable(true);
        r.addView(UiKit.card(this,details),UiKit.top(this,10));
        return sv;
    }

    private void registerUsb(){
        IntentFilter f=new IntentFilter();f.addAction(USB_PERMISSION);f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(rx,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(rx,f);rec=true;
    }

    private void scan(){
        device=null;
        if(usb!=null)for(UsbDevice d:usb.getDeviceList().values())if(UsbModeDetector.hasFastboot(d)){device=d;break;}
        if(device==null){status.setText("●  Fastboot не найден\nПереведите Client в Bootloader/Fastboot или Fastbootd.");status.setTextColor(UiKit.MUTED);return;}
        status.setText("●  Fastboot найден: "+String.format(Locale.US,"%04X:%04X",device.getVendorId(),device.getProductId())+"\nUSB permission="+usb.hasPermission(device));
        status.setTextColor(UiKit.HOST);
        if(!usb.hasPermission(device))requestPermission();
    }

    private void requestPermission(){
        if(device==null)return;
        int flags=Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0;
        PendingIntent pi=PendingIntent.getBroadcast(this,7,new Intent(USB_PERMISSION).setPackage(getPackageName()),flags);
        usb.requestPermission(device,pi);
    }

    private FastbootUsbClient open() throws Exception {
        scan();
        if(device==null)throw new IOException("Fastboot device not found");
        if(!usb.hasPermission(device)){requestPermission();throw new IOException("Разрешите доступ USB и повторите операцию");}
        return FastbootUsbClient.open(usb,device);
    }

    private void readSummary(){runBg("getvar",()->{
        try(FastbootUsbClient fb=open()){
            Map<String,String> m=fb.querySummary();
            showMap("Fastboot target",m);
        }
    });}

    private void readSlots(){runBg("slots",()->{
        try(FastbootUsbClient fb=open()){showMap("Slot Manager",fb.querySlotState());}
    });}

    private void readPartitions(){runBg("partitions",()->{
        try(FastbootUsbClient fb=open()){
            List<FastbootUsbClient.PartitionEntry> xs=fb.queryPartitionMap();
            StringBuilder s=new StringBuilder("PARTITION MAP\n");
            for(FastbootUsbClient.PartitionEntry x:xs)s.append(x.name).append("   size=").append(x.size).append("   type=").append(x.type).append('\n');
            if(xs.isEmpty())s.append("Устройство не отдало partition-size для известных разделов.");
            runOnUiThread(()->details.setText(s.toString()));
        }
    });}

    private void confirmSlot(String slot){
        new AlertDialog.Builder(this).setTitle("Переключить active slot на "+slot.toUpperCase(Locale.US))
                .setMessage("Изменение активного слота может привести к неудачной загрузке, если выбранный слот повреждён. uPOSPa не очищает данные. Продолжить?")
                .setNegativeButton("Отмена",null).setPositiveButton("Переключить",(d,w)->runBg("set_active",()->{
                    try(FastbootUsbClient fb=open()){fb.setActive(slot);Map<String,String> m=fb.querySlotState();showMap("Slot переключён",m);}
                })).show();
    }

    private void analyzeIncomingImage(){
        progress.setText("Анализ образа из Install Plan…");
        runBg("incoming image",()->{
            FirmwareAnalyzer.Result x=FirmwareAnalyzer.analyze(this,selectedUri);selectedInfo=x;
            String s="IMAGE\nФайл: "+x.name+"\nРазмер: "+CompatibilityEvaluator.fmtBytes(x.size)+"\nSHA-256: "+x.sha256+
                    "\nРаздел: "+(x.inferredPartition.isEmpty()?"не определён":x.inferredPartition)+"\n"+x.summary;
            runOnUiThread(()->{details.setText(s);progress.setText("Образ загружен из Install Plan");});
        });
    }

    private void pickImage(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_IMAGE||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        selectedUri=data.getData();
        try{getContentResolver().takePersistableUriPermission(selectedUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        progress.setText("Анализ выбранного образа…");
        runBg("image analyze",()->{
            FirmwareAnalyzer.Result x=FirmwareAnalyzer.analyze(this,selectedUri);selectedInfo=x;
            String s="IMAGE\nФайл: "+x.name+"\nРазмер: "+CompatibilityEvaluator.fmtBytes(x.size)+"\nSHA-256: "+x.sha256+
                    "\nРаздел: "+(x.inferredPartition.isEmpty()?"не определён":x.inferredPartition)+"\n"+x.summary;
            runOnUiThread(()->{details.setText(s);progress.setText("Образ проанализирован");});
        });
    }

    private void preflightFlash(){
        if(selectedUri==null||selectedInfo==null){info("Нет образа","Сначала выберите и дождитесь анализа .img файла.");return;}
        if(!selectedInfo.directImage){info("Неверный формат","Direct flasher v0.8 beta принимает только отдельный .img.");return;}
        String part=selectedInfo.inferredPartition;
        if(part==null||part.isEmpty()){info("Раздел не определён","Имя файла должно однозначно соответствовать разделу, например boot.img, vendor_boot.img, init_boot.img, dtbo.img, vbmeta.img.");return;}
        if("userdata".equals(part)){info("Операция заблокирована","uPOSPa v0.8 beta не прошивает userdata через Direct IMG.");return;}
        runBg("flash preflight",()->{
            try(FastbootUsbClient fb=open()){
                Map<String,String> m=fb.querySummary();
                String unlocked=m.get("unlocked");
                boolean ok=isTrue(unlocked);
                if(!ok)throw new IOException("Fastboot сообщает unlocked="+unlocked+". Запись заблокирована.");
                String userspace=m.get("is-userspace");
                if(isDynamicPartition(part)&&!isTrue(userspace))throw new IOException("Раздел "+part+" требует Fastbootd (is-userspace=yes).");
                String product=m.get("product");
                if(!expectedDevice.isEmpty()||!expectedProduct.isEmpty()){
                    boolean match=product!=null&&(product.equalsIgnoreCase(expectedDevice)||product.equalsIgnoreCase(expectedProduct));
                    if(!match)throw new IOException("Fastboot product="+product+" не совпадает с отчётом Client (device="+expectedDevice+", product="+expectedProduct+").");
                }
                runOnUiThread(()->confirmFlash(product,part));
            }
        });
    }

    private void confirmFlash(String product,String part){
        String msg="TARGET: "+product+"\nPARTITION: "+part+"\nFILE: "+selectedInfo.name+"\nSIZE: "+CompatibilityEvaluator.fmtBytes(selectedInfo.size)+
                "\nSHA-256: "+selectedInfo.sha256+"\n\nЭто изменит раздел устройства. Для продолжения введите FLASH.";
        final EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);input.setHint("FLASH");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(UiKit.dp(this,20),0,UiKit.dp(this,20),0);
        box.addView(UiKit.tv(this,msg,13,UiKit.TEXT,false));box.addView(input,UiKit.top(this,10));
        new AlertDialog.Builder(this).setTitle("Подтверждение Fastboot flash").setView(box)
                .setNegativeButton("Отмена",null).setPositiveButton("ПРОШИТЬ",(d,w)->{
                    if(!"FLASH".equals(input.getText().toString().trim())){info("Отменено","Текст подтверждения не совпал.");return;}
                    executeFlash(part);
                }).show();
    }

    private void executeFlash(String part){
        bar.setProgress(0);progress.setText("Подготовка Fastboot flash…");
        runBg("flash "+part,()->{
            try(FastbootUsbClient fb=open();InputStream in=getContentResolver().openInputStream(selectedUri)){
                if(in==null)throw new IOException("Не удалось открыть образ");
                fb.flash(in,selectedInfo.size,part,(done,total,phase)->runOnUiThread(()->{
                    int pct=total<=0?0:(int)Math.min(100,(done*100L)/total);bar.setProgress(pct);progress.setText(phase+"  "+pct+"%");
                }));
                runOnUiThread(()->{bar.setProgress(100);progress.setText("✓ Раздел "+part+" записан. Перезагрузка выполняется только по команде пользователя.");info("Fastboot flash завершён","Раздел "+part+" записан. Перед перезагрузкой можно проверить slot и getvar.");});
            }
        });
    }

    private void showMap(String title,Map<String,String> m){
        StringBuilder s=new StringBuilder(title).append('\n');for(Map.Entry<String,String> e:m.entrySet())s.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        runOnUiThread(()->details.setText(s.toString()));
    }

    private void runBg(String name,Task t){new Thread(()->{try{t.run();}catch(Exception e){runOnUiThread(()->{progress.setText("Ошибка: "+e.getMessage());info("Ошибка",name+"\n"+e.getMessage());});}},"upospa-service-"+name).start();}
    private interface Task{void run()throws Exception;}
    private void info(String t,String m){new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Понятно",null).show();}
    private static boolean isTrue(String s){if(s==null)return false;s=s.trim().toLowerCase(Locale.US);return "yes".equals(s)||"true".equals(s)||"1".equals(s)||"unlocked".equals(s);}
    private static boolean isDynamicPartition(String p){return Arrays.asList("system","system_ext","vendor","product","odm","vendor_dlkm","odm_dlkm","system_dlkm","super").contains(p);}
    private static String clean(String s){return s==null?"":s.trim();}
}
