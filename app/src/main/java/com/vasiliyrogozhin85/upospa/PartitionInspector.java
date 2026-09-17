package com.vasiliyrogozhin85.upospa;

import org.json.*;
import java.io.*;
import java.util.*;

final class PartitionInspector {
    static JSONArray collectLocal() {
        LinkedHashMap<String, JSONObject> map = new LinkedHashMap<>();
        collectByName(map);
        collectProcPartitions(map);
        collectMounts(map);
        JSONArray out = new JSONArray();
        int n = 0;
        for (JSONObject x : map.values()) {
            out.put(x);
            if (++n >= 192) break;
        }
        return out;
    }

    private static void collectByName(Map<String, JSONObject> out) {
        String[] dirs = {
                "/dev/block/by-name",
                "/dev/block/bootdevice/by-name",
                "/dev/block/platform/bootdevice/by-name"
        };
        for (String p : dirs) {
            File d = new File(p);
            File[] fs;
            try { fs = d.listFiles(); } catch (Exception e) { fs = null; }
            if (fs == null) continue;
            for (File f : fs) {
                try {
                    JSONObject x = get(out, f.getName());
                    x.put("name", f.getName());
                    x.put("path", f.getAbsolutePath());
                    try { x.put("canonical", f.getCanonicalPath()); } catch (Exception ignored) {}
                    x.put("source", "by-name");
                } catch (Exception ignored) {}
            }
            if (!out.isEmpty()) return;
        }
    }

    private static void collectProcPartitions(Map<String, JSONObject> out) {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/partitions"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("major")) continue;
                String[] a = line.split("\\s+");
                if (a.length < 4) continue;
                String name = a[3];
                long blocks;
                try { blocks = Long.parseLong(a[2]); } catch (Exception e) { continue; }
                JSONObject x = get(out, name);
                x.put("name", name);
                x.put("size_bytes", blocks * 1024L);
                if (!x.has("source")) x.put("source", "proc");
            }
        } catch (Exception ignored) {}
    }

    private static void collectMounts(Map<String, JSONObject> out) {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] a = line.split("\\s+");
                if (a.length < 3) continue;
                String dev = a[0], mount = a[1], fs = a[2];
                String name = dev.substring(dev.lastIndexOf('/') + 1);
                if (name.isEmpty()) continue;
                JSONObject x = get(out, name);
                x.put("name", name);
                x.put("mount", mount);
                x.put("fs", fs);
                if (!x.has("source")) x.put("source", "mounts");
            }
        } catch (Exception ignored) {}
    }

    private static JSONObject get(Map<String, JSONObject> out, String name) {
        JSONObject x = out.get(name);
        if (x == null) {
            x = new JSONObject();
            out.put(name, x);
        }
        return x;
    }

    private PartitionInspector() {}
}
