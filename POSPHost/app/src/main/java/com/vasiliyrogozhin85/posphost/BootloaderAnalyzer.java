package com.vasiliyrogozhin85.posphost;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class BootloaderAnalyzer {
    static final String[] VARS = {
            "product","serialno","unlocked","secure","critical-unlocked","current-slot","slot-count",
            "is-userspace","userspace-fastboot","max-download-size","variant","hw-revision","version","version-bootloader","version-baseband",
            "battery-voltage","battery-soc-ok","off-mode-charge","charger-screen-enabled","snapshot-update-status","super-partition-name",
            "has-slot:boot","has-slot:vendor_boot","has-slot:init_boot","has-slot:dtbo","has-slot:vbmeta","has-slot:recovery","has-slot:system","has-slot:vendor","has-slot:product",
            "slot-successful:a","slot-unbootable:a","slot-retry-count:a","slot-successful:b","slot-unbootable:b","slot-retry-count:b",
            "partition-type:boot_a","partition-size:boot_a","partition-type:boot_b","partition-size:boot_b",
            "partition-type:vendor_boot_a","partition-size:vendor_boot_a","partition-type:vendor_boot_b","partition-size:vendor_boot_b",
            "partition-type:init_boot_a","partition-size:init_boot_a","partition-type:init_boot_b","partition-size:init_boot_b",
            "partition-type:dtbo_a","partition-size:dtbo_a","partition-type:dtbo_b","partition-size:dtbo_b",
            "partition-type:vbmeta_a","partition-size:vbmeta_a","partition-type:vbmeta_b","partition-size:vbmeta_b",
            "partition-type:recovery","partition-size:recovery","partition-type:super","partition-size:super",
            "partition-type:system_a","partition-size:system_a","partition-type:system_b","partition-size:system_b",
            "partition-type:vendor_a","partition-size:vendor_a","partition-type:vendor_b","partition-size:vendor_b",
            "is-logical:system_a","is-logical:system_b","is-logical:vendor_a","is-logical:vendor_b","is-logical:product_a","is-logical:product_b"
    };

    static JSONObject queryAll(FastbootUsbClient fb) throws Exception {
        JSONObject vars = new JSONObject();
        for (String v : VARS) {
            try { vars.put(v, fb.getVar(v)); }
            catch (Exception e) { vars.put(v, "ERROR: " + e.getMessage()); }
        }
        String rawAll;
        try { rawAll = fb.command("getvar:all", 45000); }
        catch (Exception e) { rawAll = "ERROR: " + e.getMessage(); }
        vars.put("all_raw", rawAll);
        vars.put("all_parsed", parseGetVarAll(rawAll));
        return vars;
    }

    static File collect(Context ctx, FastbootUsbClient fb, String usbSummary) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", "posp_host_bootloader_report");
        root.put("schema_version", 3);
        root.put("host_app", "POSP Host 0.7");
        root.put("created_utc", utcNow());
        root.put("host_android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        root.put("target_usb", usbSummary);

        JSONObject vars = queryAll(fb);
        root.put("fastboot", vars);

        JSONObject safety = new JSONObject();
        String unlocked = vars.optString("unlocked", "").toLowerCase(Locale.US);
        String product = vars.optString("product", "").toLowerCase(Locale.US);
        safety.put("appears_unlocked", unlocked.contains("yes") || unlocked.contains("true") || unlocked.matches(".*\\b1\\b.*"));
        safety.put("product_mentions_s97", product.contains("s97"));
        safety.put("critical_partition_auto_flash_allowed", false);
        safety.put("brom_bypass_allowed", false);
        safety.put("automatic_oem_command_probing_allowed", false);
        safety.put("note", "Read-only Fastboot questionnaire. No unlock, erase, flash, OEM or BROM command is issued by this analyzer.");
        root.put("safety", safety);

        File base = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "POSPHost");
        if (!base.exists() && !base.mkdirs()) throw new java.io.IOException("Cannot create output folder");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File f = new File(base, "POSP_Bootloader_Report_" + stamp + ".json");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    private static JSONObject parseGetVarAll(String raw) throws Exception {
        JSONObject out = new JSONObject();
        if (raw == null) return out;
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String x = line.trim();
            if (x.startsWith("INFO")) x = x.substring(4).trim();
            if (x.startsWith("(bootloader)")) x = x.substring("(bootloader)".length()).trim();
            if (x.startsWith("OKAY") || x.startsWith("FAIL") || x.isEmpty()) continue;
            int c = x.indexOf(':');
            if (c <= 0) continue;
            String k = x.substring(0, c).trim();
            String v = x.substring(c + 1).trim();
            if (!k.isEmpty()) out.put(k, v);
        }
        return out;
    }

    private static String utcNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
