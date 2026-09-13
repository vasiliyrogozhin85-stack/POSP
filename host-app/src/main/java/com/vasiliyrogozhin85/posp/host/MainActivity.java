package com.vasiliyrogozhin85.posp.host;

import android.app.*;
import android.os.*;
import android.hardware.usb.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import com.vasiliyrogozhin85.posp.common.UsbModeDetector;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(7,17,31);
    private final int SURFACE = Color.rgb(13,27,42);
    private final int ACCENT = Color.rgb(45,168,255);
    private final int TEXT = Color.rgb(242,247,255);
    private final int MUTED = Color.rgb(169,184,199);

    private UsbManager usbManager;
    private TextView status;
    private UsbDevice currentDevice;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        usbManager = (UsbManager)getSystemService(USB_SERVICE);
        setContentView(buildUi());
        refreshDevice();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root);

        TextView title = tv("▲  POSP Host", 28, TEXT, true);
        root.addView(title);
        root.addView(tv("v0.8 • Управление • Диагностика • Установка", 13, MUTED, false));

        LinearLayout card = card();
        status = tv("Поиск USB-устройства…", 16, TEXT, true);
        card.addView(status);
        root.addView(card, lpTop(14));

        root.addView(section("Подключение"));
        root.addView(button("Найти устройство", v -> refreshDevice()));
        root.addView(button("Запустить POSP Client USB", v -> startAccessoryMode()));

        root.addView(section("Основные режимы"));
        root.addView(gridButton("Диагностика", "Сбор ADB/Client-данных", v -> info("Диагностика",
                "Host объединяет данные POSP Client и сервисный опрос USB/ADB. В этой версии готова инфраструктура USB и Client Link.")));
        root.addView(gridButton("Перезагрузка", "Android / Bootloader / Recovery / Fastbootd", v -> info("Перезагрузка",
                "Команды перезагрузки будут выполняться через ADB-транспорт Host. До завершения ADB-движка кнопка работает как безопасный информационный пункт.")));
        root.addView(gridButton("Загрузчик", "Проверка и подготовка к разблокировке", v -> info("Загрузчик",
                "Раздел предназначен для подробного опроса Fastboot/AVB/A-B и последующей штатной разблокировки. Опасные команды должны требовать отдельного подтверждения.")));
        root.addView(gridButton("Установка ОС", "Проверка пакета и мастер установки", v -> info("Установка ОС",
                "Будущий мастер проверит модель S97 Pro, MT6785, board, A/B, SHA-256 и состояние резервной копии до прошивки.")));
        root.addView(gridButton("Файлы", "Host ↔ Client", v -> info("Файлы",
                "Передача отчётов и будущих пакетов POSP OS по USB Client Link.")));

        root.addView(section("Справка"));
        root.addView(button("? Что делает каждая функция", v -> showHelp()));
        return scroll;
    }

    private void refreshDevice() {
        currentDevice = null;
        Map<String, UsbDevice> devices = usbManager.getDeviceList();
        if (devices.isEmpty()) {
            status.setText("● USB-устройство не найдено\nПодключите DOOGEE S97 Pro через OTG.");
            status.setTextColor(MUTED);
            return;
        }
        currentDevice = devices.values().iterator().next();
        UsbModeDetector.Mode mode = UsbModeDetector.detect(currentDevice);
        status.setText("● Подключено\n" +
                currentDevice.getDeviceName() + "\n" +
                "VID:PID " + hex(currentDevice.getVendorId()) + ":" + hex(currentDevice.getProductId()) +
                "\nРежим: " + mode);
        status.setTextColor(ACCENT);
    }

    private void startAccessoryMode() {
        if (currentDevice == null) {
            info("Нет устройства", "Сначала подключите DOOGEE через USB-OTG и нажмите «Найти устройство».");
            return;
        }
        if (!usbManager.hasPermission(currentDevice)) {
            PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                    new Intent(getPackageName()+".USB_PERMISSION"),
                    PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(currentDevice, pi);
            info("Разрешение USB", "Android запросил доступ к USB-устройству. После разрешения нажмите кнопку ещё раз.");
            return;
        }

        try (UsbDeviceConnection c = usbManager.openDevice(currentDevice)) {
            if (c == null) throw new IllegalStateException("openDevice() вернул null");
            byte[] proto = new byte[2];
            int r = c.controlTransfer(0xC0, 51, 0, 0, proto, 2, 1000);
            if (r < 0) throw new IllegalStateException("AOA protocol request rejected");
            sendString(c, 0, "POSP");
            sendString(c, 1, "POSP Client Link");
            sendString(c, 2, "Host/Client USB bridge");
            sendString(c, 3, "1.0");
            sendString(c, 4, "https://github.com/vasiliyrogozhin85-stack/POSP");
            sendString(c, 5, "POSP-HOST");
            int start = c.controlTransfer(0x40, 53, 0, 0, null, 0, 1000);
            if (start < 0) throw new IllegalStateException("AOA start rejected");
            info("POSP Client Link", "Команда перехода в Android Open Accessory отправлена. DOOGEE должен переподключиться по USB и открыть POSP Client.");
        } catch (Exception e) {
            info("Client Link не запущен", "Устройство не приняло Android Open Accessory: " + e.getMessage());
        }
    }

    private void sendString(UsbDeviceConnection c, int index, String value) throws Exception {
        byte[] data = (value + "\0").getBytes("UTF-8");
        int r = c.controlTransfer(0x40, 52, 0, index, data, data.length, 1000);
        if (r < 0) throw new IllegalStateException("AOA string " + index + " rejected");
    }

    private void showHelp() {
        info("Справка POSP Host",
                "Найти устройство — определяет USB и режим ADB/Fastboot/Accessory.\n\n" +
                "Запустить POSP Client USB — переводит совместимый Android в режим Android Open Accessory, чтобы Client мог обмениваться данными с Host без компьютера.\n\n" +
                "Диагностика — сбор и объединение отчётов.\n\n" +
                "Перезагрузка — будущие ADB-команды Android/Bootloader/Recovery/Fastbootd.\n\n" +
                "Загрузчик — безопасный анализ Fastboot/AVB/A-B перед разблокировкой.\n\n" +
                "Установка ОС — проверка пакета, SHA-256 и совместимости перед прошивкой.\n\n" +
                "Файлы — отчёты и пакеты между телефонами.\n\n" +
                "Опасные операции должны выполняться только после отдельного подтверждения.");
    }

    private void info(String t, String m) {
        new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Понятно", null).show();
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackgroundColor(SURFACE);
        return l;
    }

    private View section(String s) {
        TextView v = tv(s, 17, TEXT, true);
        v.setPadding(0, dp(20), 0, dp(8));
        return v;
    }

    private Button button(String s, View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.rgb(18,40,58));
        b.setOnClickListener(c);
        b.setPadding(dp(14), dp(11), dp(14), dp(11));
        b.setLayoutParams(lpTop(8));
        return b;
    }

    private View gridButton(String title, String sub, View.OnClickListener c) {
        LinearLayout box = card();
        TextView t = tv(title, 18, TEXT, true);
        TextView s = tv(sub, 13, MUTED, false);
        box.addView(t); box.addView(s);
        box.setOnClickListener(c);
        box.setLayoutParams(lpTop(8));
        return box;
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams lpTop(int dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(dp);
        return p;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
    private String hex(int v) { return String.format(Locale.US, "%04X", v); }
}
