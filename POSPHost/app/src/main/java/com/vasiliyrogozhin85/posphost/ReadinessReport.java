package com.vasiliyrogozhin85.posphost;

import org.json.JSONArray;
import org.json.JSONObject;

final class ReadinessReport {
    final JSONArray checks = new JSONArray();
    int passed = 0;
    int total = 0;

    void add(String id, String label, boolean ok, String detail) {
        total++;
        if (ok) passed++;
        JSONObject x = new JSONObject();
        try {
            x.put("id", id);
            x.put("label", label);
            x.put("ok", ok);
            x.put("detail", detail == null ? "" : detail);
            checks.put(x);
        } catch (Exception ignored) {}
    }

    int percent() { return total == 0 ? 0 : (int)Math.round(100.0 * passed / total); }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("percent", percent());
            o.put("passed", passed);
            o.put("total", total);
            o.put("checks", checks);
        } catch (Exception ignored) {}
        return o;
    }

    String toText() {
        StringBuilder s = new StringBuilder();
        s.append("Готовность: ").append(percent()).append("%\n");
        for (int i = 0; i < checks.length(); i++) {
            JSONObject c = checks.optJSONObject(i);
            if (c == null) continue;
            s.append(c.optBoolean("ok") ? "[OK] " : "[--] ")
                    .append(c.optString("label"));
            String d = c.optString("detail");
            if (!d.isEmpty()) s.append(" — ").append(d);
            s.append('\n');
        }
        return s.toString();
    }
}
