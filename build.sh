#!/bin/bash
set -euo pipefail
cd "$(cd "$(dirname "$0")" && pwd)"

JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export JAVA_HOME="$JBR"
export PATH="$JBR/bin:$PATH"
SDK="$HOME/Library/Android/sdk"
AJ="$SDK/platforms/android-37.0/android.jar"
BT="$SDK/build-tools/36.0.0"
KS="${AMFIX_KS:-$HOME/dsh-amfix/dsh.keystore}"
KS_PASS="${AMFIX_KS_PASS:-}"

if [ -z "$KS_PASS" ]; then
  echo "请先设置签名密码：export AMFIX_KS_PASS=你的密码" >&2
  exit 1
fi

if [ -f build/dsh.keystore ] && [ ! -f "$KS" ]; then
  cp build/dsh.keystore "$KS"
fi

echo "== env =="
"$JBR/bin/java" -version 2>&1 | head -2

echo "== xposed api =="
if [ ! -f xposed-api.jar ]; then
  curl -sSL -o xposed-api.jar "https://maven.aliyun.com/repository/jcenter/de/robv/android/xposed/api/82/api-82.jar"
fi

echo "== androidX deps =="
LIBS="$PWD/libs"
if [ ! -f "$LIBS/.ok" ]; then
  bash fetch-libs.sh
fi
LIB_JARS=$(ls "$LIBS"/jars/*.jar)
LIB_CP=$(echo "$LIB_JARS" | tr '\n' ':')

echo "== icon =="
ICON=res/mipmap-xxxhdpi/ic_launcher.png
if [ -f "$ICON" ]; then
  sips -s format png -z 192 192 "$ICON" --out /tmp/amfix_icon.png >/dev/null 2>&1 \
    && mv /tmp/amfix_icon.png "$ICON" || true
fi

echo "== aapt2 =="
rm -rf build
mkdir -p build/classes build/dex build/gen build/libres
"$BT/aapt2" compile --dir res -o build/res.zip
LIB_RES=""
for d in "$LIBS"/res/*; do
  [ -d "$d" ] || continue
  name=$(basename "$d")
  "$BT/aapt2" compile --dir "$d" -o "build/libres/$name.zip"
  LIB_RES="$LIB_RES build/libres/$name.zip"
done
# 库的 R 类不在 classes.jar 里（AAR 构建时由 AGP 生成），这里用 --extra-packages
# 让 aapt2 按最终资源表再生成一份，ID 才能和实际资源对上。
"$BT/aapt2" link -o build/base.apk --manifest AndroidManifest.xml -I "$AJ" \
  --auto-add-overlay --java build/gen \
  --extra-packages androidx.core:androidx.recyclerview:androidx.viewpager2 \
  --min-sdk-version 26 --target-sdk-version 36 \
  build/res.zip $LIB_RES

echo "== javac =="
"$JBR/bin/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
  -cp "$AJ:xposed-api.jar:$LIB_CP" -d build/classes \
  $(find src build/gen -name '*.java')
"$JBR/bin/jar" cf build/classes.jar -C build/classes .

echo "== d8 =="
"$BT/d8" --release --min-api 26 --lib "$AJ" --lib xposed-api.jar \
  --output build/dex build/classes.jar $LIB_JARS

echo "== package =="
cd build
zip -q -j base.apk dex/classes.dex
mkdir -p assets
cp -R ../assets/. assets/
zip -q -r base.apk assets

"$BT/zipalign" -f 4 base.apk aligned.apk
if [ ! -f "$KS" ]; then
  "$JBR/bin/keytool" -genkeypair -keystore "$KS" -alias dsh \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=dsh" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" --out amfix.apk aligned.apk

echo "== result =="
ls -lh amfix.apk
"$BT/aapt2" dump badging amfix.apk | grep -E "package:|launchable-activity|application:" | head -5
