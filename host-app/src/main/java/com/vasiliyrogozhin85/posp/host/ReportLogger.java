package com.vasiliyrogozhin85.posp.host;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.MediaStore;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class ReportLogger {
    private final Context ctx;
    private final String appName;
    private final File file;
    private final Object lock = new Object();

    ReportLogger(Context c, String name) {
        ctx = c.getApplicationContext();
        appName = name;
        File dir = new File(ctx.getFilesDir(), "diagnostics");
        dir.mkdirs();
        file = new File(dir, "POSP_" + appName.replace(" ","_") + "_Session_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt");
        line("=== SESSION START ===");
        line("time=" + now());
        line("app=" + appName);
        line("android=" + Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT);
        line("device=" + Build.MANUFACTURER + " " + Build.MODEL);
        line("product=" + Build.PRODUCT + " board=" + Build.BOARD + " hardware=" + Build.HARDWARE);
        line("fingerprint=" + Build.FINGERPRINT);
        Runtime rt = Runtime.getRuntime();
        line("java_heap max=" + rt.maxMemory() + " total=" + rt.totalMemory() + " free=" + rt.freeMemory());
    }

    void line(String s) {
        synchronized(lock) {
            try(FileWriter fw = new FileWriter(file, true)) {
                fw.write(now()+" | "+s+"\n");
            } catch(Exception ignored) {}
        }
    }

    void throwable(String where, Throwable t) {
        line("EXCEPTION @" + where + ": " + t);
        synchronized(lock) {
            try(PrintWriter pw = new PrintWriter(new FileWriter(file,true))) {
                t.printStackTrace(pw);
            } catch(Exception ignored) {}
        }
    }

    File getFile(){ return file; }

    String exportToDownloads(String suffix) {
        try {
            String outName = file.getName().replace(".txt","") + "_" + suffix + ".txt";
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, outName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            if(Build.VERSION.SDK_INT >= 29) cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/POSPReports");
            android.net.Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if(uri == null) return "export uri=null";
            try(InputStream in = new FileInputStream(file);
                OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                if(out == null) return "export output=null";
                byte[] b = new byte[8192]; int n;
                while((n=in.read(b))>0) out.write(b,0,n);
            }
            return "Download/POSPReports/" + outName;
        } catch(Exception e) {
            line("EXPORT ERROR: " + e);
            return "export error: " + e.getMessage();
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
