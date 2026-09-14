package com.vasiliyrogozhin85.posp.client;
import android.app.*;
import android.os.*;
import android.hardware.usb.*;
import android.content.*;
import android.graphics.Color;
import android.content.pm.*;
import android.view.*;
import android.widget.*;
import com.vasiliyrogozhin85.posp.common.Protocol;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(7,17,31),SURF=Color.rgb(13,27,42),ACC=Color.rgb(53,230,107),TEXT=Color.rgb(242,247,255),MUTED=Color.rgb(169,184,199);
    private UsbManager usb;private UsbAccessory acc;private ParcelFileDescriptor pfd;private FileInputStream in;private FileOutputStream out;private volatile boolean running;
    private TextView status,log;private File lastReport;private ReportLogger reporter;private Thread.UncaughtExceptionHandler oldCrash;

    @Override public void onCreate(Bundle b){super.onCreate(b);reporter=new ReportLogger(this,"Client_v0.2");installCrash();usb=(UsbManager)getSystemService(USB_SERVICE);setContentView(ui());report("onCreate");detect(getIntent());}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);report("onNewIntent "+i.getAction());detect(i);}
    @Override protected void onResume(){super.onResume();report("onResume");if(!running)detect(getIntent());}
    @Override protected void onStop(){report("onStop");String p=reporter.exportToDownloads("autosave");report("autosave="+p);super.onStop();}
    @Override protected void onDestroy(){report("onDestroy");close();super.onDestroy();}
    private void installCrash(){oldCrash=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler((t,e)->{reporter.throwable("uncaught/"+t.getName(),e);reporter.exportToDownloads("CRASH");if(oldCrash!=null)oldCrash.uncaughtException(t,e);});}

    private View ui(){ScrollView sv=new ScrollView(this);sv.setBackgroundColor(BG);LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(16),dp(16),dp(16),dp(24));sv.addView(r);
        r.addView(tv("▲ POSP Client",28,TEXT,true));r.addView(tv("v0.2 • DOOGEE companion + diagnostics",13,MUTED,false));
        r.addView(card(tv("СНАЧАЛА ЗДЕСЬ: 1) запустите Client на DOOGEE; 2) оставьте его открытым; 3) подключите USB-OTG к телефону Host; 4) дальнейшие кнопки нажимайте в Host.",15,TEXT,true)),top(12));
        status=tv("● Ожидание POSP Host",15,TEXT,true);r.addView(card(status),top(8));
        r.addView(section("Диагностика"));
        r.addView(btn("Собрать локальный отчёт сейчас",v->collect(false)));
        r.addView(btn("Собрать и отправить Host",v->collect(true)));
        r.addView(btn("Экспортировать сессионный отчёт в Download/POSPReports",v->{String p=reporter.exportToDownloads("manual");info("Отчёт Client",p);}));
        r.addView(section("Что делает Host"));
        r.addView(card(tv("Перезагрузка DOOGEE, Bootloader, Recovery, Fastbootd и перезапуск Client выполняются Host через ADB. Client сам не имеет права reboot — это нормально.",14,MUTED,false)));
        r.addView(btn("? Подробная инструкция",v->showHelp()));
        log=tv("Журнал:\n",12,MUTED,false);log.setTextIsSelectable(true);r.addView(card(log),top(8));return sv;
    }
    private void detect(Intent i){UsbAccessory a=null;if(i!=null&&UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(i.getAction()))a=i.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);if(a==null){UsbAccessory[]l=usb.getAccessoryList();if(l!=null&&l.length>0)a=l[0];}acc=a;if(acc!=null&&!running)open();else if(acc==null){status.setText("● Client запущен. Ожидание Host/Accessory.");status.setTextColor(MUTED);}}
    private void open(){close();try{pfd=usb.openAccessory(acc);if(pfd==null)throw new IOException("openAccessory=null");FileDescriptor fd=pfd.getFileDescriptor();in=new FileInputStream(fd);out=new FileOutputStream(fd);running=true;status.setText("● Client Link подключён к Host");status.setTextColor(ACC);report("Accessory opened "+acc.getManufacturer()+"/"+acc.getModel());sendLine(Protocol.HELLO+" CLIENT "+Build.MODEL);new Thread(this::listen,"posp-client-link").start();}catch(Exception e){reporter.throwable("openAccessory",e);status.setText("Ошибка Client Link: "+e.getMessage());}}
    private void listen(){try(BufferedReader br=new BufferedReader(new InputStreamReader(in))){String s;while(running&&(s=br.readLine())!=null){String cmd=s.trim();report("HOST->CLIENT "+cmd);if(Protocol.PING.equals(cmd))sendLine(Protocol.PONG);else if(Protocol.COLLECT_REPORT.equals(cmd))runOnUiThread(()->collect(true));}}catch(Exception e){if(running)reporter.throwable("listen",e);}finally{running=false;runOnUiThread(()->{status.setText("● Связь с Host потеряна");status.setTextColor(MUTED);});}}
    private void collect(boolean send){try{JSONObject j=new JSONObject();j.put("schema","posp_client_report");j.put("schema_version",3);j.put("time",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date()));
        JSONObject d=new JSONObject();d.put("manufacturer",Build.MANUFACTURER);d.put("model",Build.MODEL);d.put("product",Build.PRODUCT);d.put("device",Build.DEVICE);d.put("board",Build.BOARD);d.put("hardware",Build.HARDWARE);d.put("fingerprint",Build.FINGERPRINT);j.put("device",d);
        JSONObject a=new JSONObject();a.put("release",Build.VERSION.RELEASE);a.put("sdk",Build.VERSION.SDK_INT);a.put("security_patch",Build.VERSION.SECURITY_PATCH);j.put("android",a);
        android.os.BatteryManager bm=(android.os.BatteryManager)getSystemService(BATTERY_SERVICE);JSONObject bat=new JSONObject();bat.put("capacity",bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY));bat.put("charge_counter",bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));j.put("battery",bat);
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();am.getMemoryInfo(mi);JSONObject mem=new JSONObject();mem.put("total",mi.totalMem);mem.put("avail",mi.availMem);j.put("memory",mem);
        StatFs sf=new StatFs(getFilesDir().getAbsolutePath());JSONObject st=new JSONObject();st.put("total",sf.getTotalBytes());st.put("avail",sf.getAvailableBytes());j.put("storage",st);
        JSONArray feats=new JSONArray();for(FeatureInfo f:getPackageManager().getSystemAvailableFeatures())if(f.name!=null)feats.put(f.name);j.put("features",feats);
        File base=getExternalFilesDir(null);if(base==null)base=getFilesDir();File dir=new File(base,"reports");dir.mkdirs();lastReport=new File(dir,"POSP_Client_Report_"+System.currentTimeMillis()+".json");try(FileOutputStream o=new FileOutputStream(lastReport)){o.write(j.toString(2).getBytes("UTF-8"));}report("local report "+lastReport.getAbsolutePath()+" size="+lastReport.length());Toast.makeText(this,"Отчёт собран",Toast.LENGTH_SHORT).show();if(send)sendReport();}catch(Exception e){reporter.throwable("collect",e);info("Ошибка",e.toString());}}
    private void sendReport(){if(lastReport==null||!lastReport.exists()){info("Нет отчёта","Сначала соберите отчёт.");return;}if(!running||out==null){info("Host не подключён","Локальный отчёт сохранён, но Client Link не активен.");return;}File f=lastReport;new Thread(()->{try{sendLine(Protocol.REPORT_BEGIN+" "+f.length());try(FileInputStream x=new FileInputStream(f)){byte[]b=new byte[16384];int n;while((n=x.read(b))>0)out.write(b,0,n);out.flush();}sendLine(Protocol.REPORT_END);report("report sent "+f.length());}catch(Exception e){reporter.throwable("sendReport",e);}},"client-report").start();}
    private synchronized void sendLine(String s)throws Exception{if(out==null)throw new IOException("out=null");out.write(Protocol.line(s));out.flush();}
    private void close(){running=false;try{if(in!=null)in.close();}catch(Exception ignored){}try{if(out!=null)out.close();}catch(Exception ignored){}try{if(pfd!=null)pfd.close();}catch(Exception ignored){}in=null;out=null;pfd=null;}
    private void report(String s){reporter.line(s);runOnUiThread(()->{if(log!=null)log.append(s+"\n");});}
    private void showHelp(){info("Порядок работы","1. Откройте POSP Client на DOOGEE.\n2. Убедитесь, что включена «Отладка по USB» в параметрах разработчика.\n3. Оставьте Client открытым.\n4. Подключите DOOGEE к телефону Host через OTG.\n5. Перейдите на Host и выполняйте кнопки сверху вниз: Найти DOOGEE → Подключить ADB → подтвердить RSA на DOOGEE → Проверить ADB.\n6. Только после успешной проверки ADB используйте перезагрузку.\n7. Для Client Link на Host нажмите «Запустить Client USB (AOA)», подождите переподключения, затем «Найти DOOGEE» → «Открыть Client Link» → PING.\n\nОТЧЁТЫ:\nClient автоматически ведёт сессионный журнал. При уходе приложения в фон он сохраняется в Download/POSPReports. Также можно нажать кнопку экспорта вручную. Загрузите этот TXT сюда для анализа.");
    }
    private void info(String t,String m){new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Понятно",null).show();}
    private View card(View v){LinearLayout l=new LinearLayout(this);l.setPadding(dp(14),dp(14),dp(14),dp(14));l.setBackgroundColor(SURF);l.addView(v);return l;}
    private TextView section(String s){TextView v=tv(s,17,TEXT,true);v.setPadding(0,dp(18),0,dp(6));return v;}
    private Button btn(String s,View.OnClickListener c){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEXT);b.setBackgroundColor(Color.rgb(18,40,58));b.setOnClickListener(c);b.setLayoutParams(top(6));return b;}
    private TextView tv(String s,int z,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private LinearLayout.LayoutParams top(int n){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(n);return p;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
}
