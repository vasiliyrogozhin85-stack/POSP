package com.vasiliyrogozhin85.upospa;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;

import org.json.*;
import java.io.*;

public class ReportActivity extends Activity {
    private static final int PICK_PACKAGE=50;
    private LinearLayout body, grid, preflightBox, planBox;
    private TextView subtitle, packageInfo, partitionInfo;
    private JSONObject report;
    private CompatibilityEvaluator.Result readiness;
    private FirmwareAnalyzer.Result firmware;
    private Uri firmwareUri;
    private PreflightEngine.Result preflight;
    private InstallPlanner.Plan plan;
    private ReportLogger logger;
    private boolean reportFromFile;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        UiKit.screen(this);
        logger=new ReportLogger(this,"Report_v0.8 beta");
        setContentView(ui());
        String path=getIntent().getStringExtra("report_path");
        if(path!=null&&!path.isEmpty())loadReportFile(path);else collect();
    }

    private View ui(){
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(UiKit.BG);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,28));sv.addView(body);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=UiKit.tv(this,"‹",38,UiKit.HOST,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,38),UiKit.dp(this,50)));
        top.addView(UiKit.header(this,"uPOSPa • ОТЧЁТ",UiKit.HOST),new LinearLayout.LayoutParams(0,-2,1));
        TextView gear=UiKit.tv(this,"⚙",26,UiKit.MUTED,false);gear.setOnClickListener(v->info("uPOSPa v0.8 beta","Единый отчёт, карта разделов, pre-flight и Install Plan."));top.addView(gear);
        body.addView(top);
        body.addView(UiKit.tv(this,"совместимость с ОС • разделы • pre-flight • install plan",13,UiKit.MUTED,false));

        subtitle=UiKit.tv(this,"Сбор данных устройства…",15,UiKit.TEXT,true);body.addView(UiKit.card(this,subtitle),UiKit.top(this,12));

        body.addView(UiKit.section(this,"Состояние устройства"));
        grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);body.addView(grid);

        body.addView(UiKit.section(this,"Карта разделов"));
        partitionInfo=UiKit.tv(this,"Сбор доступной карты /dev/block, /proc/partitions и mounts…",12,UiKit.MUTED,false);
        body.addView(UiKit.card(this,partitionInfo));

        body.addView(UiKit.section(this,"Готовность устройства"));
        LinearLayout states=UiKit.card(this);states.addView(UiKit.tv(this,"Статусы появятся после диагностики.",13,UiKit.MUTED,false));states.setTag("states");body.addView(states);

        body.addView(UiKit.button(this,"✓  Повторить диагностику",UiKit.HOST,v->collect()));
        body.addView(UiKit.button(this,"↓  Выбрать пакет ОС / IMG",UiKit.CLIENT,v->pickPackage()));

        packageInfo=UiKit.tv(this,"Пакет ОС не выбран. uPOSPa проверит SHA-256, OTA metadata, pre-device, содержимое ZIP и имя прямого IMG.",13,UiKit.MUTED,false);
        body.addView(UiKit.card(this,packageInfo),UiKit.top(this,10));

        body.addView(UiKit.section(this,"Pre-flight"));
        preflightBox=UiKit.card(this);preflightBox.addView(UiKit.tv(this,"Выберите пакет для проверки.",13,UiKit.MUTED,false));body.addView(preflightBox);

        body.addView(UiKit.section(this,"Install Plan"));
        planBox=UiKit.card(this);planBox.addView(UiKit.tv(this,"План будет построен после анализа пакета.",13,UiKit.MUTED,false));body.addView(planBox);

        body.addView(UiKit.button(this,"⚡  Открыть SERVICE / FASTBOOT",UiKit.WARN,v->openService()));
        body.addView(UiKit.darkButton(this,"Экспортировать диагностический отчёт",v->exportReport()));
        body.addView(UiKit.darkButton(this,"Экспортировать Install Plan JSON",v->exportPlan()));
        return sv;
    }

    private void loadReportFile(String path){
        subtitle.setText("Загрузка отчёта Client…");
        new Thread(()->{
            try{
                File f=new File(path);
                byte[] data=new byte[(int)Math.min(f.length(),16L*1024L*1024L)];
                int off=0;try(FileInputStream in=new FileInputStream(f)){while(off<data.length){int n=in.read(data,off,data.length-off);if(n<0)break;off+=n;}}
                JSONObject r=new JSONObject(new String(data,0,off,"UTF-8"));
                reportFromFile=true;report=r;readiness=CompatibilityEvaluator.evaluate(r);
                logger.line("loaded client report="+path);
                runOnUiThread(()->render(r,readiness));
            }catch(Exception e){logger.throwable("load report",e);runOnUiThread(()->{subtitle.setText("Не удалось открыть отчёт Client: "+e.getMessage());collect();});}
        },"upospa-load-client-report").start();
    }

    private void collect(){
        subtitle.setText("Сбор данных устройства…");
        new Thread(()->{
            try{
                JSONObject r=DeviceSnapshot.collect(this);CompatibilityEvaluator.Result ev=CompatibilityEvaluator.evaluate(r);File f=DeviceSnapshot.write(this,r);
                report=r;readiness=ev;logger.line("report="+f.getAbsolutePath());new DeviceHistoryStore(this).add(r,"report");
                if(firmware!=null){preflight=PreflightEngine.evaluate(report,firmware);plan=InstallPlanner.build(report,firmware);}
                runOnUiThread(()->{render(r,ev);renderPackageState();});
            }catch(Exception e){logger.throwable("collect",e);runOnUiThread(()->subtitle.setText("Ошибка диагностики: "+e.getMessage()));}
        },"upospa-report-ui").start();
    }

    private void render(JSONObject r,CompatibilityEvaluator.Result ev){
        JSONObject d=r.optJSONObject("device");String make=d==null?"":d.optString("manufacturer","");String model=d==null?"unknown":d.optString("model","unknown");
        subtitle.setText("▣  "+make+" "+model+"\n"+ev.summary);
        grid.removeAllViews();
        for(int i=0;i<ev.checks.size();i+=2){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setWeightSum(2f);
            row.addView(tile(ev.checks.get(i)),new LinearLayout.LayoutParams(0,-2,1f));
            if(i+1<ev.checks.size()){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.leftMargin=UiKit.dp(this,8);row.addView(tile(ev.checks.get(i+1)),p);}else row.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1f));
            grid.addView(row,UiKit.top(this,8));
        }
        LinearLayout states=findStates();states.removeAllViews();
        states.addView(statusLine("✓  Диагностика завершена",ev.diagnosticComplete));
        states.addView(statusLine((ev.compatibilityReady?"✓":"•")+"  Совместимость проанализирована",ev.compatibilityReady),UiKit.top(this,6));
        TextView install=statusLine((ev.installReady?"✓":"!")+"  Базовая готовность к прошивке",ev.installReady);if(!ev.installReady)install.setTextColor(UiKit.WARN);states.addView(install,UiKit.top(this,6));
        renderPartitions(r.optJSONArray("partitions_local"));
    }

    private void renderPartitions(JSONArray a){
        if(a==null||a.length()==0){partitionInfo.setText("Карта разделов недоступна из обычного Android API. Для точной карты используйте SERVICE → Fastboot → «Построить карту разделов».");return;}
        StringBuilder s=new StringBuilder();int shown=0;
        for(int i=0;i<a.length()&&shown<24;i++){
            JSONObject x=a.optJSONObject(i);if(x==null)continue;
            String name=x.optString("name","");if(name.isEmpty())continue;
            s.append(name);
            long size=x.optLong("size_bytes",0);if(size>0)s.append("  ").append(CompatibilityEvaluator.fmtBytes(size));
            String mount=x.optString("mount","");if(!mount.isEmpty())s.append("  → ").append(mount);
            s.append('\n');shown++;
        }
        if(a.length()>shown)s.append("… ещё ").append(a.length()-shown).append(" записей\n");
        s.append("\nТочная Fastboot partition-size карта доступна в SERVICE.");partitionInfo.setText(s.toString());
    }

    private void renderPackageState(){
        if(firmware==null){renderPreflight(null);renderPlan(null);return;}
        String sh=firmware.sha256;String shortSha=sh.length()>24?sh.substring(0,24)+"…":sh;
        StringBuilder s=new StringBuilder();
        s.append("Файл: ").append(firmware.name).append("\nТип: ").append(firmware.type).append("\nРазмер: ").append(CompatibilityEvaluator.fmtBytes(firmware.size)).append("\nSHA-256: ").append(shortSha).append("\n").append(firmware.summary);
        if(!firmware.expectedDevices.isEmpty())s.append("\npre-device: ").append(join(firmware.expectedDevices));
        if(!firmware.otaType.isEmpty())s.append("\nota-type: ").append(firmware.otaType);
        if(!firmware.securityPatch.isEmpty())s.append("\nsecurity patch: ").append(firmware.securityPatch);
        if(report!=null&&firmware!=null&&firmware.name.toLowerCase(java.util.Locale.US).endsWith(".zip")){try{java.io.File tmp=FirmwarePackageCache.copyUriToCache(this,firmwareUri,firmware.name);org.json.JSONObject mf=FirmwareManifest.fromZip(tmp);if(mf.length()>0)s.append("\nuPOSPa manifest: ").append(FirmwareManifest.validate(mf,report));}catch(Exception ignored){}} packageInfo.setText(s.toString());renderPreflight(preflight);renderPlan(plan);
    }

    private void renderPreflight(PreflightEngine.Result pf){
        preflightBox.removeAllViews();
        if(pf==null){preflightBox.addView(UiKit.tv(this,"Выберите пакет для проверки.",13,UiKit.MUTED,false));return;}
        preflightBox.addView(UiKit.tv(this,pf.summary,14,pf.canPrepare?UiKit.CLIENT:UiKit.WARN,true));
        for(PreflightEngine.Item x:pf.items){
            int color=x.pass?UiKit.CLIENT:(x.blocking?UiKit.ERROR:UiKit.WARN);
            preflightBox.addView(UiKit.tv(this,(x.pass?"✓":"!")+"  "+x.name+": "+x.value+"\n     "+x.detail,12,color,false),UiKit.top(this,7));
        }
    }

    private void renderPlan(InstallPlanner.Plan p){
        planBox.removeAllViews();
        if(p==null){planBox.addView(UiKit.tv(this,"План будет построен после анализа пакета.",13,UiKit.MUTED,false));return;}
        planBox.addView(UiKit.tv(this,"Маршрут: "+p.route+(p.executableInV03?" • выполняется в SERVICE":" • dry-run"),14,p.executableInV03?UiKit.CLIENT:UiKit.WARN,true));
        for(InstallPlanner.Step x:p.steps){
            int c=x.destructive?UiKit.WARN:UiKit.TEXT;
            planBox.addView(UiKit.tv(this,x.number+". "+x.title+"\n   "+x.command+"\n   "+x.note,12,c,x.destructive),UiKit.top(this,8));
        }
    }

    private LinearLayout findStates(){for(int i=0;i<body.getChildCount();i++){View v=body.getChildAt(i);if(v instanceof LinearLayout&&"states".equals(v.getTag()))return(LinearLayout)v;}LinearLayout x=UiKit.card(this);x.setTag("states");body.addView(x);return x;}
    private View tile(CompatibilityEvaluator.Check c){LinearLayout box=UiKit.card(this);box.addView(UiKit.tv(this,c.name,12,UiKit.MUTED,false));box.addView(UiKit.tv(this,c.value,15,c.level==2?UiKit.TEXT:c.level==1?UiKit.WARN:UiKit.MUTED,true),UiKit.top(this,4));return box;}
    private TextView statusLine(String s,boolean ok){return UiKit.tv(this,s,14,ok?UiKit.CLIENT:UiKit.MUTED,true);}

    private void pickPackage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK_PACKAGE);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_PACKAGE||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        firmwareUri=data.getData();try{getContentResolver().takePersistableUriPermission(firmwareUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        packageInfo.setText("Анализ пакета…");
        new Thread(()->{
            try{
                firmware=FirmwareAnalyzer.analyze(this,firmwareUri);preflight=PreflightEngine.evaluate(report,firmware);plan=InstallPlanner.build(report,firmware);
                logger.line("package "+firmware.name+" sha256="+firmware.sha256+" route="+plan.route);runOnUiThread(this::renderPackageState);
            }catch(Exception e){logger.throwable("package",e);runOnUiThread(()->packageInfo.setText("Ошибка анализа пакета: "+e.getMessage()));}
        },"upospa-package").start();
    }

    private void openService(){
        Intent i=new Intent(this,ServiceActivity.class);
        if(firmwareUri!=null&&firmware!=null&&firmware.directImage)i.putExtra("image_uri",firmwareUri.toString());
        if(reportFromFile&&report!=null){
            JSONObject d=report.optJSONObject("device");
            if(d!=null){i.putExtra("expected_device",d.optString("device",""));i.putExtra("expected_product",d.optString("product",""));}
        }
        startActivity(i);
    }

    private void exportReport(){new Thread(()->{try{if(report==null)report=DeviceSnapshot.collect(this);File f=DeviceSnapshot.write(this,report);runOnUiThread(()->info("Отчёт сохранён",f.getAbsolutePath()));}catch(Exception e){runOnUiThread(()->info("Ошибка",e.toString()));}}).start();}

    private void exportPlan(){
        if(plan==null){info("Нет плана","Сначала выберите и проанализируйте пакет ОС.");return;}
        new Thread(()->{
            try{
                JSONObject o=InstallPlanner.toJson(plan);o.put("created_by","uPOSPa v0.8 beta");if(firmware!=null){o.put("package_name",firmware.name);o.put("sha256",firmware.sha256);}
                File base=getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);if(base==null)base=getFilesDir();File dir=new File(base,"uPOSPa/plans");dir.mkdirs();File f=new File(dir,"uPOSPa_InstallPlan_"+System.currentTimeMillis()+".json");
                try(FileOutputStream out=new FileOutputStream(f)){out.write(o.toString(2).getBytes("UTF-8"));}
                runOnUiThread(()->info("Install Plan сохранён",f.getAbsolutePath()));
            }catch(Exception e){runOnUiThread(()->info("Ошибка",e.toString()));}
        }).start();
    }

    private String join(java.util.List<String> xs){StringBuilder s=new StringBuilder();for(String x:xs){if(s.length()>0)s.append(", ");s.append(x);}return s.toString();}
    private void info(String t,String m){new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Понятно",null).show();}
}
