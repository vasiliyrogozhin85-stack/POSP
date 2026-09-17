package com.vasiliyrogozhin85.upospa;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.usb.*;
import android.os.Build;
import android.os.Environment;

import org.json.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class WizardEngine {
    static final String QUICK = "quick";
    static final String FULL = "full";
    static final String OS_PREP = "os_prep";
    private static final String PACKAGE = "com.vasiliyrogozhin85.upospa";
    private static final String USB_PERMISSION = "com.vasiliyrogozhin85.upospa.WIZARD_USB_PERMISSION";
    private static final String CLIENT_WIZARD_DIR = "/sdcard/Android/data/" + PACKAGE + "/files/wizard";

    interface Listener {
        void onPhase(String phase, String detail, int progress);
        void onLog(String line);
        void onComplete(File report);
        void onError(String message, File partialReport);
    }

    private final Activity activity;
    private final UsbManager usb;
    private final String scenario;
    private final Listener listener;
    private final WizardStateStore state;
    private final OperationJournal journal;
    private final JSONObject report = new JSONObject();
    private final JSONArray timeline = new JSONArray();
    private final JSONArray warnings = new JSONArray();
    private final String session;
    private volatile boolean cancelled;
    private Thread thread;
    private AdbUsbClient adb;

    WizardEngine(Activity activity, String scenario, Listener listener) throws Exception {
        this.activity = activity;
        this.usb = (UsbManager) activity.getSystemService(Activity.USB_SERVICE);
        this.scenario = scenario;
        this.listener = listener;
        this.state = new WizardStateStore(activity);
        this.journal = new OperationJournal(activity);
        this.session = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_" +
                Integer.toHexString(new Random().nextInt());

        report.put("schema", "upospa_wizard_report");
        report.put("schema_version", 1);
        report.put("app_version", "0.8-beta-wizard");
        report.put("session", session);
        report.put("scenario", scenario);
        report.put("started", isoNow());
        report.put("timeline", timeline);
        report.put("warnings", warnings);
        JSONObject privacy = new JSONObject();
        privacy.put("collects_personal_files", false);
        privacy.put("collects_messages", false);
        privacy.put("collects_contacts", false);
        privacy.put("collects_photos", false);
        privacy.put("scope", "device/OS/USB/ADB/Fastboot diagnostic metadata only");
        report.put("privacy", privacy);
    }

    void start() {
        if (thread != null) return;
        thread = new Thread(this::run, "upospa-wizard");
        thread.start();
    }

    void cancel() {
        cancelled = true;
        closeAdb();
        log("Wizard cancellation requested");
    }

    private void run() {
        File partial = null;
        try {
            journal.add("wizard", "start", scenario + " session=" + session);
            phase("USB / ADB", "Ожидание Client и автоматическое подключение ADB", 5);
            UsbDevice adbDevice = waitForDevice(WizardEngine::hasAdb, 180000, "ADB");
            ensurePermission(adbDevice, 45000);
            connectAdb(adbDevice);

            phase("Client", "Проверка uPOSPa и сбор локального отчёта Client", 15);
            JSONObject identity = collectIdentity();
            report.put("target", identity);
            collectClientSnapshot();

            phase("Android", "Сбор системных данных через ADB", 28);
            report.put("adb", collectAdbDiagnostics());

            if (QUICK.equals(scenario)) {
                phase("Завершение", "Быстрая диагностика закончена. Возвращаю uPOSPa на Client.", 88);
                finishInAndroid();
                complete();
                return;
            }

            phase("Bootloader", "Перезагрузка Client в Bootloader / Fastboot", 40);
            rebootAdb("reboot bootloader");
            closeAdb();
            UsbDevice fbDevice = waitForDevice(WizardEngine::hasFastboot, 180000, "Fastboot");
            ensurePermission(fbDevice, 45000);
            JSONObject fastboot = collectFastboot(fbDevice, false);
            report.put("fastboot", fastboot);

            if (OS_PREP.equals(scenario)) {
                phase("Fastbootd", "Проверка userspace Fastboot и dynamic partitions", 62);
                JSONObject fastbootd = collectFastbootdIfPossible(fbDevice);
                report.put("fastbootd", fastbootd);
            }

            phase("Android", "Возврат Client в обычный Android", 78);
            rebootFastbootToAndroid();
            UsbDevice adbBack = waitForDevice(WizardEngine::hasAdb, 240000, "ADB after reboot");
            ensurePermission(adbBack, 45000);
            connectAdb(adbBack);

            phase("Завершение", "Запускаю uPOSPa на Client и формирую итоговый отчёт", 90);
            finishInAndroid();
            complete();
        } catch (CancelledException e) {
            try { bestEffortReturnAndroid("Wizard остановлен пользователем. Client возвращён в Android, если это было возможно."); } catch (Exception ignored) {}
            try { partial = writeReport("cancelled"); } catch (Exception ignored) {}
            state.phase(session, scenario, "cancelled", 0, false);
            journal.add("wizard", "cancelled", scenario + " session=" + session);
            if (listener != null) listener.onError("Wizard остановлен пользователем.", partial);
        } catch (Exception e) {
            warn("Ошибка: " + e);
            try { bestEffortReturnAndroid("Процедура прервана: " + safe(e.getMessage())); } catch (Exception ignored) {}
            try { partial = writeReport("error"); } catch (Exception ignored) {}
            state.phase(session, scenario, "error", 0, false);
            journal.add("wizard", "error", safe(e.toString()));
            if (listener != null) listener.onError(safe(e.getMessage()), partial);
        } finally {
            closeAdb();
        }
    }

    private void complete() throws Exception {
        phase("Готово", "Сбор завершён. На Host доступно сохранение отчёта.", 100);
        report.put("completed", isoNow());
        File file = writeReport("complete");
        state.phase(session, scenario, "complete", 100, false);
        journal.add("wizard", "complete", file.getAbsolutePath());
        if (listener != null) listener.onComplete(file);
    }

    private JSONObject collectIdentity() throws Exception {
        JSONObject j = new JSONObject();
        j.put("manufacturer", shell("getprop ro.product.manufacturer", 10000).trim());
        j.put("model", shell("getprop ro.product.model", 10000).trim());
        j.put("device", shell("getprop ro.product.device", 10000).trim());
        j.put("product", shell("getprop ro.product.name", 10000).trim());
        j.put("serial", shell("getprop ro.serialno", 10000).trim());
        j.put("android", shell("getprop ro.build.version.release", 10000).trim());
        j.put("sdk", shell("getprop ro.build.version.sdk", 10000).trim());
        j.put("fingerprint", shell("getprop ro.build.fingerprint", 10000).trim());
        log("Target: " + j.optString("manufacturer") + " " + j.optString("model") + " / " + j.optString("device"));
        return j;
    }

    private void collectClientSnapshot() {
        try {
            String installed = shell("pm path " + PACKAGE, 10000);
            if (!installed.contains("package:")) {
                warn("uPOSPa не найден на Client. Продолжаю сбор через ADB без локального DeviceSnapshot.");
                return;
            }
            String cmd = "am start -W -n " + PACKAGE + "/.ClientActivity" +
                    " --ez wizard_collect true --es wizard_session '" + session + "'" +
                    " --es wizard_scenario '" + scenario + "'";
            shell(cmd, 15000);
            long end = System.currentTimeMillis() + 60000;
            boolean ready = false;
            while (System.currentTimeMillis() < end) {
                checkCancelled();
                String marker = shell("cat " + CLIENT_WIZARD_DIR + "/ready.txt 2>/dev/null", 7000).trim();
                if (session.equals(marker)) { ready = true; break; }
                sleep(1000);
            }
            if (!ready) {
                warn("Client DeviceSnapshot не подтвердил готовность за 60 секунд; ADB-сбор продолжается.");
                return;
            }
            String text = shell("cat " + CLIENT_WIZARD_DIR + "/snapshot.json 2>/dev/null", 30000).trim();
            if (text.startsWith("{")) {
                report.put("client_snapshot", new JSONObject(text));
                log("Client DeviceSnapshot получен через ADB bridge");
            } else {
                warn("Client snapshot unreadable; using ADB-only diagnostics.");
            }
        } catch (Exception e) {
            warn("Client snapshot: " + safe(e.getMessage()));
        }
    }

    private JSONObject collectAdbDiagnostics() throws Exception {
        JSONObject j = new JSONObject();
        putShell(j, "getprop", "getprop", 30000);
        putShell(j, "cpuinfo", "cat /proc/cpuinfo", 20000);
        putShell(j, "meminfo", "cat /proc/meminfo", 15000);
        putShell(j, "df", "df -h", 15000);
        putShell(j, "partitions", "cat /proc/partitions", 15000);
        putShell(j, "mounts", "cat /proc/mounts", 20000);
        putShell(j, "battery", "dumpsys battery", 15000);
        putShell(j, "display", "wm size; wm density", 10000);
        putShell(j, "usb", "dumpsys usb 2>/dev/null", 20000);
        putShell(j, "kernel", "uname -a; cat /proc/version", 10000);
        putShell(j, "selinux", "getenforce 2>/dev/null", 7000);
        putShell(j, "boot_slots", "getprop ro.boot.slot_suffix; getprop ro.boot.slot; getprop ro.build.ab_update; getprop ro.virtual_ab.enabled", 10000);
        return j;
    }

    private JSONObject collectFastboot(UsbDevice d, boolean fastbootd) throws Exception {
        ensurePermission(d, 45000);
        JSONObject j = new JSONObject();
        try (FastbootUsbClient fb = FastbootUsbClient.open(usb, d)) {
            Map<String,String> summary = fb.querySummary();
            JSONObject s = new JSONObject();
            for (Map.Entry<String,String> e : summary.entrySet()) s.put(e.getKey(), e.getValue());
            j.put("summary", s);
            Map<String,String> slots = fb.querySlotState();
            JSONObject sl = new JSONObject();
            for (Map.Entry<String,String> e : slots.entrySet()) sl.put(e.getKey(), e.getValue());
            j.put("slots", sl);
            JSONArray p = new JSONArray();
            for (FastbootUsbClient.PartitionEntry x : fb.queryPartitionMap()) {
                JSONObject q = new JSONObject();
                q.put("name", x.name); q.put("size", x.size); q.put("type", x.type); p.put(q);
            }
            j.put("partitions", p);
            j.put("userspace", fastbootd || "yes".equalsIgnoreCase(summary.get("is-userspace")) || "true".equalsIgnoreCase(summary.get("is-userspace")));
            log((fastbootd ? "Fastbootd" : "Fastboot") + " data: product=" + summary.get("product") + " slot=" + summary.get("current-slot"));
        }
        return j;
    }

    private JSONObject collectFastbootdIfPossible(UsbDevice initial) throws Exception {
        JSONObject result = new JSONObject();
        boolean userspace = false;
        try (FastbootUsbClient fb = FastbootUsbClient.open(usb, initial)) {
            String v = fb.getVar("is-userspace");
            userspace = "yes".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v);
            if (!userspace) {
                log("Fastbootd: sending reboot-fastboot");
                try { fb.rebootFastbootd(); }
                catch (Exception e) {
                    warn("Fastbootd переход не поддержан: " + safe(e.getMessage()));
                    result.put("available", false);
                    result.put("reason", safe(e.getMessage()));
                    return result;
                }
            }
        }
        if (!userspace) {
            UsbDevice fd = waitForDevice(WizardEngine::hasFastboot, 180000, "Fastbootd");
            ensurePermission(fd, 45000);
            JSONObject d = collectFastboot(fd, true);
            result.put("available", true);
            result.put("data", d);
        } else {
            result.put("available", true);
            result.put("data", collectFastboot(initial, true));
        }
        return result;
    }

    private void rebootFastbootToAndroid() throws Exception {
        UsbDevice d = findDevice(WizardEngine::hasFastboot);
        if (d == null) d = waitForDevice(WizardEngine::hasFastboot, 60000, "Fastboot/Fastbootd");
        ensurePermission(d, 45000);
        try (FastbootUsbClient fb = FastbootUsbClient.open(usb, d)) {
            try { fb.reboot(); }
            catch (Exception e) { warn("Fastboot reboot reply: " + safe(e.getMessage())); }
        }
    }

    private void finishInAndroid() throws Exception {
        if (adb == null) {
            UsbDevice d = waitForDevice(WizardEngine::hasAdb, 180000, "ADB final");
            ensurePermission(d, 45000);
            connectAdb(d);
        }
        try { startClientStatus(true, "Сбор данных завершён. Отчёт готов на Host."); }
        catch (Exception e) { warn("Не удалось автоматически открыть uPOSPa Client: " + safe(e.getMessage())); }
        try {
            report.put("final_android", new JSONObject()
                    .put("boot_completed", shell("getprop sys.boot_completed", 10000).trim())
                    .put("bootmode", shell("getprop ro.bootmode", 10000).trim())
                    .put("slot", shell("getprop ro.boot.slot_suffix; getprop ro.boot.slot", 10000).trim()));
        } catch (Exception e) { warn("Final Android state: " + safe(e.getMessage())); }
    }

    private void startClientStatus(boolean ok, String message) throws Exception {
        String cmd = "am start -W -n " + PACKAGE + "/.ClientActivity" +
                " --ez wizard_complete " + (ok ? "true" : "false") +
                " --es wizard_session '" + session + "'" +
                " --es wizard_scenario '" + scenario + "'" +
                " --es wizard_message '" + shellQuote(message) + "'";
        shell(cmd, 15000);
    }

    private void bestEffortReturnAndroid(String message) {
        try { closeAdb(); } catch (Exception ignored) {}
        try {
            UsbDevice fb=findDevice(WizardEngine::hasFastboot);
            if(fb!=null){
                if(!usb.hasPermission(fb)) requestPermissionRaw(fb,20000);
                if(usb.hasPermission(fb)){
                    try(FastbootUsbClient c=FastbootUsbClient.open(usb,fb)){try{c.reboot();}catch(Exception ignored){}}
                    log("Recovery: Fastboot reboot -> Android requested");
                }
            }
        }catch(Exception e){log("Recovery fastboot: "+safe(e.getMessage()));}
        try{
            long end=System.currentTimeMillis()+150000;
            UsbDevice ad=null;
            while(System.currentTimeMillis()<end){
                ad=findDevice(WizardEngine::hasAdb);
                if(ad!=null)break;
                try{Thread.sleep(700);}catch(InterruptedException x){Thread.currentThread().interrupt();break;}
            }
            if(ad!=null){
                if(!usb.hasPermission(ad))requestPermissionRaw(ad,30000);
                if(usb.hasPermission(ad)){
                    adb=AdbUsbClient.open(activity,usb,ad);
                    adb.connect();
                    startClientStatus(false,message);
                    log("Recovery: Client returned to Android and uPOSPa opened");
                }
            }
        }catch(Exception e){log("Recovery Android: "+safe(e.getMessage()));}
    }

    private void requestPermissionRaw(UsbDevice d,long timeout){
        try{
            int flags=Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0;
            PendingIntent pi=PendingIntent.getBroadcast(activity,78,new Intent(USB_PERMISSION).setPackage(activity.getPackageName()),flags);
            activity.runOnUiThread(()->usb.requestPermission(d,pi));
            long end=System.currentTimeMillis()+timeout;
            while(System.currentTimeMillis()<end&&!usb.hasPermission(d)){
                try{Thread.sleep(250);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            }
        }catch(Exception ignored){}
    }

    private void rebootAdb(String command) throws Exception {
        try { shell(command, 12000); }
        catch (Exception e) { log("Expected disconnect after " + command + ": " + safe(e.getMessage())); }
    }

    private void connectAdb(UsbDevice d) throws Exception {
        closeAdb();
        adb = AdbUsbClient.open(activity, usb, d);
        phase("ADB", "Если Client показывает RSA-запрос, подтвердите ключ uPOSPa Host.", Math.max(6, currentProgress()));
        adb.connect();
        log("ADB CONNECTED");
    }

    private String shell(String cmd, int timeout) throws Exception {
        checkCancelled();
        if (adb == null) throw new IOException("ADB is not connected");
        return adb.shell(cmd, timeout);
    }

    private void putShell(JSONObject j, String key, String cmd, int timeout) {
        try { j.put(key, shell(cmd, timeout)); }
        catch (Exception e) {
            try { j.put(key + "_error", safe(e.getMessage())); } catch (Exception ignored) {}
            warn("ADB " + key + ": " + safe(e.getMessage()));
        }
    }

    private UsbDevice waitForDevice(DeviceMatcher matcher, long timeout, String label) throws Exception {
        long end = System.currentTimeMillis() + timeout;
        boolean announced = false;
        while (System.currentTimeMillis() < end) {
            checkCancelled();
            UsbDevice d = findDevice(matcher);
            if (d != null) {
                log(label + " USB found " + hex(d.getVendorId()) + ":" + hex(d.getProductId()));
                return d;
            }
            if (!announced) { log("Waiting for " + label + " USB device…"); announced = true; }
            sleep(500);
        }
        throw new IOException(label + " device not found before timeout");
    }

    private UsbDevice findDevice(DeviceMatcher matcher) {
        if (usb == null) return null;
        for (UsbDevice d : usb.getDeviceList().values()) if (matcher.matches(d)) return d;
        return null;
    }

    private void ensurePermission(UsbDevice d, long timeout) throws Exception {
        if (d == null) throw new IOException("USB device=null");
        if (usb.hasPermission(d)) return;
        int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent pi = PendingIntent.getBroadcast(activity, 77,
                new Intent(USB_PERMISSION).setPackage(activity.getPackageName()), flags);
        activity.runOnUiThread(() -> usb.requestPermission(d, pi));
        long end = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < end) {
            checkCancelled();
            if (usb.hasPermission(d)) return;
            sleep(250);
        }
        throw new IOException("USB permission was not granted for " + hex(d.getVendorId()) + ":" + hex(d.getProductId()));
    }

    private File writeReport(String status) throws Exception {
        report.put("status", status);
        report.put("updated", isoNow());
        File base = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = activity.getFilesDir();
        File dir = new File(base, "uPOSPa/wizard");
        dir.mkdirs();
        File f = new File(dir, "uPOSPa_Wizard_" + session + ".json");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(report.toString(2).getBytes("UTF-8"));
        }
        return f;
    }

    private void phase(String title, String detail, int progress) throws Exception {
        JSONObject x = new JSONObject();
        x.put("time", isoNow()); x.put("phase", title); x.put("detail", detail); x.put("progress", progress);
        timeline.put(x);
        state.phase(session, scenario, title, progress, true);
        journal.add("wizard_phase", title, detail);
        if (listener != null) listener.onPhase(title, detail, progress);
        log(title + ": " + detail);
    }

    private int currentProgress() {
        if (timeline.length() == 0) return 0;
        JSONObject x = timeline.optJSONObject(timeline.length() - 1);
        return x == null ? 0 : x.optInt("progress", 0);
    }

    private void warn(String text) {
        warnings.put(text);
        log("WARN: " + text);
    }

    private void log(String s) {
        if (listener != null) listener.onLog(s);
    }

    private void closeAdb() {
        try { if (adb != null) adb.close(); } catch (Exception ignored) {}
        adb = null;
    }

    private void checkCancelled() throws CancelledException {
        if (cancelled) throw new CancelledException();
    }

    private static boolean hasAdb(UsbDevice d) { return UsbModeDetector.hasAdb(d); }
    private static boolean hasFastboot(UsbDevice d) { return UsbModeDetector.hasFastboot(d); }
    private interface DeviceMatcher { boolean matches(UsbDevice d); }
    private static final class CancelledException extends Exception {}

    private static void sleep(long ms) throws CancelledException {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancelledException(); }
    }

    private static String isoNow() { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date()); }
    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "unknown error" : s.replace('\n',' ').trim(); }
    private static String shellQuote(String s) { return s == null ? "" : s.replace("'", "").replace("\n", " "); }
    private static String hex(int x) { return String.format(Locale.US, "%04X", x); }
}
