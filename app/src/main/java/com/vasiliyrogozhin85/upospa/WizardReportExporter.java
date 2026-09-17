package com.vasiliyrogozhin85.upospa;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.*;

final class WizardReportExporter {
    static String export(Context ctx, File source) throws Exception {
        if (source == null || !source.isFile()) throw new FileNotFoundException("Wizard report not found");
        String name = source.getName();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/uPOSPaReports");
            android.net.Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IOException("MediaStore insert failed");
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("MediaStore openOutputStream failed");
                copy(in, out);
            }
            return "Download/uPOSPaReports/" + name;
        }
        File base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) base = ctx.getFilesDir();
        File dir = new File(base, "uPOSPaReports");
        dir.mkdirs();
        File dst = new File(dir, name);
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dst)) {
            copy(in, out);
        }
        return dst.getAbsolutePath();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }

    private WizardReportExporter() {}
}
