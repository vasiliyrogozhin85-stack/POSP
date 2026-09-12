# Phone OS Profiler v0.2

Phone OS Profiler собирает аппаратный и системный профиль Android-телефона для последующей разработки кастомной ОС.

## Результат
После сканирования создаётся один файл `PhoneOSProfiler_Report_YYYYMMDD_HHMMSS.json`. В приложении есть кнопка **«Поделиться рапортом»**.

## Схема отчёта
`schema = phone_os_profiler_report`, `schema_version = 1`.
Разделы: `device`, `android`, `cpu`, `memory`, `storage`, `display`, `battery`, `sensors`, `cameras`, `network`, `audio`, `telephony`, `boot`, `treble`, `kernel`, `features`, `root`, `raw`, `errors`.

Недоступные обычному Android-приложению сведения не выдумываются: сохраняются `null`, `<unavailable: ...>` или ошибка в `errors`.

## Сборка
JDK 17+, Android SDK 36. Проект настроен на Android Gradle Plugin 9.4.0 / Gradle 9.6.0. GitHub Actions workflow включён.
