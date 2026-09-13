package com.vasiliyrogozhin85.posphost;

import android.content.Context;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class BackupPlanner {
    private static final String[] IMPORTANT = {
            "boot","vendor_boot","init_boot","dtbo","vbmeta","vbmeta_system","super","metadata",
            "persist","nvram","nvdata","nvcfg","protect1","protect2","proinfo","frp"
    };

    static File create(Context ctx, AdbUsbClient adb) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", "posp_host_backup_plan");
        root.put("schema_version", 1);
        root.put("host_app", "POSP Host 0.7");
        root.put("product", clean(adb.shell("getprop ro.product.name", 8000)));
        root.put("model", clean(adb.shell("getprop ro.product.model", 8000)));
        root.put("hardware", clean(adb.shell("getprop ro.hardware", 8000)));
        root.put("board", clean(adb.shell("getprop ro.product.board", 8000)));
        root.put("slot_suffix", clean(adb.shell("getprop ro.boot.slot_suffix", 8000)));
        root.put("flash_locked", clean(adb.shell("getprop ro.boot.flash.locked", 8000)));
        root.put("verified_boot_state", clean(adb.shell("getprop ro.boot.verifiedbootstate", 8000)));

        String map = adb.shell("ls -l /dev/block/by-name 2>&1; ls -l /dev/block/platform/bootdevice/by-name 2>&1; ls -l /dev/block/mapper 2>&1", 20000);
        root.put("partition_map_raw", map);

        JSONArray candidates = new JSONArray();
        String low = map.toLowerCase(Locale.US);
        for (String p : IMPORTANT) {
            JSONObject x = new JSONObject();
            x.put("partition", p);
            x.put("seen_in_map", low.contains(p.toLowerCase(Locale.US)));
            boolean critical = p.equals("nvram") || p.equals("nvdata") || p.equals("nvcfg") || p.startsWith("protect") || p.equals("proinfo") || p.equals("frp");
            x.put("critical_identity_or_security_data", critical);
            x.put("automatic_write_allowed", false);
            x.put("automatic_erase_allowed", false);
            candidates.put(x);
        }
        root.put("candidates", candidates);
        root.put("raw_partition_backup_created", false);
        root.put("note", "Без root или поддерживаемого Fastboot fetch обычный ADB не может безопасно прочитать большинство raw-разделов. Этот файл фиксирует карту и план резервирования; он не является образом разделов.");

        File base = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "POSPHost");
        if (!base.exists() && !base.mkdirs()) throw new java.io.IOException("Cannot create output folder");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File f = new File(base, "POSP_Backup_Plan_" + stamp + ".json");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
