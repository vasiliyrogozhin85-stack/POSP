package com.vasiliyrogozhin85.upospa;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;

final class WizardStateStore {
    private final File file;

    WizardStateStore(Context c) {
        file = new File(c.getFilesDir(), "wizard_session.json");
    }

    synchronized JSONObject load() {
        try {
            if (!file.isFile()) return new JSONObject();
            byte[] b = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            }
            return new JSONObject(new String(b, "UTF-8"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    synchronized void save(JSONObject j) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(j.toString(2).getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    synchronized void phase(String session, String scenario, String phase, int progress, boolean active) {
        try {
            JSONObject j = load();
            j.put("session", session);
            j.put("scenario", scenario);
            j.put("phase", phase);
            j.put("progress", progress);
            j.put("active", active);
            j.put("updated_ms", System.currentTimeMillis());
            save(j);
        } catch (Exception ignored) {}
    }

    synchronized void clear() {
        if (file.exists()) file.delete();
    }
}
