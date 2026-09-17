package com.vasiliyrogozhin85.upospa;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class WizardActivity extends Activity {
    private int step = 0;
    private String scenario = WizardEngine.FULL;
    private String role = "host";
    private LinearLayout root;
    private TextView stepText, title, detail, progressText, log;
    private ProgressBar progress;
    private Button next, back, save, cancel;
    private File finalReport;
    private WizardEngine engine;
    private volatile boolean running;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        UiKit.screen(this);
        setContentView(build());
        showStep();
    }

    @Override public void onBackPressed() {
        if (running) {
            confirmCancel();
            return;
        }
        if (step > 0) { step--; showStep(); }
        else super.onBackPressed();
    }

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,28));
        sv.addView(root);
        return sv;
    }

    private void showStep() {
        root.removeAllViews();
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = UiKit.tv(this,"‹",38,UiKit.HOST,true); close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> onBackPressed());
        top.addView(close,new LinearLayout.LayoutParams(UiKit.dp(this,38),UiKit.dp(this,50)));
        top.addView(UiKit.header(this,"uPOSPa • WIZARD",UiKit.HOST),new LinearLayout.LayoutParams(0,-2,1));
        root.addView(top);
        stepText = UiKit.tv(this, stepLabel(), 13, UiKit.MUTED, true); root.addView(stepText);

        if (step == 0) showScenario();
        else if (step == 1) showRole();
        else if (step == 2) showConsent();
        else showExecution();
    }

    private String stepLabel() {
        if (step == 0) return "Шаг 1/4 • тип работы";
        if (step == 1) return "Шаг 2/4 • роль телефона";
        if (step == 2) return "Шаг 3/4 • данные и действия";
        return "Шаг 4/4 • автоматическая процедура";
    }

    private void showScenario() {
        root.addView(UiKit.section(this,"Что нужно сделать?"));
        root.addView(option("БЫСТРАЯ ДИАГНОСТИКА", "Android / ADB / локальный отчёт без перезагрузки", WizardEngine.QUICK, UiKit.CLIENT));
        root.addView(option("ПОЛНАЯ ДИАГНОСТИКА", "Android + Bootloader/Fastboot + A/B + карта разделов", WizardEngine.FULL, UiKit.HOST));
        root.addView(option("ПОДГОТОВКА К УСТАНОВКЕ ОС", "Полная диагностика + Fastbootd + dynamic partitions", WizardEngine.OS_PREP, UiKit.WARN));
        navButtons();
    }

    private void showRole() {
        root.addView(UiKit.section(this,"Роль этого телефона"));
        root.addView(roleOption("HOST", "Этот телефон управляет процедурой и собирает итоговый отчёт", "host", UiKit.HOST));
        root.addView(roleOption("CLIENT", "Этот телефон диагностируется и ждёт команд Host", "client", UiKit.CLIENT));
        LinearLayout note = UiKit.card(this);
        note.addView(UiKit.tv(this,"Для полностью автоматического Wizard установите один и тот же uPOSPa на оба телефона. На Host нужна поддержка USB OTG/Host. На Client нужно один раз разрешить USB debugging и RSA-ключ Host.",13,UiKit.MUTED,false));
        root.addView(note,UiKit.top(this,12));
        navButtons();
    }

    private void showConsent() {
        root.addView(UiKit.section(this,"Перед запуском"));
        LinearLayout card = UiKit.card(this);
        card.addView(UiKit.tv(this,"Будут собраны",16,UiKit.TEXT,true));
        card.addView(UiKit.tv(this, collectionDescription(),13,UiKit.MUTED,false),UiKit.top(this,7));
        card.addView(UiKit.tv(this,"Не собираются",16,UiKit.TEXT,true),UiKit.top(this,12));
        card.addView(UiKit.tv(this,"Фотографии, сообщения, контакты, документы и содержимое пользовательских файлов.",13,UiKit.CLIENT,false),UiKit.top(this,6));
        root.addView(card);

        LinearLayout actions = UiKit.card(this);
        actions.addView(UiKit.tv(this,"Что Wizard может сделать автоматически",16,UiKit.TEXT,true));
        String a = role.equals("host") ? hostActions() : clientActions();
        actions.addView(UiKit.tv(this,a,13,UiKit.MUTED,false),UiKit.top(this,7));
        root.addView(actions,UiKit.top(this,10));

        LinearLayout safety = UiKit.card(this);
        safety.addView(UiKit.tv(this,"Безопасность",16,UiKit.WARN,true));
        safety.addView(UiKit.tv(this,"Wizard не разблокирует bootloader, не стирает userdata и не прошивает разделы. Если соединение пропадёт, Host ждёт повторного появления устройства и продолжает с безопасного шага.",13,UiKit.MUTED,false),UiKit.top(this,7));
        root.addView(safety,UiKit.top(this,10));
        navButtons();
    }

    private String collectionDescription() {
        String base = "• производитель, модель, device/product, serial, Android и build fingerprint\n"+
                "• CPU/kernel, RAM, storage, батарея, экран, SELinux, USB/ADB\n"+
                "• системные свойства Treble, AVB, A/B, Virtual A/B, Dynamic Partitions\n"+
                "• локальный uPOSPa DeviceSnapshot Client, если приложение доступно";
        if (WizardEngine.QUICK.equals(scenario)) return base;
        base += "\n• Fastboot getvar, состояние slot A/B и карта разделов";
        if (WizardEngine.OS_PREP.equals(scenario)) base += "\n• Fastbootd/userspace и dynamic partition readiness";
        return base;
    }

    private String hostActions() {
        String s = "• сам найдёт Client по USB и подключит ADB\n"+
                "• запустит uPOSPa Client и запросит локальный snapshot\n"+
                "• соберёт Android/ADB диагностику";
        if (!WizardEngine.QUICK.equals(scenario)) s += "\n• перезагрузит Client в Bootloader/Fastboot и прочитает параметры";
        if (WizardEngine.OS_PREP.equals(scenario)) s += "\n• при поддержке перейдёт в Fastbootd для проверки dynamic partitions";
        if (!WizardEngine.QUICK.equals(scenario)) s += "\n• вернёт Client в обычный Android";
        s += "\n• откроет uPOSPa на Client с уведомлением о завершении\n• сформирует единый JSON-отчёт на Host";
        return s;
    }

    private String clientActions() {
        return "• откроет режим ожидания Host\n• по команде Host создаст локальный DeviceSnapshot\n• после возврата в Android покажет результат Wizard\n• системное уведомление о завершении будет показано, если Android разрешает уведомления для uPOSPa";
    }

    private View option(String name, String desc, String value, int color) {
        LinearLayout c = UiKit.card(this);
        TextView n = UiKit.tv(this,(scenario.equals(value)?"✓  ":"○  ")+name,15,scenario.equals(value)?color:UiKit.TEXT,true);
        c.addView(n);
        c.addView(UiKit.tv(this,desc,12,UiKit.MUTED,false),UiKit.top(this,5));
        c.setOnClickListener(v -> { scenario=value; showStep(); });
        return withTop(c,8);
    }

    private View roleOption(String name, String desc, String value, int color) {
        LinearLayout c = UiKit.card(this);
        c.addView(UiKit.tv(this,(role.equals(value)?"✓  ":"○  ")+name,16,role.equals(value)?color:UiKit.TEXT,true));
        c.addView(UiKit.tv(this,desc,13,UiKit.MUTED,false),UiKit.top(this,5));
        c.setOnClickListener(v -> { role=value; showStep(); });
        return withTop(c,8);
    }

    private View withTop(View v, int dp) { v.setLayoutParams(UiKit.top(this,dp)); return v; }

    private void navButtons() {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        if (step > 0) {
            Button b = UiKit.darkButton(this,"Назад",v->{step--;showStep();});
            row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        }
        Button n = UiKit.button(this, step==2 ? (role.equals("host")?"НАЧАТЬ WIZARD":"ПЕРЕЙТИ В ОЖИДАНИЕ") : "Далее", step==2?UiKit.CLIENT:UiKit.HOST, v->next());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-2,1); if(step>0)p.leftMargin=UiKit.dp(this,8); row.addView(n,p);
        root.addView(row,UiKit.top(this,16));
    }

    private void next() {
        if (step < 2) { step++; showStep(); return; }
        if ("client".equals(role)) {
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},9001);
            }
            Intent i = new Intent(this,ClientActivity.class);
            i.putExtra("wizard_waiting",true);
            i.putExtra("wizard_scenario",scenario);
            startActivity(i);
            finish();
            return;
        }
        PackageManager pm=getPackageManager();
        if(!pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)){
            new AlertDialog.Builder(this).setTitle("USB Host недоступен").setMessage("Этот телефон не объявляет поддержку USB Host/OTG. Выберите роль Client или используйте другой Host.").setPositiveButton("Понятно",null).show();
            return;
        }
        step=3; showStep(); startEngine();
    }

    private void showExecution() {
        title=UiKit.tv(this,"Подготовка Wizard…",20,UiKit.TEXT,true); root.addView(title,UiKit.top(this,16));
        detail=UiKit.tv(this,"Не отключайте кабель. На первом ADB-подключении подтвердите RSA на Client.",13,UiKit.MUTED,false); root.addView(detail,UiKit.top(this,6));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgress(0); root.addView(progress,UiKit.top(this,14));
        progressText=UiKit.tv(this,"0%",13,UiKit.MUTED,true); progressText.setGravity(Gravity.END); root.addView(progressText,UiKit.top(this,4));

        LinearLayout state=UiKit.card(this); state.addView(UiKit.tv(this,"Автоматический сценарий",14,UiKit.HOST,true));
        state.addView(UiKit.tv(this,scenarioTitle()+"\nHost автоматически ждёт переподключения между ADB / Fastboot / Fastbootd.",12,UiKit.MUTED,false),UiKit.top(this,5)); root.addView(state,UiKit.top(this,10));

        log=UiKit.tv(this,"",11,UiKit.MUTED,false); log.setTextIsSelectable(true); LinearLayout lc=UiKit.card(this); lc.addView(UiKit.tv(this,"Журнал Wizard",14,UiKit.TEXT,true)); lc.addView(log,UiKit.top(this,6)); root.addView(lc,UiKit.top(this,10));
        save=UiKit.button(this,"СОХРАНИТЬ ОТЧЁТ",UiKit.CLIENT,v->saveReport()); save.setEnabled(false); save.setAlpha(.45f); root.addView(save,UiKit.top(this,12));
        cancel=UiKit.button(this,"Остановить Wizard",UiKit.ERROR,v->confirmCancel()); root.addView(cancel);
    }

    private String scenarioTitle(){return WizardEngine.QUICK.equals(scenario)?"Быстрая диагностика":WizardEngine.OS_PREP.equals(scenario)?"Подготовка к установке ОС":"Полная диагностика";}

    private void startEngine() {
        running=true;
        try {
            engine = new WizardEngine(this,scenario,new WizardEngine.Listener(){
                public void onPhase(String p,String d,int n){runOnUiThread(()->{if(title!=null)title.setText(p);if(detail!=null)detail.setText(d);if(progress!=null)progress.setProgress(n);if(progressText!=null)progressText.setText(n+"%");});}
                public void onLog(String s){runOnUiThread(()->appendLog(s));}
                public void onComplete(File f){runOnUiThread(()->wizardDone(f,null));}
                public void onError(String m,File f){runOnUiThread(()->wizardDone(f,m));}
            });
            engine.start();
        } catch(Exception e){wizardDone(null,e.getMessage());}
    }

    private void wizardDone(File f,String error){
        running=false;finalReport=f;
        if(cancel!=null){cancel.setText("Закрыть Wizard");cancel.setBackground(UiKit.bg(this,UiKit.SURF2,10,UiKit.STROKE,1));cancel.setOnClickListener(v->finish());}
        if(f!=null&&save!=null){save.setEnabled(true);save.setAlpha(1f);}
        if(error==null){if(title!=null){title.setText("✓  Сбор завершён");title.setTextColor(UiKit.CLIENT);}if(detail!=null)detail.setText("Client возвращён в Android. uPOSPa открыт на Client. Нажмите «Сохранить отчёт» на Host.");if(progress!=null)progress.setProgress(100);if(progressText!=null)progressText.setText("100%");}
        else {if(title!=null){title.setText("Wizard завершён с ошибкой");title.setTextColor(UiKit.ERROR);}if(detail!=null)detail.setText(error+(f==null?"":"\nЧастичный отчёт сохранён."));appendLog("ERROR: "+error);}
    }

    private void saveReport(){
        if(finalReport==null)return;
        new Thread(()->{try{String p=WizardReportExporter.export(this,finalReport);runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Отчёт сохранён").setMessage(p).setPositiveButton("Понятно",null).show());}catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Ошибка сохранения").setMessage(String.valueOf(e)).setPositiveButton("Понятно",null).show());}},"wizard-export").start();
    }

    private void confirmCancel(){
        if(!running){finish();return;}
        new AlertDialog.Builder(this).setTitle("Остановить Wizard?").setMessage("Сбор прекратится. uPOSPa попробует безопасно вернуть Client из Fastboot/Fastbootd в Android и показать статус остановки. Если кабель уже отключён, возврат может потребовать ручного запуска Android.").setNegativeButton("Продолжить",null).setPositiveButton("Остановить",(d,w)->{if(engine!=null)engine.cancel();}).show();
    }

    private void appendLog(String s){if(log!=null)log.append("["+new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"] "+s+"\n");}
}
