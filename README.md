# POSP Host v0.9 + Client v0.2

Основная цель версии — сделать работу пары понятной и диагностируемой.

## Короткий порядок
1. На DOOGEE включить USB debugging.
2. Запустить POSP Client и оставить открытым.
3. Подключить DOOGEE к телефону Host через OTG.
4. На Host: `1. Найти DOOGEE`.
5. На Host: `2. Подключить ADB`.
6. На DOOGEE принять RSA fingerprint.
7. На Host: `3. Проверить ADB`.
8. Только после этого использовать reboot / bootloader / recovery / fastbootd.
9. Для Client Link: `4. Запустить Client USB (AOA)` → подождать → `1. Найти DOOGEE` → `5. Открыть Client Link` → `6. PING` → `7. Запросить отчёт`.

## Диагностика
Оба приложения создают сессионный TXT при каждом запуске, пишут туда lifecycle, USB/ADB/AOA события, ошибки и stack traces.
При `onStop` создаётся копия в `Download/POSPReports`.
Также есть ручная кнопка экспорта.
