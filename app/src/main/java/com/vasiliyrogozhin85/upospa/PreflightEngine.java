package com.vasiliyrogozhin85.upospa;

import org.json.*;
import java.util.*;

final class PreflightEngine {
    static final class Item {
        final String name, value, detail;
        final boolean pass;
        final boolean blocking;
        Item(String n, String v, String d, boolean p, boolean b) {
            name=n; value=v; detail=d; pass=p; blocking=b;
        }
    }

    static final class Result {
        final List<Item> items = new ArrayList<>();
        boolean canPrepare;
        boolean canInstall;
        String summary;
    }

    static Result evaluate(JSONObject deviceReport, FirmwareAnalyzer.Result fw) {
        Result r = new Result();
        JSONObject d = deviceReport == null ? null : deviceReport.optJSONObject("device");
        JSONObject b = deviceReport == null ? null : deviceReport.optJSONObject("battery");
        JSONObject s = deviceReport == null ? null : deviceReport.optJSONObject("storage");
        JSONObject p = deviceReport == null ? null : deviceReport.optJSONObject("system_properties");

        String model = d == null ? "unknown" : d.optString("model", "unknown");
        String device = d == null ? "" : d.optString("device", "");
        String product = d == null ? "" : d.optString("product", "");
        boolean diag = d != null;
        r.items.add(new Item("Устройство", model, "Диагностика завершена", diag, true));

        int cap = b == null ? -1 : b.optInt("capacity_percent", -1);
        boolean batteryOk = cap >= 50;
        r.items.add(new Item("Батарея", cap < 0 ? "—" : cap + "%", "Рекомендуется не менее 50%", batteryOk, true));

        long free = s == null ? 0 : s.optLong("avail_bytes", 0);
        long need = fw == null ? 0 : Math.max(1024L*1024L*1024L, fw.size + Math.min(fw.size, 4L*1024L*1024L*1024L));
        boolean spaceOk = fw == null || free <= 0 || free >= need;
        r.items.add(new Item("Свободное место", CompatibilityEvaluator.fmtBytes(free),
                fw == null ? "Пакет ещё не выбран" : "Проверка места для анализа и временных файлов", spaceOk, fw != null));

        String locked = p == null ? "" : p.optString("ro.boot.flash.locked", "");
        String state = p == null ? "" : p.optString("ro.boot.vbmeta.device_state", "");
        boolean unlocked = "0".equals(locked) || "unlocked".equalsIgnoreCase(state);
        r.items.add(new Item("Bootloader", unlocked ? "unlocked" : (locked.isEmpty() && state.isEmpty() ? "unknown" : "locked"),
                "Для Fastboot-записи загрузчик должен разрешать прошивку", unlocked, true));

        boolean packageOk = fw != null && fw.sha256 != null && fw.sha256.length() == 64;
        r.items.add(new Item("SHA-256", packageOk ? "проверен" : "не проверен", "Целостность выбранного пакета", packageOk, true));

        boolean identityOk = true;
        String identityText = "метаданные не ограничивают устройство";
        if (fw != null && !fw.expectedDevices.isEmpty()) {
            identityOk = false;
            for (String x : fw.expectedDevices) {
                if (x.equalsIgnoreCase(device) || x.equalsIgnoreCase(product) || model.toLowerCase(Locale.US).contains(x.toLowerCase(Locale.US))) {
                    identityOk = true; break;
                }
            }
            identityText = identityOk ? "совпадает" : "не совпадает";
        }
        r.items.add(new Item("Совместимость модели", identityText,
                fw == null ? "Выберите пакет ОС" : fw.expectedDevices.isEmpty() ? "В пакете нет pre-device" : "Ожидается: " + join(fw.expectedDevices),
                identityOk && fw != null, true));

        boolean recognized = fw != null && (fw.directImage || !fw.interesting.isEmpty());
        r.items.add(new Item("Структура пакета", recognized ? "распознана" : "не распознана",
                fw == null ? "Пакет не выбран" : fw.summary, recognized, true));

        boolean directReady = fw != null && fw.directImage && fw.inferredPartition != null && !fw.inferredPartition.isEmpty();
        r.items.add(new Item("Маршрут установки", directReady ? "Fastboot image" : (fw == null ? "—" : "план/OTA"),
                directReady ? "Раздел: " + fw.inferredPartition : "ZIP/payload в v0.8 beta анализируется и планируется без автоматической записи",
                directReady || (fw != null && recognized), false));

        r.canPrepare = diag && batteryOk && packageOk && identityOk && recognized && spaceOk;
        r.canInstall = r.canPrepare && unlocked && directReady;
        if (fw == null) r.summary = "Выберите пакет ОС для pre-flight";
        else if (!r.canPrepare) r.summary = "Pre-flight остановлен: есть блокирующие проверки";
        else if (r.canInstall) r.summary = "Direct IMG готов к подтверждённой Fastboot-установке";
        else r.summary = "Пакет подготовлен: доступен безопасный план установки";
        return r;
    }

    private static String join(List<String> xs) {
        StringBuilder s = new StringBuilder();
        for (String x : xs) { if (s.length() > 0) s.append(", "); s.append(x); }
        return s.toString();
    }

    private PreflightEngine() {}
}
