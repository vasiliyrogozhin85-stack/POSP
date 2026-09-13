package com.vasiliyrogozhin85.posphost;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Read-only evidence collector used before any bootloader unlock attempt.
 * It intentionally does not send unlock, erase, flash, OEM or MediaTek BROM commands.
 */
final class UnlockResearchCollector {
    private static final String[][] ADB_PROBES = new String[][]{
            {"identity", "echo manufacturer=$(getprop ro.product.manufacturer); echo model=$(getprop ro.product.model); echo product=$(getprop ro.product.name); echo device=$(getprop ro.product.device); echo board=$(getprop ro.product.board); echo hardware=$(getprop ro.hardware); echo platform=$(getprop ro.board.platform); echo boot_hardware=$(getprop ro.boot.hardware); echo fingerprint=$(getprop ro.build.fingerprint)"},
            {"boot_state", "echo flash_locked=$(getprop ro.boot.flash.locked); echo vbmeta_device_state=$(getprop ro.boot.vbmeta.device_state); echo verified_boot_state=$(getprop ro.boot.verifiedbootstate); echo verity_mode=$(getprop ro.boot.veritymode); echo bootloader=$(getprop ro.boot.bootloader); echo bootmode=$(getprop ro.bootmode); echo slot_suffix=$(getprop ro.boot.slot_suffix); echo avb_version=$(getprop ro.boot.avb_version)"},
            {"oem_unlock_signals", "echo ro_oem_unlock_supported=$(getprop ro.oem_unlock_supported); echo sys_oem_unlock_allowed=$(getprop sys.oem_unlock_allowed); echo persistent_oem_unlock_allowed=$(getprop persist.sys.oem_unlock_allowed); echo development_settings_enabled=$(settings get global development_settings_enabled 2>&1); echo adb_enabled=$(settings get global adb_enabled 2>&1)"},
            {"partition_model", "echo dynamic_partitions=$(getprop ro.boot.dynamic_partitions); echo virtual_ab=$(getprop ro.virtual_ab.enabled); echo retrofit_dynamic=$(getprop ro.boot.dynamic_partitions_retrofit); echo super_partition=$(getprop ro.boot.super_partition); echo slot_suffix=$(getprop ro.boot.slot_suffix); echo treble=$(getprop ro.treble.enabled); echo vndk=$(getprop ro.vndk.version)"},
            {"usb_debug", "echo usb_config=$(getprop persist.sys.usb.config); echo sys_usb_config=$(getprop sys.usb.config); echo usb_state=$(getprop sys.usb.state); dumpsys usb 2>&1 | head -400"},
            {"bootctl", "bootctl hal-info 2>&1; echo --- get-current-slot ---; bootctl get-current-slot 2>&1; echo --- suffix ---; bootctl get-suffix 0 2>&1; bootctl get-suffix 1 2>&1; echo --- slot states ---; bootctl is-slot-bootable 0 2>&1; bootctl is-slot-bootable 1 2>&1; bootctl is-slot-marked-successful 0 2>&1; bootctl is-slot-marked-successful 1 2>&1"},
            {"partitions", "cat /proc/partitions 2>&1; echo --- by-name ---; ls -l /dev/block/by-name 2>&1; ls -l /dev/block/platform/bootdevice/by-name 2>&1; echo --- mapper ---; ls -l /dev/block/mapper 2>&1"},
            {"avb_props", "getprop | grep -iE 'avb|vbmeta|verity|verified|flash.locked|device_state|unlock' 2>&1"},
            {"mediatek_boot_props", "getprop | grep -iE 'mtk|mediatek|preloader|lk|bootloader|tee|seccfg|secro|rpmb' 2>&1 | head -800"},
            {"recovery_update", "echo recovery_api=$(getprop ro.build.version.incremental); ls -l /system/bin/update_engine /system/bin/bootctl 2>&1; getprop | grep -iE 'recovery|fastboot|update_engine' 2>&1 | head -500"}
    };

    static File collectAdb(Context ctx, AdbUsbClient adb, String usbSummary) throws Exception {
        JSONObject root = base("adb", usbSummary);
        JSONObject probes = new JSONObject();
        JSONArray limitations = new JSONArray();
        for (String[] p : ADB_PROBES) {
            String out;
            try { out = adb.shell(p[1], 45000); }
            catch (Exception e) { out = "ERROR: " + e.getMessage(); }
            probes.put(p[0], out == null ? "" : out.trim());
            String low = out == null ? "" : out.toLowerCase(Locale.US);
            if (low.contains("permission denied") || low.contains("not permitted")) limitations.put(p[0] + ": partially blocked by Android permissions");
        }
        root.put("adb_probes", probes);
        root.put("limitations", limitations);
        root.put("assessment", assessAdb(probes));
        return write(ctx, root, "POSP_Unlock_Research_ADB_");
    }

    static File collectFastboot(Context ctx, FastbootUsbClient fb, String usbSummary) throws Exception {
        JSONObject root = base("fastboot", usbSummary);
        JSONObject vars = BootloaderAnalyzer.queryAll(fb);
        root.put("fastboot", vars);
        root.put("assessment", assessFastboot(vars));
        root.put("safety_note", "Read-only questionnaire. No unlock, erase, flash, OEM or BROM command was sent.");
        return write(ctx, root, "POSP_Unlock_Research_FASTBOOT_");
    }

    private static JSONObject base(String mode, String usbSummary) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", "posp_host_unlock_research");
        root.put("schema_version", 1);
        root.put("host_app", "POSP Host 0.7");
        root.put("mode", mode);
        root.put("created_utc", utcNow());
        root.put("host_android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        root.put("target_usb", usbSummary);
        root.put("purpose", "Collect evidence required to determine the supported bootloader unlock path for the target S97 Pro without bypass attempts.");
        return root;
    }

    private static JSONObject assessAdb(JSONObject p) throws Exception {
        JSONObject a = new JSONObject();
        String identity = p.optString("identity", "").toLowerCase(Locale.US);
        String boot = p.optString("boot_state", "").toLowerCase(Locale.US);
        String oem = p.optString("oem_unlock_signals", "").toLowerCase(Locale.US);
        a.put("target_looks_like_s97pro", identity.contains("s97"));
        a.put("target_looks_like_mt6785", identity.contains("mt6785"));
        a.put("bootloader_appears_locked", boot.contains("flash_locked=1") || boot.contains("device_state=locked") || boot.contains("vbmeta_device_state=locked"));
        a.put("verified_boot_green", boot.contains("verified_boot_state=green"));
        a.put("oem_unlock_supported_signal", triState(oem, "ro_oem_unlock_supported="));
        a.put("oem_unlock_allowed_signal", triState(oem, "sys_oem_unlock_allowed="));
        a.put("next_step", "Reboot to bootloader, reconnect POSP Host, then run the same Unlock Research questionnaire in Fastboot mode. Do not unlock until both reports are reviewed.");
        return a;
    }

    private static JSONObject assessFastboot(JSONObject vars) throws Exception {
        JSONObject a = new JSONObject();
        String product = vars.optString("product", "").toLowerCase(Locale.US);
        String unlocked = vars.optString("unlocked", "").toLowerCase(Locale.US);
        String critical = vars.optString("critical-unlocked", "").toLowerCase(Locale.US);
        String secure = vars.optString("secure", "").toLowerCase(Locale.US);
        String userspace = vars.optString("is-userspace", "").toLowerCase(Locale.US);
        a.put("product_response", vars.optString("product", ""));
        a.put("product_mentions_s97", product.contains("s97"));
        a.put("appears_unlocked", positive(unlocked));
        a.put("critical_appears_unlocked", positive(critical));
        a.put("secure_response", secure);
        a.put("fastbootd_userspace", positive(userspace));
        a.put("standard_unlock_command_not_tested", true);
        a.put("next_step", "Review this report together with the ADB unlock-research report. If OEM unlocking is allowed and the target identity matches, a later explicit user-confirmed standard fastboot flashing unlock may be tested.");
        return a;
    }

    private static String triState(String text, String key) {
        int p = text.indexOf(key);
        if (p < 0) return "unknown";
        int e = text.indexOf('\n', p);
        String v = text.substring(p + key.length(), e < 0 ? text.length() : e).trim();
        if (v.equals("1") || v.equals("true") || v.equals("yes")) return "true";
        if (v.equals("0") || v.equals("false") || v.equals("no")) return "false";
        return v.isEmpty() ? "unknown" : v;
    }

    private static boolean positive(String s) { return s.contains("yes") || s.contains("true") || s.matches(".*\\b1\\b.*"); }

    private static File write(Context ctx, JSONObject root, String prefix) throws Exception {
        File base = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "POSPHost");
        if (!base.exists() && !base.mkdirs()) throw new java.io.IOException("Cannot create output folder");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File f = new File(base, prefix + stamp + ".json");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    private static String utcNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
