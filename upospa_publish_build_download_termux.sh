#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

# uPOSPa 0.8 beta + Wizard
# One-shot Termux publisher:
#   source ZIP -> GitHub branch -> GitHub Actions -> APK -> phone Downloads
#
# Default repository can be overridden by the first argument:
#   bash upospa_publish_build_download_termux.sh owner/repo
#   bash upospa_publish_build_download_termux.sh https://github.com/owner/repo.git

DEFAULT_REPO="vasiliyrogozhin85-stack/POSP"
REPO_INPUT="${1:-${GITHUB_REPO:-$DEFAULT_REPO}}"
BRANCH="${UPOSPA_BRANCH:-upospa-unified-v0.8-beta-wizard}"
SOURCE_ZIP_NAME="uPOSPa_v0.8_beta_wizard_sources.zip"
ARTIFACT_NAME="uPOSPa-v0.8-beta-wizard-debug"
APK_NAME="uPOSPa_v0.8_beta_wizard_debug.apk"
WORKFLOW_FILE="build-upospa.yml"

say() { printf '\n\033[1;36m[uPOSPa]\033[0m %s\n' "$*"; }
warn() { printf '\n\033[1;33m[uPOSPa]\033[0m %s\n' "$*"; }
die() { printf '\n\033[1;31m[uPOSPa ERROR]\033[0m %s\n' "$*" >&2; exit 1; }

cleanup() {
  if [ -n "${TMPROOT:-}" ] && [ -d "${TMPROOT:-}" ]; then
    rm -rf "$TMPROOT" || true
  fi
}
trap cleanup EXIT

normalize_repo() {
  local r="$1"
  r="${r#https://github.com/}"
  r="${r#http://github.com/}"
  r="${r#git@github.com:}"
  r="${r%.git}"
  r="${r%/}"
  printf '%s' "$r"
}

REPO_SLUG="$(normalize_repo "$REPO_INPUT")"
[[ "$REPO_SLUG" == */* ]] || die "Неверный репозиторий: $REPO_INPUT. Нужен owner/repo или URL GitHub."

say "Подготовка Termux..."
pkg update -y
pkg install -y git gh jq unzip coreutils findutils

# Shared storage is required only to find the ZIP and save the final APK.
if [ ! -d "$HOME/storage/downloads" ]; then
  warn "Termux ещё не получил доступ к памяти телефона. Сейчас Android покажет запрос."
  termux-setup-storage || true
  printf 'Разрешите Termux доступ к файлам, затем нажмите Enter... '
  read -r _
fi

DOWNLOADS=""
for d in "$HOME/storage/downloads" "/storage/emulated/0/Download" "/sdcard/Download"; do
  if [ -d "$d" ] && [ -w "$d" ]; then DOWNLOADS="$d"; break; fi
done
[ -n "$DOWNLOADS" ] || die "Не удалось получить доступ к папке Download. Выполните termux-setup-storage и разрешите доступ."

SCRIPT_DIR="$(cd "$(dirname "$0")" 2>/dev/null && pwd || pwd)"
SOURCE_ZIP=""
for f in \
  "$SCRIPT_DIR/$SOURCE_ZIP_NAME" \
  "$DOWNLOADS/$SOURCE_ZIP_NAME" \
  "$PWD/$SOURCE_ZIP_NAME"; do
  if [ -f "$f" ]; then SOURCE_ZIP="$f"; break; fi
done
[ -n "$SOURCE_ZIP" ] || die "Не найден $SOURCE_ZIP_NAME. Скачайте архив исходников из чата в Download и повторите запуск."

say "Исходники: $SOURCE_ZIP"
say "Репозиторий: $REPO_SLUG"
say "Ветка: $BRANCH"

if ! gh auth status -h github.com >/dev/null 2>&1; then
  warn "Нужна авторизация GitHub. Откроется стандартная авторизация gh."
  gh auth login --hostname github.com --git-protocol https --web
fi

gh auth setup-git >/dev/null 2>&1 || true
gh repo view "$REPO_SLUG" >/dev/null 2>&1 || die "Нет доступа к $REPO_SLUG или репозиторий не существует."

TMPROOT="$(mktemp -d "$HOME/.upospa-build.XXXXXX")"
SRCROOT="$TMPROOT/src"
REPODIR="$TMPROOT/repo"
ARTDIR="$TMPROOT/artifact"
mkdir -p "$SRCROOT" "$ARTDIR"

say "Проверка и распаковка архива..."
unzip -t "$SOURCE_ZIP" >/dev/null || die "ZIP исходников повреждён."
unzip -q "$SOURCE_ZIP" -d "$SRCROOT"
PROJECT_DIR="$SRCROOT/uPOSPa_v0.8_beta_wizard"
[ -f "$PROJECT_DIR/settings.gradle" ] || die "В архиве не найдена структура проекта uPOSPa."
[ -f "$PROJECT_DIR/.github/workflows/$WORKFLOW_FILE" ] || die "В исходниках отсутствует GitHub Actions workflow."

say "Клонирование GitHub..."
gh repo clone "$REPO_SLUG" "$REPODIR"
cd "$REPODIR"

git fetch origin --prune
if git show-ref --verify --quiet "refs/remotes/origin/$BRANCH"; then
  git checkout -B "$BRANCH" "origin/$BRANCH"
else
  # Start the uPOSPa branch from the repository default HEAD, then replace its working tree.
  git checkout -b "$BRANCH"
fi

say "Копирование uPOSPa 0.8 beta + Wizard в ветку..."
find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -a "$PROJECT_DIR"/. "$REPODIR"/

# The helper script is useful in the repository too.
cp -f "$0" "$REPODIR/upospa_publish_build_download_termux.sh" 2>/dev/null || true
chmod +x "$REPODIR/upospa_publish_build_download_termux.sh" 2>/dev/null || true

git add -A
git config user.name "${GIT_AUTHOR_NAME:-uPOSPa Builder}"
git config user.email "${GIT_AUTHOR_EMAIL:-upospa@local}"

if git diff --cached --quiet; then
  warn "Изменений относительно ветки нет. Для сборки будет использован текущий HEAD."
else
  git commit -m "uPOSPa 0.8 beta Wizard: unified Host Client diagnostics"
fi

git push -u origin "$BRANCH"
HEAD_SHA="$(git rev-parse HEAD)"
say "GitHub commit: $HEAD_SHA"

say "Ожидание запуска GitHub Actions..."
RUN_ID=""
for _ in $(seq 1 72); do
  RUN_ID="$(gh run list \
    --repo "$REPO_SLUG" \
    --branch "$BRANCH" \
    --event push \
    --limit 30 \
    --json databaseId,headSha,workflowName,createdAt \
    | jq -r --arg sha "$HEAD_SHA" '.[] | select(.headSha == $sha and (.workflowName | contains("uPOSPa"))) | .databaseId' \
    | head -n 1)"
  if [ -n "$RUN_ID" ] && [ "$RUN_ID" != "null" ]; then break; fi
  sleep 5
done

if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "null" ]; then
  die "GitHub Actions не запустился в течение 6 минут. Проверьте вкладку Actions репозитория и разрешения Actions."
fi

say "Workflow run ID: $RUN_ID"
say "Сборка APK на GitHub Actions. Терминал можно оставить открытым..."
if ! gh run watch "$RUN_ID" --repo "$REPO_SLUG" --exit-status; then
  warn "Сборка завершилась с ошибкой. Последние ошибочные логи:"
  gh run view "$RUN_ID" --repo "$REPO_SLUG" --log-failed || true
  exit 2
fi

say "Скачивание артефакта $ARTIFACT_NAME..."
rm -rf "$ARTDIR"
mkdir -p "$ARTDIR"
gh run download "$RUN_ID" \
  --repo "$REPO_SLUG" \
  --name "$ARTIFACT_NAME" \
  --dir "$ARTDIR"

APK_SRC="$(find "$ARTDIR" -type f -name '*.apk' | head -n 1)"
[ -n "$APK_SRC" ] && [ -f "$APK_SRC" ] || die "Workflow успешен, но APK в артефакте не найден."

DEST_DIR="$DOWNLOADS/uPOSPa"
mkdir -p "$DEST_DIR"
DEST_APK="$DEST_DIR/$APK_NAME"
cp -f "$APK_SRC" "$DEST_APK"

APK_SHA256="$(sha256sum "$DEST_APK" | awk '{print $1}')"
APK_SIZE="$(du -h "$DEST_APK" | awk '{print $1}')"

say "ГОТОВО"
printf 'APK: %s\n' "$DEST_APK"
printf 'Размер: %s\n' "$APK_SIZE"
printf 'SHA-256: %s\n' "$APK_SHA256"
printf 'GitHub: https://github.com/%s/actions/runs/%s\n' "$REPO_SLUG" "$RUN_ID"

printf '\nОткрыть APK для установки сейчас? [y/N]: '
read -r INSTALL_NOW || true
case "${INSTALL_NOW:-}" in
  y|Y|yes|YES|д|Д|да|ДА)
    say "Открываю системный установщик Android..."
    if command -v termux-open >/dev/null 2>&1; then
      termux-open "$DEST_APK" || true
    else
      warn "termux-open не найден. APK уже сохранён: $DEST_APK"
    fi
    ;;
  *)
    say "APK оставлен в памяти телефона. Установить его можно позже из папки Download/uPOSPa."
    ;;
esac
