package com.vasiliyrogozhin85.upospa;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ReportLogger {
    private final Context ctx;
    private final File file;
    private final Object lock = new Object();

    ReportLogger(Context c, String name) {
        ctx = c.getApplicationContext();
        File dir = new File(ctx.getFilesDir(), "diagnostics");
        dir.mkdirs();
        file = new File(dir, "uPOSPa_" + name.replace(" ", "_") + "_Session_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt");
        line("=== SESSION START ===");
        line("android=" + Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT);
        line("device=" + Build.MANUFACTURER + " " + Build.MODEL);
        line("product=" + Build.PRODUCT + " device=" + Build.DEVICE +
                " board=" + Build.BOARD + " hardware=" + Build.HARDWARE);
        line("fingerprint=" + Build.FINGERPRINT);
    }

    void line(String s) {
        synchronized (lock) {
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(now() + " | " + s + "\n");
            } catch (Exception ignored) {}
        }
    }

    void throwable(String where, Throwable t) {
        line("EXCEPTION @" + where + ": " + t);
        synchronized (lock) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
                t.printStackTrace(pw);
            } catch (Exception ignored) {}
        }
    }

    File getFile() { return file; }

    String exportToDownloads(String suffix) {
        String outName = file.getName().replace(".txt", "") + "_" + suffix + ".txt";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, outName);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/uPOSPaReports");
                android.net.Uri uri = ctx.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "export uri=null";
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                    if (out == null) return "export output=null";
                    copy(in, out);
                }
                return "Download/uPOSPaReports/" + outName;
            } else {
                File base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (base == null) base = ctx.getFilesDir();
                File dir = new File(base, "uPOSPaReports");
                dir.mkdirs();
                File dst = new File(dir, outName);
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = new FileOutputStream(dst)) {
                    copy(in, out);
                }
                return dst.getAbsolutePath();
            }
        } catch (Exception e) {
            line("EXPORT ERROR: " + e);
            return "export error: " + e.getMessage();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
