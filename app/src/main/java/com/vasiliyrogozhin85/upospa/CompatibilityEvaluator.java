package com.vasiliyrogozhin85.upospa;

import org.json.*;
import java.util.*;

final class CompatibilityEvaluator {
    static final class Check {
        final String name, value, detail;
        final int level;
        Check(String n, String v, String d, int l) { name=n; value=v; detail=d; level=l; }
    }

    static final class Result {
        final List<Check> checks = new ArrayList<>();
        boolean diagnosticComplete;
        boolean compatibilityReady;
        boolean installReady;
        String summary;
    }

    static Result evaluate(JSONObject r) {
        Result out = new Result();
        JSONObject d = r.optJSONObject("device");
        JSONObject a = r.optJSONObject("android");
        JSONObject p = r.optJSONObject("system_properties");
        JSONObject b = r.optJSONObject("battery");
        JSONObject st = r.optJSONObject("storage");

        String model = d == null ? "unknown" : d.optString("model","unknown");
        String abi = "unknown";
        if (d != null) {
            JSONArray abis=d.optJSONArray("abis");
            if (abis != null && abis.length()>0) abi=abis.optString(0,"unknown");
        }
        out.checks.add(new Check("Модель", model, "Идентифицировано устройство", 2));
        out.checks.add(new Check("Архитектура", abi, "Основной ABI", 2));

        String locked = prop(p,"ro.boot.flash.locked","");
        String state = prop(p,"ro.boot.vbmeta.device_state","");
        boolean unlocked = "0".equals(locked) || "unlocked".equalsIgnoreCase(state);
        String lockValue = unlocked ? "unlocked" :
                ("1".equals(locked) || "locked".equalsIgnoreCase(state)) ? "locked" : "unknown";
        out.checks.add(new Check("Bootloader", lockValue,
                unlocked ? "Разблокирован" : "Для прошивки может потребоваться штатная разблокировка", unlocked?2:1));

        String vb = prop(p,"ro.boot.verifiedbootstate","unknown");
        out.checks.add(new Check("Verified Boot", vb, "Состояние AVB/Verified Boot",
                "green".equalsIgnoreCase(vb)?2:1));

        boolean dyn = boolProp(p,"ro.boot.dynamic_partitions");
        boolean vab = boolProp(p,"ro.virtual_ab.enabled");
        boolean ab = boolProp(p,"ro.build.ab_update") || !prop(p,"ro.boot.slot_suffix","").isEmpty()
                || !prop(p,"ro.boot.slot","").isEmpty();
        out.checks.add(new Check("Dynamic Partitions", UiKit.yesNo(dyn), "Раздел super/dynamic", dyn?2:0));
        out.checks.add(new Check("Virtual A/B", UiKit.yesNo(vab), "Virtual A/B OTA", vab?2:0));
        out.checks.add(new Check("A/B", UiKit.yesNo(ab), "Слоты обновления", ab?2:0));

        String patch = a == null ? "" : a.optString("security_patch","");
        out.checks.add(new Check("Security Patch", patch.isEmpty()?"—":patch, "Патч безопасности Android", 2));

        long free = st == null ? 0 : st.optLong("avail_bytes",0);
        out.checks.add(new Check("Свободно памяти", fmtBytes(free), "Место для отчётов/образов",
                free >= 4L*1024*1024*1024 ? 2 : 1));

        int cap = b == null ? -1 : b.optInt("capacity_percent",-1);
        out.checks.add(new Check("Батарея", cap<0?"—":cap+"%", "Перед прошивкой желательно ≥ 50%",
                cap>=50?2:1));

        out.diagnosticComplete = d != null && a != null;
        out.compatibilityReady = out.diagnosticComplete;
        out.installReady = out.compatibilityReady && unlocked && cap >= 50;
        if (!out.diagnosticComplete) out.summary="Диагностика не завершена";
        else if (out.installReady) out.summary="Устройство готово к подготовке установки";
        else if (!unlocked) out.summary="Диагностика завершена • загрузчик требует проверки";
        else if (cap < 50) out.summary="Диагностика завершена • зарядите устройство";
        else out.summary="Диагностика завершена • требуется дополнительная проверка";
        return out;
    }

    private static String prop(JSONObject p,String k,String def) { return p==null?def:p.optString(k,def); }
    private static boolean boolProp(JSONObject p,String k) {
        String s=prop(p,k,"").toLowerCase(Locale.US);
        return "1".equals(s)||"true".equals(s)||"yes".equals(s);
    }
    static String fmtBytes(long b) {
        if (b<=0) return "—";
        double gb=b/(1024.0*1024.0*1024.0);
        if (gb>=1) return String.format(Locale.US,"%.1f ГБ",gb);
        return String.format(Locale.US,"%.0f МБ",b/(1024.0*1024.0));
    }
    private CompatibilityEvaluator(){}
}
