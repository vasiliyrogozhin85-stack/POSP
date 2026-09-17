package com.vasiliyrogozhin85.upospa;

import org.json.*;
import java.util.*;

final class InstallPlanner {
    static final class Step {
        final int number;
        final String title, command, note;
        final boolean destructive;
        Step(int n, String t, String c, String no, boolean d) {
            number=n; title=t; command=c; note=no; destructive=d;
        }
    }

    static final class Plan {
        final List<Step> steps = new ArrayList<>();
        String route;
        boolean executableInV03;
    }

    static Plan build(JSONObject report, FirmwareAnalyzer.Result fw) {
        Plan p = new Plan();
        if (fw == null) {
            p.route = "Пакет не выбран";
            return p;
        }
        int n = 1;
        p.steps.add(new Step(n++, "Зафиксировать состояние устройства", "diagnostics + partition map", "Сохранить отчёт и текущий slot", false));
        p.steps.add(new Step(n++, "Проверить пакет", "SHA-256 " + shortSha(fw.sha256), "Проверка целостности и метаданных", false));

        if (fw.directImage && fw.inferredPartition != null && !fw.inferredPartition.isEmpty()) {
            p.route = "Fastboot direct image";
            p.executableInV03 = true;
            p.steps.add(new Step(n++, "Перейти в Fastboot/Fastbootd", "adb reboot bootloader / adb reboot fastboot", "Режим выбирается по типу раздела", false));
            p.steps.add(new Step(n++, "Повторно сверить target", "fastboot getvar product; fastboot getvar unlocked", "Перед записью uPOSPa повторно читает target", false));
            p.steps.add(new Step(n++, "Записать образ", "fastboot flash " + fw.inferredPartition + " <" + fw.name + ">", "Изменяет раздел устройства", true));
            p.steps.add(new Step(n++, "Проверить slot", "fastboot getvar current-slot", "Не переключать слот автоматически без подтверждения", false));
            p.steps.add(new Step(n, "Перезагрузка", "fastboot reboot", "После успешной записи", false));
        } else if (fw.interesting.contains("payload.bin")) {
            p.route = "OTA payload";
            p.executableInV03 = false;
            p.steps.add(new Step(n++, "Проверить OTA metadata", "pre-device / ota-type / security patch", "Пакет анализируется без записи", false));
            p.steps.add(new Step(n++, "Подготовить payload", "payload.bin", "Автоматическая установка payload оставлена для следующего этапа", false));
            p.steps.add(new Step(n, "Не выполнять запись", "dry-run", "v0.8 beta не прошивает payload.bin автоматически", false));
        } else {
            p.route = "Планируемая установка";
            p.executableInV03 = false;
            p.steps.add(new Step(n++, "Проверить найденные компоненты", fw.summary, "Сопоставить образы с разделами", false));
            p.steps.add(new Step(n, "Остановиться перед записью", "dry-run", "Для этого формата нет безопасного автоматического маршрута в v0.8 beta", false));
        }
        return p;
    }

    static JSONObject toJson(Plan p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("route", p.route);
        o.put("executable_in_v0_3", p.executableInV03);
        JSONArray a = new JSONArray();
        for (Step s : p.steps) {
            JSONObject x = new JSONObject();
            x.put("number", s.number);
            x.put("title", s.title);
            x.put("command", s.command);
            x.put("note", s.note);
            x.put("destructive", s.destructive);
            a.put(x);
        }
        o.put("steps", a);
        return o;
    }

    private static String shortSha(String s) {
        if (s == null) return "—";
        return s.length() > 16 ? s.substring(0, 16) + "…" : s;
    }

    private InstallPlanner() {}
}
