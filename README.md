# Phone OS Profiler v0.5

Версия 0.5 подготовлена специально после проблемы установки v0.4 на DOOGEE S97 Pro.

## Главное исправление установки

В v0.2-v0.4 использовался стандартный debug-подписант GitHub Actions. На новом runner debug-ключ может отличаться, поэтому Android может отклонить обновление уже установленного APK с тем же applicationId.

Начиная с v0.5:

- applicationId: `com.vasiliyrogozhin85.posp`
- стабильный тестовый ключ: `app/signing/posp-dev.jks`
- debug и release development-сборки подписываются одним ключом
- следующие версии POSP должны использовать тот же applicationId и этот же тестовый ключ, чтобы обновляться поверх v0.5

**Важно:** этот ключ находится в исходниках и предназначен только для разработки/тестирования. Для публичного релиза нужен закрытый release-ключ, хранящийся вне репозитория.

## Возможности

- JSON-рапорт аппаратной/системной конфигурации
- запрос runtime-разрешения READ_PHONE_STATE
- Bluetooth-информация
- отдельная кнопка отправки рапорта по Bluetooth
- обычное Android Share
- CPU, RAM, storage, display, battery, sensors, cameras, audio, network, telephony
- boot / Treble / kernel / graphics / thermal / partitions / firmware
- расширенный ADB/Termux collector в `tools/`

## Совместимость

- minSdk 26
- Android 8.0+
- проверяемая цель: DOOGEE S97 Pro / Android 11
