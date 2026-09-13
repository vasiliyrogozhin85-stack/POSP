package com.vasiliyrogozhin85.posphost;

import android.content.Context;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DiagnosticBundle {
    static File build(Context ctx, List<File> inputs, String logText) throws Exception {
        File base = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "POSPHost");
        if (!base.exists() && !base.mkdirs()) throw new IOException("Cannot create output folder");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File out = new File(base, "POSPHost_S97Pro_Diagnostic_" + stamp + ".zip");
        JSONArray entries = new JSONArray();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            for (File f : inputs) {
                if (f == null || !f.isFile()) continue;
                String name = "reports/" + f.getName();
                zos.putNextEntry(new ZipEntry(name));
                try (InputStream in = new FileInputStream(f)) {
                    byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) zos.write(b, 0, n);
                }
                zos.closeEntry();
                JSONObject e = new JSONObject(); e.put("file", name); e.put("sha256", sha256(f)); e.put("bytes", f.length()); entries.put(e);
            }
            byte[] log = (logText == null ? "" : logText).getBytes(StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("operation_log.txt")); zos.write(log); zos.closeEntry();
            JSONObject manifest = new JSONObject();
            manifest.put("schema", "posp_host_diagnostic_bundle"); manifest.put("schema_version", 2); manifest.put("host_app", "POSP Host 0.7"); manifest.put("entries", entries);
            byte[] m = manifest.toString(2).getBytes(StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("manifest.json")); zos.write(m); zos.closeEntry();
        }
        return out;
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) { byte[] b = new byte[8192]; int n; while ((n=in.read(b))>0) md.update(b,0,n); }
        StringBuilder s = new StringBuilder(); for (byte x: md.digest()) s.append(String.format(Locale.US,"%02x",x)); return s.toString();
    }
}
