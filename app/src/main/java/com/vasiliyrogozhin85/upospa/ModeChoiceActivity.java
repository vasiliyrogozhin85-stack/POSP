package com.vasiliyrogozhin85.upospa;

import android.app.*;import android.os.*;import android.content.*;import android.content.pm.PackageManager;import android.hardware.usb.*;import android.view.*;import android.widget.*;

public class ModeChoiceActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);UiKit.screen(this);setContentView(ui());}

    private View ui(){
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(UiKit.BG);
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(UiKit.dp(this,18),UiKit.dp(this,16),UiKit.dp(this,18),UiKit.dp(this,28));sv.addView(r);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(UiKit.tv(this,"▲",38,UiKit.HOST,true),new LinearLayout.LayoutParams(UiKit.dp(this,50),-2));
        head.addView(UiKit.header(this,"uPOSPa",UiKit.HOST),new LinearLayout.LayoutParams(0,-2,1));
        TextView gear=UiKit.tv(this,"⚙",26,UiKit.MUTED,false);gear.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));head.addView(gear);r.addView(head);
        r.addView(UiKit.tv(this,"v0.8 beta + Wizard • диагностика • управление • установка ОС",14,UiKit.MUTED,true),UiKit.top(this,4));

        LinearLayout hero=UiKit.card(this);hero.addView(UiKit.tv(this,"Единый мобильный сервисный центр",17,UiKit.TEXT,true));hero.addView(UiKit.tv(this,"Host + Client + Fastboot + отчёты + история + аппаратные тесты + безопасный Install Plan.",13,UiKit.MUTED,false),UiKit.top(this,6));r.addView(hero,UiKit.top(this,14));

        r.addView(UiKit.button(this,"▦  DASHBOARD — состояние устройства",UiKit.HOST,v->startActivity(new Intent(this,DashboardActivity.class))));
        r.addView(UiKit.button(this,"✦  WIZARD — автоматическая диагностика двух телефонов",UiKit.CLIENT,v->startActivity(new Intent(this,WizardActivity.class))));
        r.addView(UiKit.section(this,"Режим работы"));
        PackageManager pm=getPackageManager();boolean host=pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        Button hb=UiKit.button(this,"▣  HOST\n     подключить другое Android-устройство",UiKit.HOST,v->startActivity(new Intent(this,HostActivity.class)));if(!host){hb.setEnabled(false);hb.setAlpha(.45f);}r.addView(hb);
        r.addView(UiKit.button(this,"▯  CLIENT\n     диагностировать этот телефон",UiKit.CLIENT,v->startActivity(new Intent(this,ClientActivity.class))));
        r.addView(UiKit.button(this,"⚙  AUTO\n     определить роль по USB",UiKit.SURF2,v->autoMode()));

        r.addView(UiKit.section(this,"Инструменты"));
        r.addView(UiKit.darkButton(this,"▤  Единый отчёт и совместимость",v->startActivity(new Intent(this,ReportActivity.class))));
        r.addView(UiKit.button(this,"⚡  SERVICE / FASTBOOT / A-B / PARTITIONS",UiKit.WARN,v->startActivity(new Intent(this,ServiceActivity.class))));
        r.addView(UiKit.darkButton(this,"⚙  Аппаратные тесты",v->startActivity(new Intent(this,HardwareTestActivity.class))));
        r.addView(UiKit.darkButton(this,"◴  История устройств",v->startActivity(new Intent(this,HistoryActivity.class))));
        r.addView(UiKit.darkButton(this,"▦  Библиотека пакетов ОС",v->startActivity(new Intent(this,PackageLibraryActivity.class))));

        r.addView(UiKit.section(this,"Возможности этого телефона"));
        UsbManager um=(UsbManager)getSystemService(USB_SERVICE);boolean accessory=pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY);
        LinearLayout cap=UiKit.card(this);cap.addView(line("USB Host / OTG",host));cap.addView(line("USB Accessory",accessory),UiKit.top(this,6));cap.addView(line("ADB Host",host),UiKit.top(this,6));cap.addView(UiKit.tv(this,"Android "+Build.VERSION.RELEASE+" • "+Build.MANUFACTURER+" "+Build.MODEL,13,UiKit.MUTED,false),UiKit.top(this,6));cap.addView(UiKit.tv(this,"USB устройств сейчас: "+(um==null?0:um.getDeviceList().size()),13,UiKit.MUTED,false),UiKit.top(this,6));r.addView(cap);

        TextView foot=UiKit.tv(this,"uPOSPa 0.8 beta + Wizard • strict safety gate • один APK",11,UiKit.MUTED,false);foot.setGravity(Gravity.CENTER);r.addView(foot,UiKit.top(this,18));
        return sv;
    }

    private TextView line(String n,boolean ok){return UiKit.tv(this,(ok?"✓":"—")+"  "+n+": "+(ok?"да":"нет"),13,ok?UiKit.CLIENT:UiKit.MUTED,true);}
    private void autoMode(){UsbManager u=(UsbManager)getSystemService(USB_SERVICE);if(u!=null){UsbAccessory[] a=u.getAccessoryList();if(a!=null&&a.length>0){startActivity(new Intent(this,ClientActivity.class));return;}if(!u.getDeviceList().isEmpty()){startActivity(new Intent(this,HostActivity.class));return;}}startActivity(new Intent(this,DashboardActivity.class));}
}
