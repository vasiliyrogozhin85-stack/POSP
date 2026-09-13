# POSP Suite

Monorepo для двух Android-приложений:

- `host-app/` — POSP Host
- `client-app/` — POSP Client
- `common/` — общий протокол, модели данных и утилиты
- `docs/` — документация
- `firmware/manifests/` — манифесты пакетов POSP OS
- `tools/` — вспомогательные скрипты
- `.github/workflows/` — GitHub Actions

## План сборки

Host:
`./gradlew :host-app:assembleDebug`

Client:
`./gradlew :client-app:assembleDebug`
