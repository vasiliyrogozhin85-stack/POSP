#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
REPO="vasiliyrogozhin85-stack/POSP"
ZIP="/sdcard/Download/POSP_Host_v0.9_Client_v0.2_Source.zip"
WORK="$HOME/posp_v09_v02"
pkg update -y
pkg install -y git gh unzip rsync
if ! gh auth status >/dev/null 2>&1; then
  echo "Введите GitHub PAT (HTTPS):"
  read -rsp "PAT: " TOKEN; echo
  printf '%s' "$TOKEN" | gh auth login --hostname github.com --git-protocol https --with-token
fi
gh auth setup-git
rm -rf "$WORK"; mkdir -p "$WORK/src"
unzip -q "$ZIP" -d "$WORK/src"
git clone "https://github.com/$REPO.git" "$WORK/repo"
cd "$WORK/repo"; git checkout main; git pull --ff-only
BACKUP="backup-before-v09-v02-$(date +%Y%m%d-%H%M%S)"; git branch "$BACKUP"
rsync -av --delete --exclude=".git" "$WORK/src/" "$WORK/repo/"
git config user.name "Boreac"; git config user.email "posp@users.noreply.github.com"
git add -A; git commit -m "POSP Host v0.9 and Client v0.2 diagnostics and guided workflow"
git push origin "$BACKUP"; git push origin main
SHA="$(git rev-parse HEAD)"
echo "Ожидаю GitHub Actions для $SHA..."
RUN=""
for i in $(seq 1 24); do
  RUN="$(gh run list --repo "$REPO" --workflow build-both.yml --commit "$SHA" --limit 1 --json databaseId --jq '.[0].databaseId // empty' 2>/dev/null || true)"
  [ -n "$RUN" ] && break
  sleep 5
done
[ -n "$RUN" ] || { echo "Run не найден"; exit 2; }
gh run watch "$RUN" --repo "$REPO" --exit-status || { gh run view "$RUN" --repo "$REPO" --log-failed; exit 3; }
OUT="/sdcard/Download/POSP_v09_v02_APK_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUT"; gh run download "$RUN" --repo "$REPO" --dir "$OUT"
echo "Готово: $OUT"; find "$OUT" -name "*.apk" -print -exec sha256sum {} \;
