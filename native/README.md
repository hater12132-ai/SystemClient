# native/SystemClientCore

Place the **SystemClientCore** xmake project here (from `SystemClientCore-native-src.zip`).

Required layout:

```
native/SystemClientCore/
  xmake.lua          # target("SystemClientCore") → libSystemClientCore.so
  src/
  include/
  scripts/
  ...
```

CI runs:

```bash
cd native/SystemClientCore
xmake f -y -p android -a arm64-v8a -m release --ndk=$ANDROID_NDK_HOME
xmake -y
```

The built `libSystemClientCore.so` is copied to:

`app/src/main/assets/systemclient_core/libSystemClientCore.so`

before the APK is assembled. That is the first-party core — not a user-importable `.levipack`.
