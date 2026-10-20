# SystemClient CI — first-party core + APK

## One-time setup in Termux / PC

```bash
cd /data/data/com.termux/files/home/SystemClient
git checkout main
git fetch origin
git reset --hard origin/main

# 1) Workflows
unzip -o /storage/emulated/0/Download/SystemClient-ci-core.zip

# 2) Native source (from SystemClientCore-native-src.zip)
mkdir -p native/SystemClientCore
unzip -o /storage/emulated/0/Download/SystemClientCore-native-src.zip -d native/SystemClientCore
# if nested folder:
if [ -d native/SystemClientCore/BedrockTools-main ]; then
  cp -a native/SystemClientCore/BedrockTools-main/. native/SystemClientCore/
  rm -rf native/SystemClientCore/BedrockTools-main
fi
# if zip extracted flat files already at native/SystemClientCore, skip

test -f native/SystemClientCore/xmake.lua && echo "native tree OK"

git add -A
git commit -m "ci: build SystemClientCore.so then APK (first-party, no levipack import)"
git push origin main
```

## Artifacts

| Name | Content |
|------|---------|
| `SystemClient-debug-apk` | Installable APK with core SO in assets |
| `SystemClientCore-so` | The arm64 `libSystemClientCore.so` alone |

## Workflows

- **Build APK** — NDK + xmake core → assets → `assembleDebug`
- **Build SystemClient Core (.so)** — SO only when `native/SystemClientCore/**` changes
