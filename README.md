# POSP Suite

Монорепозиторий для пары Android-приложений:

- **POSP Host v0.8** — устанавливается на второй Android-телефон и работает как USB Host.
- **POSP Client v0.1** — устанавливается непосредственно на DOOGEE S97 Pro.
- **common** — общий код протокола и определения USB-режимов.

## Что уже реализовано

### Host v0.8
- приятный тёмный интерфейс;
- встроенная справка;
- собственная синяя adaptive icon;
- USB Host discovery;
- определение интерфейсов ADB / Fastboot / Android Open Accessory;
- запуск Android Open Accessory (AOA) для POSP Client Link;
- каркас разделов: диагностика, перезагрузка, загрузчик, установка ОС, файлы.

### Client v0.1
- зелёный интерфейс и отдельная adaptive icon;
- встроенная справка;
- подключение к Host через Android Open Accessory;
- локальный JSON-отчёт об устройстве;
- передача отчёта Host по USB Client Link;
- обработка PING и команды COLLECT_REPORT.

## Важное ограничение первой пары

Client Link (AOA) уже заложен как реальный USB-канал приложения ↔ приложения.  
Полный ADB/Fastboot транспорт внутри Host — следующий этап. Поэтому кнопки
перезагрузки/bootloader/recovery/fastbootd в этой версии объясняют действие,
но не пытаются обходить Android permissions.

Обычный Client не может сам вызвать `reboot bootloader` без системной подписи/root.
Правильная архитектура — выполнять такие операции на Host через ADB/Fastboot.

## Сборка

```bash
gradle :host-app:assembleDebug
gradle :client-app:assembleDebug
```

GitHub Actions собирает оба APK и публикует два артефакта.

## Package names

- Host: `com.vasiliyrogozhin85.posp.host`
- Client: `com.vasiliyrogozhin85.posp.client`

## Требования

- JDK 17
- Android SDK 36
- Gradle 9.2+ / GitHub Actions setup-gradle
