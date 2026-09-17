# uPOSPa 0.8 beta + Wizard

uPOSPa — единый Android-инструмент для диагностики, связи Host ↔ Client, ADB/Fastboot обслуживания, анализа прошивок и безопасной подготовки установки ОС.

## Новый Wizard

В сборку добавлен пошаговый **Wizard двух телефонов**. Один и тот же APK ставится на Host и Client.

Порядок работы:
1. Соединить телефоны кабелем/OTG.
2. На обоих устройствах открыть `WIZARD`.
3. Выбрать тип работы: **Быстрая диагностика / Полная диагностика / Подготовка к установке ОС**.
4. Выбрать роль **HOST** или **CLIENT**.
5. Ознакомиться с перечнем собираемых данных и автоматических действий.
6. На Host нажать `НАЧАТЬ WIZARD`.

Host автоматически ищет Client, подключает ADB, запускает uPOSPa на Client, получает локальный DeviceSnapshot и собирает ADB-диагностику. В полной диагностике Client автоматически переводится в Bootloader/Fastboot для чтения `getvar`, A/B и карты разделов. В режиме подготовки ОС дополнительно выполняется попытка безопасного перехода в Fastbootd для чтения dynamic partitions. Затем Host возвращает Client в обычный Android, снова открывает uPOSPa и показывает результат.

После завершения на Host активируется кнопка **«СОХРАНИТЬ ОТЧЁТ»**. Итоговый `uPOSPa_Wizard_*.json` объединяет данные Android/Client/ADB/Fastboot/Fastbootd и журнал этапов.

### Что Wizard собирает

- manufacturer / model / product / device / build fingerprint;
- Android, kernel, SELinux, CPU, RAM, storage, battery, display;
- системные свойства Treble, AVB, A/B, Virtual A/B, Dynamic Partitions;
- локальный `DeviceSnapshot` uPOSPa Client;
- `/proc`/mount/partition/USB diagnostics через ADB;
- Fastboot `getvar`, slot state и partition map в полной диагностике;
- Fastbootd/userspace и dynamic partition readiness в режиме подготовки ОС.

Wizard **не собирает** фотографии, сообщения, контакты, документы и содержимое пользовательских файлов.

### Ограничения Android

Первое ADB-соединение требует ручного подтверждения RSA на Client — Android не позволяет приложению безопасно обойти этот запрос. Системное уведомление о завершении на Android 13+ требует разрешения Notifications; независимо от него uPOSPa показывает результат прямо на экране Client. Fastboot/Fastbootd поддерживаются не всеми производителями одинаково, поэтому Wizard записывает неподдерживаемый этап как предупреждение и продолжает безопасный сценарий, где это возможно.

## Остальной функционал 0.8 beta

- Один APK: **HOST / CLIENT / AUTO / WIZARD / DASHBOARD / SERVICE**.
- Сохранён POSP Client Link и AOA-совместимость.
- USB ADB Host с RSA-авторизацией, reboot Android/Bootloader/Recovery/Fastbootd.
- Fastboot/Fastbootd: getvar, карта разделов, A/B Slot Manager, direct IMG с явным подтверждением.
- Единый отчёт: устройство, Android, kernel, SELinux, батарея, RAM/storage, датчики, Treble, AVB, A/B, Virtual A/B, Dynamic Partitions.
- OEM Profile Engine: generic, Pixel, Samsung, Xiaomi/Redmi/POCO, Motorola, OnePlus/OPPO/Realme, DOOGEE.
- Hardware Quick Test, история устройств, журнал операций, библиотека пакетов ОС.
- Анализ ZIP/IMG/OTA/payload: SHA-256, OTA metadata, `pre-device`, критические образы и `upospa-manifest.json`.
- Pre-flight + Safety Gate + Install Plan.

## Модель безопасности Wizard

Wizard автоматизирует **только диагностику и чтение параметров**. Он не разблокирует загрузчик, не стирает userdata и не прошивает разделы. При остановке или ошибке движок выполняет best-effort возврат Client из Fastboot/Fastbootd в Android и пытается снова открыть uPOSPa.

## Сборка

Требования: JDK 17, Android SDK 36, Android Gradle Plugin 9.0.0, Gradle 9.1+.

```bash
gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions собирает артефакт `uPOSPa_v0.8_beta_wizard_debug.apk`.
