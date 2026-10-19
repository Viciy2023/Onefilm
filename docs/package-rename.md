# OneFilm 包名重命名方案

## 1. 结论

采用 **1:1 结构替换**：

```text
com.fongmi.android.tv  ->  com.onefilm.android.tv
com/fongmi/android/tv  ->  com/onefilm/android/tv
```

这比 `com.onetv.film` 更稳，因为目录层级、类路径和 Manifest 相对类名都保持不变。

## 2. 应用标识

| 项 | TV | 手机 |
|---|---|---|
| `applicationId` | `com.onefilm.android.tv` | `com.onefilm.android.tv.mobile` |
| `namespace` | `com.onefilm.android.tv` | `com.onefilm.android.tv` |
| Java/Kotlin 包 | `com.onefilm.android.tv` | `com.onefilm.android.tv` |
| App 名 | `OneFilm` | `OneFilm` |
| 仓库根目录 | `F:\APK\Onefilm` | 同左 |

手机包名通过 flavor 配置：

```groovy
productFlavors {
    leanback {
        dimension "device"
    }
    mobile {
        dimension "device"
        applicationIdSuffix ".mobile"
    }
}
```

如果不想手机用 `.mobile` 后缀，也可以两端同包名；但同一台设备不能同时安装两个 APK，因此推荐后缀。

## 3. 首版重命名范围

### 3.1 必须改

- `app/build.gradle`
  - `namespace`
  - `defaultConfig.applicationId`
  - `mobile.applicationIdSuffix`
  - `annotationProcessorOptions.eventBusIndex`
  - `versionName` / `versionCode`
- `settings.gradle`
  - `rootProject.name = "OneFilm"`
- `app/src/main/AndroidManifest.xml`
- `app/src/leanback/AndroidManifest.xml`
- `app/src/mobile/AndroidManifest.xml`
- `app/src/main/java/com/fongmi/android/tv/**`
- `app/src/leanback/java/com/fongmi/android/tv/**`
- `app/src/mobile/java/com/fongmi/android/tv/**`
- `app/src/main/res/layout/*.xml` 中的自定义 View FQCN
- `app/src/main/res/xml/*.xml` 中的类名/权限配置（如有）
- `app/proguard-rules.pro`
  - `com.fongmi.android.tv.bean.**`
- `app/src/main/res/values/strings.xml`
  - `app_name` -> `OneFilm`
- `README.md`
  - 应用名/构建说明；保留上游归属
- `LICENSE` / `NOTICE`
  - 保留 GPL-3.0 与 FongMi 归属

### 3.2 首版保留

以下包属于引擎/构建/内部库，不是应用包；首版不重命名，降低 JNI、反射和上游同步风险：

- `com.fongmi.quickjs`
- `com.fongmi.hook`
- `com.fongmi.chaquo`
- `com.fongmi.gradle`

对应 ProGuard 规则也保留：

```proguard
-keep class com.fongmi.quickjs.method.** { *; }
```

如果阶段二要求“源码中零 fongmi”，再统一改成 `com.onefilm.quickjs` / `com.onefilm.hook` / `com.onefilm.chaquo` / `com.onefilm.gradle`，并做完整回归。

## 4. 重命名步骤

### 4.1 先做基线

```powershell
git checkout -b rename/onefilm-package
.\gradlew.bat :app:assembleLeanbackDebug :app:assembleMobileDebug
```

确认基线可构建后再改包名。

### 4.2 移动目录

```powershell
# main
New-Item -ItemType Directory -Force 'app\src\main\java\com\onefilm\android' | Out-Null
git mv 'app\src\main\java\com\fongmi\android\tv' 'app\src\main\java\com\onefilm\android\tv'

# leanback
New-Item -ItemType Directory -Force 'app\src\leanback\java\com\onefilm\android' | Out-Null
git mv 'app\src\leanback\java\com\fongmi\android\tv' 'app\src\leanback\java\com\onefilm\android\tv'

# mobile
New-Item -ItemType Directory -Force 'app\src\mobile\java\com\onefilm\android' | Out-Null
git mv 'app\src\mobile\java\com\fongmi\android\tv' 'app\src\mobile\java\com\onefilm\android\tv'
```

删除空的 `com\fongmi` 目录前先确认没有其他包（如 `com.fongmi.hook`）：

```powershell
Get-ChildItem -Recurse 'app\src\*\java\com\fongmi' -Directory |
  Select-Object FullName
```

如果 `com.fongmi.hook` 等仍在 `app` 下，保留对应目录，不要整棵删除。

### 4.3 替换应用包名

只替换应用包名，不要全局替换 `com.fongmi`，否则会误伤引擎包、上游 URL 和版权信息。

```powershell
$files = git grep -l -e 'com\.fongmi\.android\.tv' -- `
  '*.java' '*.kt' '*.xml' '*.gradle' '*.pro' '*.properties' '*.py' '*.js' '*.json' `
  ':!docs/**' ':!README.md' ':!UPSTREAM.md'

foreach ($f in $files) {
  $path = Join-Path (Get-Location) $f
  $t = [System.IO.File]::ReadAllText($path)
  $n = $t.Replace('com.fongmi.android.tv', 'com.onefilm.android.tv')
  if ($n -ne $t) {
    [System.IO.File]::WriteAllText($path, $n, [System.Text.UTF8Encoding]::new($false))
  }
}
```

注意：

- 不要对 `README.md`、`docs/**`、`UPSTREAM.md` 做自动替换；上游归属要保留。
- 不要替换 `com.fongmi.quickjs`、`com.fongmi.hook`、`com.fongmi.chaquo`、`com.fongmi.gradle`。
- XML 里的 `com.fongmi.android.tv.ui.custom.*` 必须替换，否则 inflate 会 `ClassNotFoundException`。

### 4.4 更新 Gradle

`app/build.gradle`：

```groovy
android {
    namespace = 'com.onefilm.android.tv'

    defaultConfig {
        applicationId "com.onefilm.android.tv"
        // ...
        javaCompileOptions {
            annotationProcessorOptions {
                arguments = [
                    "room.schemaLocation": "$projectDir/schemas".toString(),
                    "eventBusIndex": "com.onefilm.android.tv.event.EventIndex"
                ]
            }
        }
    }

    productFlavors {
        leanback {
            dimension "device"
        }
        mobile {
            dimension "device"
            applicationIdSuffix ".mobile"
        }
    }
}
```

`settings.gradle`：

```groovy
rootProject.name = "OneFilm"
```

### 4.5 更新 Manifest / 资源

- `android:name=".App"` 保持不变；namespace 改变后会自动解析到 `com.onefilm.android.tv.App`。
- `android:authorities="${applicationId}.provider"` 不需要改。
- 自定义 View 的 XML FQCN 改为 `com.onefilm.android.tv.ui.custom.*`。
- `app_name` 改为 `OneFilm`；leanback banner/icon 替换为 OneFilm 素材。
- 检查 `res/xml/searchable.xml`、`automotive_app_desc.xml` 是否有类名/包名。

### 4.6 更新 ProGuard

```proguard
-keep class com.onefilm.android.tv.bean.** { *; }
```

保留：

```proguard
-keep class com.fongmi.quickjs.method.** { *; }
```

## 5. 审计

### 5.1 旧应用包零残留

```powershell
git grep -n "com\.fongmi\.android\.tv" -- `
  ':!docs/**' ':!README.md' ':!UPSTREAM.md'
```

期望：无输出。

### 5.2 目录零残留

```powershell
Get-ChildItem -Recurse 'app\src' -Directory |
  Where-Object { $_.FullName -match 'com\\fongmi\\android\\tv' }
```

期望：无输出。

### 5.3 引擎包保留

```powershell
git grep -n "com\.fongmi\.quickjs\|com\.fongmi\.hook\|com\.fongmi\.chaquo\|com\.fongmi\.gradle"
```

期望：只出现在引擎模块、ProGuard、构建逻辑中。

### 5.4 预编译 AAR / native

源码层已确认：

- 没有 `Java_com_fongmi` JNI 函数名。
- `app/src/main/python` 没有 `com.fongmi` 引用。
- `app/src/main/assets` 没有 `com.fongmi` 引用。
- `catvod` 没有 `com.fongmi` 引用。

仍需检查预编译产物：

```powershell
# 列出 AAR
Get-ChildItem app\libs -Filter *.aar

# 解压后检查 class/字符串（示例）
# 可用 7-Zip 解压，再对 classes.jar 执行：
# jar tf classes.jar
# javap -classpath classes.jar -p <class>
```

重点看：

- JNI 函数名是否包含 `Java_com_fongmi`。
- 反射是否硬编码 `com.fongmi.android.tv`。
- AAR 是否包含自己的 `Application`/`ContentProvider`。
- 再分发许可证。

### 5.5 构建与安装

```powershell
.\gradlew.bat clean :app:assembleLeanbackDebug :app:assembleMobileDebug
```

检查包名：

```powershell
# 使用 Android SDK 的 apkanalyzer
apkanalyzer manifest application-id app\build\outputs\apk\leanback\debug\app-leanback-debug.apk
apkanalyzer manifest application-id app\build\outputs\apk\mobile\debug\app-mobile-debug.apk
```

期望：

```text
com.onefilm.android.tv
com.onefilm.android.tv.mobile
```

安装：

```powershell
adb install -r app\build\outputs\apk\leanback\debug\app-leanback-debug.apk
adb install -r app\build\outputs\apk\mobile\debug\app-mobile-debug.apk
adb shell pm list packages | Select-String onefilm
```

### 5.6 功能 smoke

至少验证：

- 冷启动。
- 配置导入 `https://tvbox.alibabagroup.de5.net/config.json`。
- 首页、搜索、详情、播放。
- 直播、EPG。
- 历史/收藏。
- 设置页。
- 返回键/D-pad。
- JS/Python 爬虫。
- 分享/打开链接。
- 手机 PiP/扫码（如适用）。
- TV Leanback 桌面图标/横幅。

## 6. 回滚

- 重命名单独一个 commit；出问题直接 `git revert`。
- 不要和 Compose 改造混在同一个 commit。
- 保留 `rename/onefilm-package` 分支，便于对比。

## 7. 常见坑

| 坑 | 现象 | 处理 |
|---|---|---|
| XML 自定义 View 未改 | `ClassNotFoundException` / inflate 失败 | 全局检查 `res/layout` |
| ProGuard 未改 | release 包 Gson/Bean 被混淆 | 更新 `-keep` |
| EventBus index 未改 | 事件不触发/编译失败 | 更新 `eventBusIndex` |
| R/BuildConfig import 未改 | 编译失败 | IDE 全局替换 import |
| Manifest 相对类名 | 启动崩溃 | 确认 `.App` 与新 namespace 对应 |
| 引擎包被误改 | JNI/反射失败 | 只替换 `com.fongmi.android.tv` |
| 上游 URL 被误改 | 文档/更新检查失效 | 排除 docs/README/UPSTREAM |
| 预编译 AAR 硬编码旧包名 | 运行时崩溃 | P0 扫描 AAR；必要时保留旧类桥接 |
