#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
REPO="${1:-${GITHUB_REPO:-}}"
BRANCH="${UPOSPA_BRANCH:-upospa-unified-v0.8-beta-wizard}"
if [ -z "$REPO" ]; then echo "Использование: bash upload_upospa_termux.sh https://github.com/USER/REPO.git"; exit 1; fi
pkg update -y
pkg install -y git unzip
SRC="$(cd "$(dirname "$0")" && pwd)"
WORK="$HOME/uPOSPa-v0.8-beta-wizard-upload"
rm -rf "$WORK"
git clone "$REPO" "$WORK"
cd "$WORK"
if git show-ref --verify --quiet "refs/remotes/origin/$BRANCH"; then git checkout -B "$BRANCH" "origin/$BRANCH"; else git checkout -b "$BRANCH"; fi
find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -a "$SRC"/. "$WORK"/
rm -f "$WORK/upload_upospa_termux.sh"
git add -A
git config user.name "${GIT_AUTHOR_NAME:-uPOSPa Builder}"
git config user.email "${GIT_AUTHOR_EMAIL:-upospa@local}"
git commit -m "uPOSPa 0.8 beta Wizard: automatic Host/Client diagnostics" || true
git push -u origin "$BRANCH"
echo "Готово: $BRANCH. GitHub Actions соберёт uPOSPa_v0.8_beta_wizard_debug.apk."
