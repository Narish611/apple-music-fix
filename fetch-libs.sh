#!/bin/bash
# 下载 ViewPager2 运行所需的最小 AndroidX 依赖集（固定版本，不做传递解析）。
# 只用于构建，不进入源码仓库（见 .gitignore 的 libs/）。
set -euo pipefail
cd "$(cd "$(dirname "$0")" && pwd)"

MIRROR="${AMFIX_MAVEN_MIRROR:-https://maven.aliyun.com/repository/google}"
LIBS=libs
mkdir -p "$LIBS/raw" "$LIBS/jars" "$LIBS/res"

ARTS="
androidx/viewpager2/viewpager2/1.1.0/viewpager2-1.1.0.aar
androidx/recyclerview/recyclerview/1.2.1/recyclerview-1.2.1.aar
androidx/core/core/1.3.2/core-1.3.2.aar
androidx/customview/customview/1.0.0/customview-1.0.0.aar
androidx/collection/collection/1.1.0/collection-1.1.0.jar
androidx/annotation/annotation/1.1.0/annotation-1.1.0.jar
androidx/fragment/fragment/1.1.0/fragment-1.1.0.aar
androidx/activity/activity/1.0.0/activity-1.0.0.aar
androidx/loader/loader/1.0.0/loader-1.0.0.aar
androidx/lifecycle/lifecycle-runtime/2.0.0/lifecycle-runtime-2.0.0.aar
androidx/lifecycle/lifecycle-common/2.0.0/lifecycle-common-2.0.0.jar
androidx/lifecycle/lifecycle-viewmodel/2.0.0/lifecycle-viewmodel-2.0.0.aar
androidx/lifecycle/lifecycle-livedata/2.0.0/lifecycle-livedata-2.0.0.aar
androidx/lifecycle/lifecycle-livedata-core/2.0.0/lifecycle-livedata-core-2.0.0.aar
androidx/arch/core/core-common/2.0.0/core-common-2.0.0.jar
androidx/arch/core/core-runtime/2.0.0/core-runtime-2.0.0.aar
androidx/savedstate/savedstate/1.0.0/savedstate-1.0.0.aar
androidx/versionedparcelable/versionedparcelable/1.1.0/versionedparcelable-1.1.0.aar
"

echo "== 下载依赖 =="
for a in $ARTS; do
  f="$LIBS/raw/$(basename "$a")"
  if [ -f "$f" ]; then
    continue
  fi
  if ! curl -fsSL -o "$f" "$MIRROR/$a"; then
    echo "下载失败：$MIRROR/$a" >&2
    rm -f "$f"
    exit 1
  fi
  echo "  $(basename "$a")"
done

echo "== 解包 =="
rm -rf "$LIBS/tmp"
mkdir -p "$LIBS/tmp"
for f in "$LIBS"/raw/*.aar; do
  [ -e "$f" ] || continue
  name=$(basename "$f" .aar)
  unzip -o -q "$f" classes.jar -d "$LIBS/tmp/$name" 2>/dev/null || true
  if [ -f "$LIBS/tmp/$name/classes.jar" ]; then
    cp "$LIBS/tmp/$name/classes.jar" "$LIBS/jars/$name.jar"
  fi
  # 注意：不要用 unzip -l | grep -q 判断（pipefail 下 SIGPIPE 会让判断恒假）。
  unzip -o -q "$f" 'res/*' -d "$LIBS/tmp/$name" 2>/dev/null || true
  if [ -d "$LIBS/tmp/$name/res" ]; then
    mkdir -p "$LIBS/res/$name"
    cp -R "$LIBS/tmp/$name/res/." "$LIBS/res/$name/"
  fi
done
for f in "$LIBS"/raw/*.jar; do
  [ -e "$f" ] || continue
  cp "$f" "$LIBS/jars/"
done
rm -rf "$LIBS/tmp"
touch "$LIBS/.ok"
echo "== 依赖就绪：$(ls "$LIBS/jars" | wc -l | tr -d ' ') 个 jar =="
