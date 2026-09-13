package com.vasiliyrogozhin85.posp.client;

import android.app.*;
import android.os.*;
import android.hardware.usb.*;
import android.content.*;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.view.*;
import android.widget.*;
import com.vasiliyrogozhin85.posp.common.Protocol;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(7,17,31);
    private final int SURFACE = Color.rgb(13,27,42);
    private final int ACCENT = Color.rgb(53,230,107);
    private final int TEXT = Color.rgb(242,247,255);
    private final int MUTED = Color.rgb(169,184,199);

    private UsbManager usbManager;
    private UsbAccessory accessory;
    private ParcelFileDescriptor pfd;
    private FileInputStream in;
    private FileOutputStream out;
    private TextView status;
    private File lastReport;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        usbManager = (UsbManager)getSystemService(USB_SERVICE);
        setContentView(buildUi());
        detectAccessory(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        detectAccessory(intent);
    }

    @Override protected void onDestroy() {
        closeAccessory();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root);

        root.addView(tv("▲  POSP Client", 28, TEXT, true));
        root.addView(tv("v0.1 • DOOGEE S97 Pro companion", 13, MUTED, false));

        LinearLayout card = card();
        status = tv("Host не подключён", 16, TEXT, true);
        card.addView(status);
        root.addView(card, lpTop(14));

        root.addView(section("Данные устройства"));
        root.addView(button("Полный локальный сбор данных", v -> collect()));
        root.addView(button("Передать последний отчёт Host", v -> sendLastReport()));

        root.addView(section("Управление"));
        root.addView(tile("Перезагрузка", "В Android", "Обычное приложение не имеет системного права reboot. Команда должна выполняться Host через ADB."));
        root.addView(tile("Bootloader", "Перейти в загрузчик", "Переход выполняется Host через ADB после подтверждения пользователя."));
        root.addView(tile("Recovery", "Перейти в recovery", "Переход выполняется Host через ADB."));
        root.addView(tile("Fastbootd", "Перейти в userspace fastboot", "Переход выполняется Host через ADB/Recovery, если устройство поддерживает fastbootd."));

        root.addView(section("Сервис"));
        root.addView(tile("Состояние", "Модель, Android, board, battery", "Client собирает доступные обычному приложению данные и передаёт их Host."));
        root.addView(button("? Справка", v -> showHelp()));
        return scroll;
    }

    private void detectAccessory(Intent intent) {
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(intent.getAction())) {
            accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        }
        if (accessory == null) {
            UsbAccessory[] list = usbManager.getAccessoryList();
            if (list != null && list.length > 0) accessory = list[0];
        }
        if (accessory != null) openAccessory();
        else {
            status.setText("● Ожидание POSP Host\nПодключите телефоны USB-OTG.");
            status.setTextColor(MUTED);
        }
    }

    private void openAccessory() {
        try {
            pfd = usbManager.openAccessory(accessory);
            if (pfd == null) throw new IllegalStateException("openAccessory() = null");
            FileDescriptor fd = pfd.getFileDescriptor();
            in = new FileInputStream(fd);
            out = new FileOutputStream(fd);
            status.setText("● Подключён к POSP Host\n" + accessory.getManufacturer() + " / " + accessory.getModel());
            status.setTextColor(ACCENT);
            new Thread(this::listenLoop, "posp-client-link").start();
            sendLine(Protocol.HELLO + " CLIENT " + Build.MODEL);
        } catch (Exception e) {
            status.setText("Ошибка Client Link: " + e.getMessage());
            status.setTextColor(Color.rgb(255,92,112));
        }
    }

    private void listenLoop() {
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                String cmd = line.trim();
                if (Protocol.PING.equals(cmd)) {
                    sendLine(Protocol.PONG);
                } else if (Protocol.COLLECT_REPORT.equals(cmd)) {
                    runOnUiThread(this::collect);
                }
            }
        } catch (Exception ignored) {}
    }

    private void collect() {
        try {
            JSONObject j = new JSONObject();
            j.put("schema", "posp_client_report");
            j.put("schema_version", 1);
            j.put("generated_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()));

            JSONObject d = new JSONObject();
            d.put("manufacturer", Build.MANUFACTURER);
            d.put("brand", Build.BRAND);
            d.put("model", Build.MODEL);
            d.put("device", Build.DEVICE);
            d.put("product", Build.PRODUCT);
            d.put("board", Build.BOARD);
            d.put("hardware", Build.HARDWARE);
            d.put("fingerprint", Build.FINGERPRINT);
            j.put("device", d);

            JSONObject a = new JSONObject();
            a.put("release", Build.VERSION.RELEASE);
            a.put("sdk", Build.VERSION.SDK_INT);
            a.put("security_patch", Build.VERSION.SECURITY_PATCH);
            j.put("android", a);

            JSONObject mem = new JSONObject();
            android.app.ActivityManager am = (android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            mem.put("total_bytes", mi.totalMem);
            mem.put("available_bytes", mi.availMem);
            j.put("memory", mem);

            JSONObject st = new JSONObject();
            android.os.StatFs sf = new android.os.StatFs(getFilesDir().getAbsolutePath());
            st.put("total_bytes", sf.getTotalBytes());
            st.put("available_bytes", sf.getAvailableBytes());
            j.put("storage", st);

            JSONArray features = new JSONArray();
            PackageManager pm = getPackageManager();
            for (android.content.pm.FeatureInfo f : pm.getSystemAvailableFeatures()) {
                if (f.name != null) features.put(f.name);
            }
            j.put("features", features);

            File dir = new File(getExternalFilesDir(null), "reports");
            dir.mkdirs();
            lastReport = new File(dir, "POSP_Client_Report_" + System.currentTimeMillis() + ".json");
            try (FileOutputStream fos = new FileOutputStream(lastReport)) {
                fos.write(j.toString(2).getBytes("UTF-8"));
            }
            Toast.makeText(this, "Отчёт сохранён", Toast.LENGTH_SHORT).show();
            if (out != null) sendReport(lastReport);
        } catch (Exception e) {
            info("Ошибка сбора", e.toString());
        }
    }

    private void sendLastReport() {
        if (lastReport == null || !lastReport.exists()) {
            info("Нет отчёта", "Сначала выполните «Полный локальный сбор данных».");
            return;
        }
        if (out == null) {
            info("Host не подключён", "Отчёт сохранён локально, но Client Link сейчас не активен.");
            return;
        }
        new Thread(() -> {
            try { sendReport(lastReport); }
            catch (Exception e) { runOnUiThread(() -> info("Передача", e.toString())); }
        }).start();
    }

    private synchronized void sendReport(File f) throws Exception {
        sendLine(Protocol.REPORT_BEGIN + " " + f.length());
        try (FileInputStream fin = new FileInputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = fin.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        }
        sendLine("\n" + Protocol.REPORT_END);
    }

    private synchronized void sendLine(String s) throws Exception {
        if (out != null) {
            out.write(Protocol.line(s));
            out.flush();
        }
    }

    private void closeAccessory() {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
    }

    private void showHelp() {
        info("Справка POSP Client",
                "Client устанавливается непосредственно на DOOGEE S97 Pro.\n\n" +
                "Полный локальный сбор — создаёт JSON с данными, доступными обычному Android-приложению.\n\n" +
                "Передать отчёт Host — отправляет последний отчёт по USB Client Link (Android Open Accessory).\n\n" +
                "Перезагрузка/Bootloader/Recovery/Fastbootd — выполняются со стороны Host через ADB, потому что обычный Client не имеет системного права reboot.\n\n" +
                "Client Link не заменяет ADB/Fastboot: он дополняет их и удобен для отчётов, команд приложения и передачи файлов.");
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
        b.setText(s); b.setTextColor(TEXT); b.setTextSize(15); b.setAllCaps(false);
        b.setBackgroundColor(Color.rgb(18,40,58)); b.setOnClickListener(c);
        b.setLayoutParams(lpTop(8));
        return b;
    }

    private View tile(String t, String s, String help) {
        LinearLayout box = card();
        box.addView(tv(t, 18, TEXT, true));
        box.addView(tv(s, 13, MUTED, false));
        box.setOnClickListener(v -> info(t, help));
        box.setLayoutParams(lpTop(8));
        return box;
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams lpTop(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        return p;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
