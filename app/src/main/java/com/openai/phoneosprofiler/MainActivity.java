package com.openai.phoneosprofiler;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class MainActivity extends Activity {
    private TextView status;
    private Button share;
    private File lastReport;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18); root.setPadding(pad,pad,pad,pad);
        TextView title = new TextView(this); title.setText("Phone OS Profiler v0.3"); title.setTextSize(24);
        TextView desc = new TextView(this); desc.setText("Сбор аппаратного профиля телефона для разработки кастомной ОС. Результат — один JSON-рапорт."); desc.setPadding(0,dp(8),0,dp(12));
        Button scan = new Button(this); scan.setText("СОБРАТЬ И СОХРАНИТЬ РАПОРТ");
        share = new Button(this); share.setText("ПОДЕЛИТЬСЯ РАПОРТОМ"); share.setEnabled(false);
        status = new TextView(this); status.setText("Готов к сканированию."); status.setTextIsSelectable(true); status.setPadding(0,dp(16),0,0);
        root.addView(title); root.addView(desc); root.addView(scan); root.addView(share); root.addView(status);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);

        scan.setOnClickListener(v -> new Thread(() -> {
            runOnUiThread(() -> { scan.setEnabled(false); share.setEnabled(false); status.setText("Сбор данных..."); });
            try {
                JSONObject report = ReportCollector.collect(this);
                lastReport = ReportCollector.save(this, report);
                String sha = sha256(lastReport);
                runOnUiThread(() -> {
                    status.setText("Готово.\n\n" + lastReport.getAbsolutePath() + "\n\nSHA-256:\n" + sha + "\n\nНажмите «Поделиться рапортом» и пришлите JSON в чат.");
                    scan.setEnabled(true); share.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { status.setText("Ошибка: " + e); scan.setEnabled(true); });
            }
        }).start());

        share.setOnClickListener(v -> {
            if (lastReport == null) return;
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, getPackageName()+".files", lastReport));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Отправить Phone OS Profiler Report"));
        });
    }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private static String sha256(File f) {
        try { MessageDigest md=MessageDigest.getInstance("SHA-256"); FileInputStream in=new FileInputStream(f); byte[] b=new byte[8192]; int n; while((n=in.read(b))>0)md.update(b,0,n); in.close(); StringBuilder s=new StringBuilder(); for(byte x:md.digest())s.append(String.format(Locale.US,"%02x",x)); return s.toString(); } catch(Exception e){return "unavailable:"+e.getClass().getSimpleName();}
    }

    static class ReportCollector {
        interface J { JSONObject run() throws Exception; }
        interface IS { int x() throws Exception; }
        interface SS { String x() throws Exception; }

        static JSONObject collect(Context c) throws Exception {
            JSONObject r = new JSONObject(); JSONArray errors = new JSONArray();
            r.put("schema", "phone_os_profiler_report"); r.put("schema_version", 2);
            r.put("report_id", UUID.randomUUID().toString()); r.put("generated_at_utc", utcNow());
            r.put("profiler", obj("name","Phone OS Profiler","version","0.3","mode","normal_app"));
            putSection(r,"device",()->device(c),errors); putSection(r,"android",ReportCollector::androidInfo,errors);
            putSection(r,"cpu",ReportCollector::cpu,errors); putSection(r,"memory",()->memory(c),errors);
            putSection(r,"storage",()->storage(c),errors); putSection(r,"display",()->display(c),errors);
            putSection(r,"battery",()->battery(c),errors); putSection(r,"sensors",()->sensors(c),errors);
            putSection(r,"cameras",()->cameras(c),errors); putSection(r,"network",()->network(c),errors);
            putSection(r,"audio",()->audio(c),errors); putSection(r,"telephony",()->telephony(c),errors);
            putSection(r,"boot",ReportCollector::boot,errors); putSection(r,"treble",ReportCollector::treble,errors);
            putSection(r,"kernel",ReportCollector::kernel,errors); putSection(r,"graphics",ReportCollector::graphics,errors);
            putSection(r,"thermal",ReportCollector::thermal,errors); putSection(r,"partitions",ReportCollector::partitions,errors);
            putSection(r,"firmware",ReportCollector::firmware,errors); putSection(r,"features",()->features(c),errors);
            putSection(r,"root",ReportCollector::rootInfo,errors); putSection(r,"raw",ReportCollector::raw,errors);
            r.put("errors", errors); return r;
        }

        static void putSection(JSONObject r,String name,J j,JSONArray errors){ try { r.put(name,j.run()); } catch(Exception e){ try { r.put(name,obj("status","unavailable","error",e.toString())); errors.put(obj("section",name,"error",e.toString())); } catch(Exception ignored){} } }
        static JSONObject obj(Object... kv)throws Exception{JSONObject o=new JSONObject();for(int i=0;i+1<kv.length;i+=2)o.put(String.valueOf(kv[i]),kv[i+1]==null?JSONObject.NULL:kv[i+1]);return o;}
        static String utcNow(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date());}

        static JSONObject device(Context c)throws Exception{return obj("manufacturer",Build.MANUFACTURER,"brand",Build.BRAND,"model",Build.MODEL,"device",Build.DEVICE,"product",Build.PRODUCT,"board",Build.BOARD,"hardware",Build.HARDWARE,"bootloader",Build.BOOTLOADER,"fingerprint",Build.FINGERPRINT,"supported_abis",new JSONArray(Build.SUPPORTED_ABIS),"android_id_present",Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ANDROID_ID)!=null);}
        static JSONObject androidInfo()throws Exception{return obj("release",Build.VERSION.RELEASE,"sdk_int",Build.VERSION.SDK_INT,"security_patch",Build.VERSION.SECURITY_PATCH,"incremental",Build.VERSION.INCREMENTAL,"codename",Build.VERSION.CODENAME,"base_os",Build.VERSION.BASE_OS,"build_id",Build.ID,"build_type",Build.TYPE,"tags",Build.TAGS,"user",Build.USER,"host",Build.HOST,"time_ms",Build.TIME);}
        static JSONObject cpu()throws Exception{JSONObject o=obj("available_processors",Runtime.getRuntime().availableProcessors(),"supported_abis",new JSONArray(Build.SUPPORTED_ABIS),"proc_cpuinfo",read("/proc/cpuinfo",262144));JSONArray a=new JSONArray();for(int i=0;i<128;i++){File d=new File("/sys/devices/system/cpu/cpu"+i);if(!d.exists())continue;a.put(obj("id",i,"max_khz",read1(d+"/cpufreq/cpuinfo_max_freq"),"min_khz",read1(d+"/cpufreq/cpuinfo_min_freq"),"cur_khz",read1(d+"/cpufreq/scaling_cur_freq"),"governor",read1(d+"/cpufreq/scaling_governor")));}o.put("cores",a);return o;}
        static JSONObject memory(Context c)throws Exception{ActivityManager am=(ActivityManager)c.getSystemService(ACTIVITY_SERVICE);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();am.getMemoryInfo(m);return obj("total_mem_bytes",m.totalMem,"avail_mem_bytes",m.availMem,"low_memory",m.lowMemory,"threshold_bytes",m.threshold,"proc_meminfo",read("/proc/meminfo",131072));}
        static JSONObject storage(Context c)throws Exception{JSONArray a=new JSONArray();File[] fs={Environment.getDataDirectory(),Environment.getRootDirectory(),c.getFilesDir(),c.getExternalFilesDir(null)};for(File f:fs){if(f==null)continue;StatFs s=new StatFs(f.getAbsolutePath());a.put(obj("path",f.getAbsolutePath(),"total_bytes",s.getTotalBytes(),"available_bytes",s.getAvailableBytes(),"free_bytes",s.getFreeBytes()));}return obj("volumes",a,"proc_mounts",read("/proc/mounts",262144));}
        static JSONObject display(Context c)throws Exception{Display d=((android.view.WindowManager)c.getSystemService(WINDOW_SERVICE)).getDefaultDisplay();DisplayMetrics m=new DisplayMetrics();d.getRealMetrics(m);Point p=new Point();d.getRealSize(p);return obj("width_px",p.x,"height_px",p.y,"density",m.density,"density_dpi",m.densityDpi,"xdpi",m.xdpi,"ydpi",m.ydpi,"refresh_rate_hz",d.getRefreshRate(),"rotation",d.getRotation());}
        static JSONObject battery(Context c)throws Exception{Intent i=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(i==null)return obj("status","unavailable");BatteryManager b=(BatteryManager)c.getSystemService(BATTERY_SERVICE);return obj("level",i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),"scale",i.getIntExtra(BatteryManager.EXTRA_SCALE,-1),"status",i.getIntExtra(BatteryManager.EXTRA_STATUS,-1),"plugged",i.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1),"health",i.getIntExtra(BatteryManager.EXTRA_HEALTH,-1),"temperature_tenths_c",i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1),"voltage_mv",i.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1),"technology",i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),"charge_counter_uah",b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),"current_now_ua",b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),"current_avg_ua",b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),"capacity_percent",b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));}
        static JSONObject sensors(Context c)throws Exception{SensorManager sm=(SensorManager)c.getSystemService(SENSOR_SERVICE);JSONArray a=new JSONArray();for(Sensor s:sm.getSensorList(Sensor.TYPE_ALL))a.put(obj("name",s.getName(),"vendor",s.getVendor(),"version",s.getVersion(),"type",s.getType(),"string_type",s.getStringType(),"max_range",s.getMaximumRange(),"resolution",s.getResolution(),"power_ma",s.getPower(),"min_delay_us",s.getMinDelay(),"max_delay_us",s.getMaxDelay(),"fifo_reserved",s.getFifoReservedEventCount(),"fifo_max",s.getFifoMaxEventCount(),"wakeup",s.isWakeUpSensor()));return obj("count",a.length(),"items",a);}
        static JSONObject cameras(Context c)throws Exception{CameraManager cm=(CameraManager)c.getSystemService(CAMERA_SERVICE);JSONArray a=new JSONArray();for(String id:cm.getCameraIdList()){JSONObject x=obj("id",id);try{CameraCharacteristics cc=cm.getCameraCharacteristics(id);x.put("lens_facing",jsonVal(cc.get(CameraCharacteristics.LENS_FACING)));x.put("hardware_level",jsonVal(cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)));int[] caps=cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);x.put("capabilities",caps==null?JSONObject.NULL:new JSONArray(caps));android.util.Size s=cc.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);if(s!=null){x.put("pixel_array_width",s.getWidth());x.put("pixel_array_height",s.getHeight());}}catch(Exception e){x.put("error",e.toString());}a.put(x);}return obj("count",a.length(),"items",a);}
        static Object jsonVal(Object x){return x==null?JSONObject.NULL:x;}
        static JSONObject network(Context c)throws Exception{JSONObject o=new JSONObject();ConnectivityManager cm=(ConnectivityManager)c.getSystemService(CONNECTIVITY_SERVICE);Network n=cm.getActiveNetwork();NetworkCapabilities nc=n==null?null:cm.getNetworkCapabilities(n);if(nc!=null)o.put("active_capabilities",nc.toString());JSONArray a=new JSONArray();Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();while(en!=null&&en.hasMoreElements()){NetworkInterface ni=en.nextElement();JSONArray add=new JSONArray();for(java.net.InetAddress ia:Collections.list(ni.getInetAddresses()))add.put(ia.getHostAddress());a.put(obj("name",ni.getName(),"display_name",ni.getDisplayName(),"up",safeUp(ni),"loopback",safeLoop(ni),"mtu",safeMtu(ni),"addresses",add));}o.put("interfaces",a);return o;}
        static boolean safeUp(NetworkInterface n){try{return n.isUp();}catch(Exception e){return false;}}static boolean safeLoop(NetworkInterface n){try{return n.isLoopback();}catch(Exception e){return false;}}static int safeMtu(NetworkInterface n){try{return n.getMTU();}catch(Exception e){return -1;}}
        static JSONObject audio(Context c)throws Exception{AudioManager a=(AudioManager)c.getSystemService(AUDIO_SERVICE);return obj("mode",a.getMode(),"music_active",a.isMusicActive(),"speakerphone",a.isSpeakerphoneOn(),"sample_rate_property",a.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE),"frames_per_buffer_property",a.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER),"fixed_volume",a.isVolumeFixed());}
        static JSONObject telephony(Context c)throws Exception{TelephonyManager t=(TelephonyManager)c.getSystemService(TELEPHONY_SERVICE);return obj("phone_type",t.getPhoneType(),"sim_state",t.getSimState(),"data_network_type",safeInt(()->t.getDataNetworkType()),"voice_network_type",safeInt(()->t.getVoiceNetworkType()),"network_country_iso",safeStr(()->t.getNetworkCountryIso()),"sim_country_iso",safeStr(()->t.getSimCountryIso()),"carrier_id",safeInt(()->t.getSimCarrierId()),"active_modem_count",Build.VERSION.SDK_INT>=30?safeInt(()->t.getActiveModemCount()):-1,"permission_read_phone_state",c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED);}
        static int safeInt(IS f){try{return f.x();}catch(Exception e){return -1;}}static String safeStr(SS f){try{return f.x();}catch(Exception e){return null;}}
        static JSONObject boot()throws Exception{return obj("verified_boot_state",prop("ro.boot.verifiedbootstate"),"vbmeta_device_state",prop("ro.boot.vbmeta.device_state"),"flash_locked",prop("ro.boot.flash.locked"),"slot_suffix",prop("ro.boot.slot_suffix"),"bootreason",prop("ro.boot.bootreason"),"bootloader",Build.BOOTLOADER);}
        static JSONObject treble()throws Exception{return obj("treble_enabled",prop("ro.treble.enabled"),"ab_update",prop("ro.build.ab_update"),"virtual_ab",prop("ro.virtual_ab.enabled"),"dynamic_partitions",prop("ro.boot.dynamic_partitions"),"vndk_version",prop("ro.vndk.version"),"product_first_api_level",prop("ro.product.first_api_level"),"vendor_api_level",prop("ro.vendor.api_level"));}
        static JSONObject kernel()throws Exception{return obj("proc_version",read1("/proc/version"),"uname_a",exec("uname -a",32768),"cmdline",read("/proc/cmdline",65536),"selinux",exec("getenforce",4096));}

        static JSONObject graphics()throws Exception{return obj(
                "egl",prop("ro.hardware.egl"),
                "vulkan",prop("ro.hardware.vulkan"),
                "gpu_vendor_hint",prop("ro.hardware"),
                "surfaceflinger",exec("dumpsys SurfaceFlinger 2>/dev/null | head -n 120",131072),
                "gpu_sysfs",exec("for d in /sys/class/misc/mali* /sys/devices/platform/*gpu* /sys/kernel/debug/mali*; do [ -e \"$d\" ] && echo \"=== $d ===\" && ls -la \"$d\" 2>&1; done",131072));}
        static JSONObject thermal()throws Exception{return obj(
                "thermalservice",exec("dumpsys thermalservice 2>/dev/null",131072),
                "zones",exec("for z in /sys/class/thermal/thermal_zone*; do [ -d \"$z\" ] || continue; echo \"=== $z ===\"; cat \"$z/type\" 2>/dev/null; cat \"$z/temp\" 2>/dev/null; done",131072));}
        static JSONObject partitions()throws Exception{return obj(
                "proc_partitions",read("/proc/partitions",131072),
                "by_name",exec("ls -la /dev/block/by-name 2>&1; ls -la /dev/block/platform/bootdevice/by-name 2>&1",131072),
                "dm",exec("ls -la /dev/block/dm-* 2>&1",65536));}
        static JSONObject firmware()throws Exception{return obj(
                "baseband",prop("gsm.version.baseband"),
                "radio",prop("ro.boot.radio"),
                "vendor_build_fingerprint",prop("ro.vendor.build.fingerprint"),
                "odm_build_fingerprint",prop("ro.odm.build.fingerprint"),
                "product_build_fingerprint",prop("ro.product.build.fingerprint"),
                "hardware_sku",prop("ro.boot.hardware.sku"),
                "boot_hardware",prop("ro.boot.hardware"));}
        static JSONObject features(Context c)throws Exception{JSONArray a=new JSONArray();FeatureInfo[] fs=c.getPackageManager().getSystemAvailableFeatures();if(fs!=null)for(FeatureInfo f:fs)a.put(obj("name",f.name,"version",f.version,"gles_version",f.getGlEsVersion()));return obj("items",a);}
        static JSONObject rootInfo()throws Exception{JSONArray a=new JSONArray();String[] ps={"/system/bin/su","/system/xbin/su","/sbin/su","/su/bin/su","/data/adb/magisk"};boolean found=false;for(String s:ps){boolean e=new File(s).exists();a.put(obj("path",s,"exists",e));found|=e;}return obj("root_artifact_found",found,"which_su",exec("which su",4096),"paths",a);}
        static JSONObject raw()throws Exception{return obj("getprop",exec("getprop",1048576),"proc_partitions",read("/proc/partitions",131072),"proc_filesystems",read("/proc/filesystems",131072),"proc_modules",read("/proc/modules",524288),"sys_block",exec("ls -la /sys/block 2>&1",65536));}

        static String prop(String k){String s=exec("getprop "+k,8192);return s==null?null:s.trim();}
        static String read1(String p){String s=read(p,8192);return s==null?null:s.trim();}
        static String read(String p,int max){try{File f=new File(p);if(!f.exists())return null;BufferedReader b=new BufferedReader(new InputStreamReader(new FileInputStream(f)));StringBuilder s=new StringBuilder();char[] c=new char[4096];int n,total=0;while((n=b.read(c))>0&&total<max){int use=Math.min(n,max-total);s.append(c,0,use);total+=use;}b.close();return s.toString();}catch(Exception e){return "<unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage()+">";}}
        static String exec(String command,int max){try{Process p=Runtime.getRuntime().exec(new String[]{"sh","-c",command});BufferedReader b=new BufferedReader(new InputStreamReader(p.getInputStream()));StringBuilder s=new StringBuilder();char[] c=new char[4096];int n,total=0;while((n=b.read(c))>0&&total<max){int use=Math.min(n,max-total);s.append(c,0,use);total+=use;}b.close();p.waitFor();return s.toString();}catch(Exception e){return "<unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage()+">";}}
        static File save(Context c,JSONObject r)throws Exception{File base=c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);if(base==null)base=c.getFilesDir();File dir=new File(base,"PhoneOSProfiler");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Cannot create "+dir);String ts=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());File f=new File(dir,"PhoneOSProfiler_Report_"+ts+".json");FileOutputStream out=new FileOutputStream(f);out.write(r.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));out.close();return f;}
    }
}
