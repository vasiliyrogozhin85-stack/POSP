# Архитектура POSP Host / Client

## Android mode

Host (USB Host) → Android Open Accessory → Client

Назначение:
- отчёты;
- статус Client;
- обмен файлами;
- безопасные команды уровня приложения.

## ADB mode

Host → ADB USB transport → adbd на S97 Pro

Назначение:
- `reboot bootloader`;
- `reboot recovery`;
- диагностические shell-команды;
- расширенный сбор данных.

## Fastboot mode

Host → Fastboot USB transport → bootloader / fastbootd

Назначение:
- getvar;
- анализ A/B;
- проверка unlocked/secure;
- установка совместимых образов после проверок.

## Безопасность

Операции erase/flash/unlock не должны выполняться автоматически.
Перед ними Host обязан проверить модель, board, SoC, SHA-256, заряд и резервную копию.
