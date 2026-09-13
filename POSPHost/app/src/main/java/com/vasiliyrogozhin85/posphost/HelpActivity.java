package com.vasiliyrogozhin85.posphost;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public class HelpActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(18),dp(18),dp(28)); root.setBackgroundColor(Color.rgb(244,247,250)); scroll.addView(root);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); Button back=new Button(this); back.setText("← Назад"); back.setAllCaps(false); back.setOnClickListener(v->finish()); TextView title=new TextView(this); title.setText("Справка POSP Host v0.7"); title.setTextSize(22); title.setTextColor(Color.rgb(20,36,52)); title.setPadding(dp(10),0,0,0); top.addView(back); top.addView(title,new LinearLayout.LayoutParams(0,-2,1)); root.addView(top);
        add(root,"Назначение","POSP Host устанавливается на второй Android-телефон с USB OTG и работает как ADB/Fastboot-хост для DOOGEE S97 Pro. v0.7 сосредоточена на подробном безопасном опросе загрузчика перед последующей разблокировкой.");
        add(root,"Подключить устройство","Ищет USB-интерфейс ADB или Fastboot. В Android-режиме на S97 Pro должна быть включена USB-отладка и подтверждён RSA-ключ.");
        add(root,"Проверить готовность","Проверяет модель, MT6785, board k85v1_64, A/B, dynamic partitions, AVB и доступность состояния загрузчика. Процент показывает полноту проверки, а не разрешение на прошивку.");
        add(root,"Подробный опрос для разблокировки","Главная новая функция v0.7. В ADB-режиме собирает признаки OEM unlock, AVB/vbmeta, flash_locked, слоты, dynamic partitions, bootctl, USB-debug и MediaTek boot properties. В Fastboot-режиме опрашивает расширенный набор getvar, слоты, размеры/типы разделов и fastbootd. Никаких unlock, erase, flash, OEM или BROM-команд этот опрос не отправляет.");
        add(root,"Как пользоваться опросом","1) В Android/ADB выполните опрос и сохраните ADB unlock-report. 2) Перейдите в Bootloader. 3) Снова подключите устройство. 4) Выполните тот же опрос в Fastboot. 5) Создайте диагностический пакет и передайте оба отчёта для анализа перед разблокировкой.");
        add(root,"Bootloader Analyzer","Читает расширенный getvar: product, unlocked, secure, critical-unlocked, fastbootd/userspace, A/B slot state, battery signals, has-slot, partition type/size и logical partition признаки. Также сохраняет raw getvar all и разобранную карту переменных.");
        add(root,"Собрать полный ADB-рапорт","Сохраняет ZIP с getprop, VINTF/HAL, lshal, разделами, dumpsys, камерой, сенсорами, аудио, thermal, сетью, GPU, fstab, init и другими доступными данными.");
        add(root,"Создать план резервной копии","Создаёт JSON с картой разделов и критическими областями. Это план, а не обещание raw-backup: чтение некоторых разделов может быть запрещено без дополнительных прав.");
        add(root,"Перезагрузить в Bootloader","ADB reboot bootloader. После смены USB-режима нужно снова нажать «Подключить устройство».");
        add(root,"Перейти в Fastbootd","Использует adb reboot fastboot или fastboot reboot fastboot. Fastbootd нужен для dynamic/logical partitions.");
        add(root,"Перейти в Recovery","Перезагружает устройство в recovery без прошивки.");
        add(root,"Проверить Fastboot / Bootloader","Быстрая read-only проверка переменных загрузчика. Для полного исследования используйте «Подробный опрос для разблокировки».");
        add(root,"Разблокировать загрузчик","Перед unlock v0.7 рекомендует сначала сохранить Fastboot unlock-report. После двойного подтверждения приложение может отправить только стандартную fastboot flashing unlock. Операция обычно стирает пользовательские данные. BROM/FRP bypass и перебор OEM-команд автоматически не выполняются.");
        add(root,"Rescue: переключить A/B слот","Меняет active slot через стандартный set_active только после подтверждения. Использовать, только если второй слот содержит рабочую систему.");
        add(root,"Проверить пакет POSP OS","ZIP должен содержать posp_manifest.json. Проверяются product/hardware/board и SHA-256. Автоматическая установка ОС пока отключена.");
        add(root,"Создать диагностический пакет","Объединяет ADB-рапорт, оба unlock-research отчёта, Bootloader Analyzer, Backup Plan и журнал операций в один ZIP.");
        add(root,"Что улучшено дополнительно","v0.7 сохраняет raw и parsed getvar all, собирает состояние каждого A/B-слота, battery-soc-ok/voltage, fastbootd/userspace признаки и предупреждает перед unlock, если предварительный Fastboot-опрос ещё не сохранён.");
        add(root,"Ограничения безопасности","Приложение не стирает и не прошивает preloader, NVRAM, NVDATA, NVCFG, protect, proinfo или FRP. Подробный опрос предназначен именно для выбора поддерживаемого штатного пути разблокировки, а не для обхода защиты.");
        setContentView(scroll);
    }
    private void add(LinearLayout root,String h,String p){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),Color.rgb(224,230,236));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,0);root.addView(c,lp);TextView t=new TextView(this);t.setText(h);t.setTextSize(17);t.setTextColor(Color.rgb(24,55,82));t.setPadding(0,0,0,dp(7));c.addView(t);TextView b=new TextView(this);b.setText(p);b.setTextSize(14);b.setTextColor(Color.rgb(62,76,89));b.setLineSpacing(0,1.12f);c.addView(b);}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
}
