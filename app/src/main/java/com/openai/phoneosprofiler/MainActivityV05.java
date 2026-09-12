package com.openai.phoneosprofiler;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivityV05 extends Activity {
    private static final int REQ_SCAN_PERMISSIONS = 401;

    private TextView status;
    private Button scan;
    private Button share;
    private Button bluetooth;
    private File lastReport;
    private boolean pendingScan;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Phone OS Profiler v0.5");
        title.setTextSize(24);

        TextView desc = new TextView(this);
        desc.setText("Сбор аппаратного профиля телефона для разработки кастомной ОС. Результат — JSON-рапорт с обычной и Bluetooth-отправкой.");
        desc.setPadding(0, dp(8), 0, dp(12));

        scan = new Button(this);
        scan.setText("СОБРАТЬ И СОХРАНИТЬ РАПОРТ");

        share = new Button(this);
        share.setText("ПОДЕЛИТЬСЯ РАПОРТОМ");
        share.setEnabled(false);

        bluetooth = new Button(this);
        bluetooth.setText("ОТПРАВИТЬ ПО BLUETOOTH");
        bluetooth.setEnabled(false);

        status = new TextView(this);
        status.setText("Готов к сканированию.");
        status.setTextIsSelectable(true);
        status.setPadding(0, dp(16), 0, 0);

        root.addView(title);
        root.addView(desc);
        root.addView(scan);
        root.addView(share);
        root.addView(bluetooth);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        scan.setOnClickListener(v -> requestPermissionsAndScan());
        share.setOnClickListener(v -> shareReport(false));
        bluetooth.setOnClickListener(v -> shareReport(true));
    }

    private void requestPermissionsAndScan() {
        ArrayList<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (!missing.isEmpty()) {
            pendingScan = true;
            requestPermissions(missing.toArray(new String[0]), REQ_SCAN_PERMISSIONS);
            return;
        }
        startScan();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SCAN_PERMISSIONS && pendingScan) {
            pendingScan = false;
            startScan();
        }
    }

    private void startScan() {
        scan.setEnabled(false);
        share.setEnabled(false);
        bluetooth.setEnabled(false);
        status.setText("Сбор данных...");

        new Thread(() -> {
            try {
                JSONObject report = MainActivity.ReportCollector.collect(this);
                report.put("schema_version", 4);
                JSONObject profiler = report.optJSONObject("profiler");
                if (profiler != null) profiler.put("version", "0.5");
                report.put("permissions", permissionStatus());
                report.put("bluetooth", bluetoothInfo());
                report.put("transport_capabilities", new JSONObject()
                        .put("android_share", true)
                        .put("bluetooth_obex_share", true));
                report.put("collection_notes", "Normal-app mode is Android-sandbox limited; use the bundled ADB collector for VINTF/HAL/device-tree/partition details when available. v0.5 uses a stable development signing key and applicationId for reliable upgrades.");

                lastReport = MainActivity.ReportCollector.save(this, report);
                String sha = sha256(lastReport);

                runOnUiThread(() -> {
                    status.setText("Готово.\n\n" + lastReport.getAbsolutePath() +
                            "\n\nSHA-256:\n" + sha +
                            "\n\nРапорт можно отправить обычным способом или отдельной кнопкой Bluetooth.");
                    scan.setEnabled(true);
                    share.setEnabled(true);
                    bluetooth.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Ошибка: " + e);
                    scan.setEnabled(true);
                });
            }
        }).start();
    }

    private JSONObject permissionStatus() throws Exception {
        JSONObject o = new JSONObject();
        o.put("read_phone_state", Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED);
        o.put("bluetooth_connect", Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
        return o;
    }

    private JSONObject bluetoothInfo() throws Exception {
        JSONObject o = new JSONObject();
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        o.put("adapter_present", adapter != null);
        if (adapter == null) return o;

        boolean allowed = Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        o.put("permission_connect", allowed);
        if (allowed) {
            try { o.put("enabled", adapter.isEnabled()); } catch (Exception e) { o.put("enabled_error", e.toString()); }
            try { o.put("name", adapter.getName()); } catch (Exception e) { o.put("name_error", e.toString()); }
        }
        return o;
    }

    private Intent baseShareIntent(Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, "Phone OS Profiler report");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("Phone OS Profiler report", uri));
        return i;
    }

    private void shareReport(boolean bluetoothOnly) {
        if (lastReport == null || !lastReport.exists()) {
            status.setText("Сначала соберите рапорт.");
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", lastReport);
        Intent base = baseShareIntent(uri);

        if (!bluetoothOnly) {
            startActivity(Intent.createChooser(base, "Отправить Phone OS Profiler Report"));
            return;
        }

        PackageManager pm = getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(base, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo r : handlers) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName == null ? "" : r.activityInfo.packageName.toLowerCase(Locale.US);
            String cls = r.activityInfo.name == null ? "" : r.activityInfo.name.toLowerCase(Locale.US);
            if (pkg.contains("bluetooth") || cls.contains("bluetooth")) {
                Intent bt = new Intent(base);
                bt.setComponent(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
                try {
                    startActivity(bt);
                    status.setText("Рапорт передан системному Bluetooth для выбора принимающего устройства.");
                    return;
                } catch (ActivityNotFoundException ignored) { }
            }
        }

        try {
            Intent bt = new Intent(base);
            bt.setPackage("com.android.bluetooth");
            startActivity(bt);
            status.setText("Рапорт передан системному Bluetooth для выбора принимающего устройства.");
        } catch (Exception e) {
            status.setText("Отдельный Bluetooth-компонент не найден. Открыто системное меню отправки — выберите Bluetooth.");
            startActivity(Intent.createChooser(base, "Выберите Bluetooth"));
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String sha256(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
            in.close();
            StringBuilder s = new StringBuilder();
            for (byte x : md.digest()) s.append(String.format(Locale.US, "%02x", x));
            return s.toString();
        } catch (Exception e) {
            return "unavailable:" + e.getClass().getSimpleName();
        }
    }
}
