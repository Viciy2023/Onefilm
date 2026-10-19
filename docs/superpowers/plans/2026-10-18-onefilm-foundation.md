# OneFilm Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立可构建、可安装、可回归的 OneFilm 基础工程：新仓库导入 FongMi/TV pinned commit，完成应用身份与包名重命名，启用 Kotlin/Compose，搭好 `:core`、`:designsystem`、`:ui` 骨架，同时保留旧 UI 作为 fallback。

**Architecture:** 新仓库 + `upstream` remote；应用包 `com.onefilm.android.tv` 做 1:1 重命名；`:app` 作为宿主，`:core` 提供数据与引擎 facade，`:designsystem` 提供 Compose 主题/组件，`:ui` 提供带 `leanback`/`mobile` flavor 的 Compose 屏幕；旧 Activity 保留，按屏幕逐步切换。

**Tech Stack:** FongMi/TV 5.6.3、AGP 9.3.1、Java 21、Kotlin 2.3.20（AGP 内置）、Compose BOM 2026.09.00、TV Material 1.1.0、TV Foundation 1.0.0、Activity Compose 1.13.0、Room、EventBus、Media3/IJK/MPV（保留）。

**Spec:** `docs/architecture.md`、`docs/package-rename.md`、`docs/feature-matrix.md`、`docs/upstream.md`

## Global Constraints

- 许可证：GPL-3.0；保留 `LICENSE`、版权头、上游归属；新增代码同样 GPL-3.0。
- 上游 pin：`FongMi/TV` `fongmi` commit `0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e`，版本 5.6.3。
- TV `applicationId = com.onefilm.android.tv`；手机 `applicationId = com.onefilm.android.tv.mobile`。
- `namespace` 与 Java/Kotlin 包：`com.onefilm.android.tv`。
- minSdk 28；compileSdk/targetSdk 37；Java 21；Kotlin 2.3.20（AGP 9 内置）。
- 首版两个 APK；不做单 APK 自适应。
- `:catvod`、`:chaquo`、`:quickjs` 行为不变；不重写播放器内核。
- 旧 UI 保留；新 UI 未通过回归前不切换默认入口；不删除功能。
- 不复制 Apple TV / Disney+ / Netflix 商标、图标、截图或受版权保护素材。
- 所有命令默认在 `F:\APK\Onefilm` 下用 PowerShell 执行。

## Verified Environment Baseline (2026-10-18)

探针工程 `C:\Users\Amy\AppData\Local\Temp\sdk37-probe`（不进入仓库）已实测通过：

| 项 | 实测值 | 结论 |
|---|---|---|
| JDK | JBR 21.0.11（`C:\Users\Amy\.jdks\jbr-21.0.11`） | 可用；PATH 里的 JDK 17 不要用于构建 |
| Gradle | 9.7.1 | 可用；本机缓存的 9.3.1 分发损坏，不要用 |
| AGP | 9.3.1 | 可用 |
| SDK Platform | `platforms;android-37.0` | compileSdk/targetSdk 37 必须装这个小版本包 |
| Build-Tools | AGP 自动使用 `36.0.0` | 不需要 `build-tools;37.0.0` |
| Kotlin | 内置 2.3.20 | **禁止**再应用 `org.jetbrains.kotlin.android` |
| Compose | BOM 2026.09.00 + compose 插件 2.3.20 | 编译通过 |
| TV 库 | tv-material 1.1.0、tv-foundation 1.0.0 | 编译通过 |
| 构建结果 | `BUILD SUCCESSFUL in 3m23s` | 产出 30.7MB debug APK |

硬性结论：

- AGP 9 内置 Kotlin。应用 `org.jetbrains.kotlin.android` 会直接报错：
  `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.`
- jvmTarget 用 `kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21 } }`（放在 `android {}` 外），不要再写 `kotlinOptions`。
- 不需要 `android.builtInKotlin=false`。
- `sdkmanager` 必须带 `--sdk_root=D:\android_sdk`（本机 cmdline-tools 不在标准路径）。

## Review Focus

- **包名残留**：Java 包改了但 XML 自定义 View、ProGuard、EventBus index、`BuildConfig`/`R` import 没改，构建可能通过、启动或 release 崩溃。验证：`git grep` 零残留 + 安装启动 + 打开设置/播放页。
- **AGP 9 built-in Kotlin 与 Compose 插件冲突**：已实测（2026-10-18）：AGP 9.3.1 内置 Kotlin 2.3.20，加 Compose 插件正常；**不能**再应用 `kotlin-android`。验证：clean build 两个 flavor；目标配置见 Task 5。
- **Chaquopy/QuickJS/R8 回归**：Kotlin/Compose/R8 改动可能影响 Python/JS 爬虫、配置加载、播放器。验证：导入 ONETV 配置，跑 Java/JS/Python 站点，播放。
- **flavor variant 不匹配**：`:app` 与 `:ui` 都有 `leanback`/`mobile` 时，依赖解析可能失败或拿到错误变体。验证：两个 flavor 都 assemble，检查 `:app:dependencies` 中 `:ui` 变体。
- **签名/local.properties**：FongMi 的 release signing 读取 `local.properties`，新仓库/CI 没有 keystore 会阻塞构建。验证：无签名配置时 debug 构建成功，release 给出明确错误。

---

### Task 1: 建立新仓库并导入 pinned 上游

**Files:**
- Create: `F:\APK\Onefilm\.gitignore`
- Create: `F:\APK\Onefilm\UPSTREAM.md`
- Import: FongMi/TV 全仓库（排除 `.git`、`build`、`.gradle`、`local.properties`）

**Interfaces:**
- Consumes: 上游 commit `0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e`
- Produces: 可编译的 `:app`、`:catvod`、`:chaquo`、`:quickjs` 基线；`UPSTREAM.md` 记录来源

- [ ] **Step 1: 确认目标目录只含 docs**

```powershell
Test-Path 'F:\APK\Onefilm'
Get-ChildItem -Force 'F:\APK\Onefilm' | Select-Object Mode, Name
```

Expected: `True`，且**只**有 `docs` 目录。若出现其他文件/目录，先备份并确认，不要覆盖用户文件；`docs` 保留，不删除、不清空。

- [ ] **Step 2: 克隆上游到临时目录并 checkout pinned commit**

```powershell
$tmp = Join-Path $env:TEMP 'fongmi-onefilm-import'
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
git clone --filter=blob:none --no-checkout https://github.com/FongMi/TV.git $tmp
git -C $tmp checkout 0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e
git -C $tmp rev-parse HEAD
```

Expected: 输出 `0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e`。

- [ ] **Step 3: 校验上游版本**

```powershell
Select-String -Path "$tmp\app\build.gradle" -Pattern 'versionName|versionCode|applicationId'
```

Expected: `versionName "5.6.3"`、`versionCode 563`、`applicationId "com.fongmi.android.tv"`。

- [ ] **Step 4: 复制源码到新仓库**

```powershell
robocopy $tmp 'F:\APK\Onefilm' /E /XD .git build .gradle .idea /XF local.properties
if ($LASTEXITCODE -ge 8) { throw "robocopy failed: $LASTEXITCODE" }
```

Expected: 源码、`gradle/`、`build-logic/`、`app/libs/` 都存在；`local.properties` 未复制。

- [ ] **Step 5: 初始化 git 并记录上游**

```powershell
Set-Location 'F:\APK\Onefilm'
git init
git remote add upstream https://github.com/FongMi/TV.git
git fetch upstream fongmi
```

创建 `UPSTREAM.md`，至少包含：

```markdown
# Upstream

- Repo: https://github.com/FongMi/TV
- Branch: fongmi
- Commit: 0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e
- Version: 5.6.3
- Date: 2026-10-18
- License: GPL-3.0
```

- [ ] **Step 6: 初始提交**

```powershell
git add .
git commit -m "chore: import FongMi/TV 5.6.3 (0af3b31)"
```

Expected: commit 成功；`git status` clean。

---

### Task 2: 让 debug/CI 在没有 release keystore 时可构建

**Files:**
- Modify: `app/build.gradle`
- Create: `local.properties.example`
- Modify: `.gitignore`（确保 `local.properties` 被忽略）
- Modify: `README.md`（构建说明）

**Interfaces:**
- Produces: `.\gradlew.bat :app:assembleLeanbackDebug :app:assembleMobileDebug` 在只有 `sdk.dir` 的 `local.properties` 下成功
- Produces: release 构建在缺少签名时给出明确错误，不静默使用 debug 签名

- [ ] **Step 1: 写失败验证**

创建 `local.properties`，只写 SDK 路径：

```properties
sdk.dir=C\:\\Users\\Amy\\AppData\\Local\\Android\\Sdk
```

运行：

```powershell
.\gradlew.bat :app:assembleLeanbackDebug
```

Expected: 如果当前配置因 `storeFile` 为空失败，记录错误；这就是本任务要修的问题。

- [ ] **Step 2: 修改 `app/build.gradle` 的签名配置**

把无条件读取改成条件配置，参考：

```groovy
def releaseStoreFile = localProps['storeFile']
def hasReleaseSigning =
    releaseStoreFile &&
    localProps['keyAlias'] &&
    localProps['storePassword'] &&
    localProps['keyPassword']

android {
    signingConfigs {
        if (hasReleaseSigning) {
            release {
                storeFile file(releaseStoreFile)
                keyAlias localProps['keyAlias']
                keyPassword localProps['keyPassword']
                storePassword localProps['storePassword']
            }
        }
    }

    buildTypes {
        release {
            minifyEnabled = true
            shrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.release
            }
            // ...
        }
    }
}
```

保留 release 的 R8/资源压缩配置；不要为了 debug 关闭混淆。

- [ ] **Step 3: 增加 `local.properties.example`**

```properties
# Android SDK
sdk.dir=C\:\\Users\\Amy\\AppData\\Local\\Android\\Sdk

# Release signing (optional for debug; required for signed release)
storeFile=C\:\\path\\to\\onefilm-release.jks
keyAlias=onefilm
storePassword=change-me
keyPassword=change-me
```

- [ ] **Step 4: 验证 debug 构建**

```powershell
.\gradlew.bat :app:assembleLeanbackDebug :app:assembleMobileDebug
```

Expected: `BUILD SUCCESSFUL`；两个 APK 生成。

- [ ] **Step 5: 验证 release 缺签名行为**

```powershell
.\gradlew.bat :app:assembleLeanbackRelease
```

Expected: 要么成功生成 unsigned release，要么给出明确错误；不能因为 `storeFile` 空指针崩溃。

- [ ] **Step 6: 提交**

```powershell
git add app/build.gradle local.properties.example .gitignore README.md
git commit -m "build: make release signing optional for debug and CI"
```

---

### Task 3: 应用身份改名（名称/版本/图标）

**Files:**
- Modify: `settings.gradle`
- Modify: `app/build.gradle`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/leanback/res/values/strings.xml`（如存在）
- Modify: `app/src/mobile/res/values/strings.xml`（如存在）
- Replace: `app/src/main/res/mipmap-*/ic_launcher*`、`ic_banner*`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1 基线
- Produces: 应用名 `OneFilm`、版本 `0.1.0`、原创图标/横幅；包名仍是旧值

- [ ] **Step 1: 改工程名与版本**

`settings.gradle`：

```groovy
rootProject.name = "OneFilm"
```

`app/build.gradle`：

```groovy
versionCode 1
versionName "0.1.0"
```

- [ ] **Step 2: 改应用名**

所有 `app_name` 资源改为：

```xml
<string name="app_name">OneFilm</string>
```

检查：

```powershell
git grep -n "app_name"
```

Expected: 所有定义都指向 `OneFilm`，没有遗漏的旧名。

- [ ] **Step 3: 替换图标/横幅**

创建原创 OneFilm 图标与 TV banner；不要使用 FongMi、TVBox、Apple TV、Disney+、Netflix 素材。至少覆盖：

- `mipmap-mdpi` 到 `mipmap-xxxhdpi` 的 `ic_launcher`
- `ic_launcher_round`
- `ic_banner`（leanback）
- adaptive icon 的 foreground/background（如存在）

- [ ] **Step 4: 验证构建与显示名**

```powershell
.\gradlew.bat :app:assembleLeanbackDebug :app:assembleMobileDebug
apkanalyzer manifest print app\build\outputs\apk\leanback\debug\app-leanback-debug.apk | Select-String "application-label|package"
```

Expected: label 为 `OneFilm`；package 仍是 `com.fongmi.android.tv`（Task 4 再改）。

- [ ] **Step 5: 提交**

```powershell
git add settings.gradle app/build.gradle app/src README.md
git commit -m "chore: rebrand app identity to OneFilm"
```

---

### Task 4: 应用包与 namespace 全量重命名

**Files:**
- Modify: `app/build.gradle`
- Modify: `settings.gradle`
- Move: `app/src/main/java/com/fongmi/android/tv/**` → `app/src/main/java/com/onefilm/android/tv/**`
- Move: `app/src/leanback/java/com/fongmi/android/tv/**` → `app/src/leanback/java/com/onefilm/android/tv/**`
- Move: `app/src/mobile/java/com/fongmi/android/tv/**` → `app/src/mobile/java/com/onefilm/android/tv/**`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/leanback/AndroidManifest.xml`
- Modify: `app/src/mobile/AndroidManifest.xml`
- Modify: `app/src/main/res/layout/*.xml`
- Modify: `app/proguard-rules.pro`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 3 的 `OneFilm` 身份
- Produces: TV `applicationId = com.onefilm.android.tv`；手机 `com.onefilm.android.tv.mobile`；`namespace = com.onefilm.android.tv`
- Produces: 代码中不再有 `com.fongmi.android.tv`；引擎包 `com.fongmi.quickjs`/`hook`/`chaquo`/`gradle` 保留

- [ ] **Step 1: 开分支**

```powershell
git checkout -b rename/onefilm-package
```

- [ ] **Step 2: 移动 Java/Kotlin 目录**

```powershell
New-Item -ItemType Directory -Force 'app\src\main\java\com\onefilm\android' | Out-Null
git mv 'app\src\main\java\com\fongmi\android\tv' 'app\src\main\java\com\onefilm\android\tv'

New-Item -ItemType Directory -Force 'app\src\leanback\java\com\onefilm\android' | Out-Null
git mv 'app\src\leanback\java\com\fongmi\android\tv' 'app\src\leanback\java\com\onefilm\android\tv'

New-Item -ItemType Directory -Force 'app\src\mobile\java\com\onefilm\android' | Out-Null
git mv 'app\src\mobile\java\com\fongmi\android\tv' 'app\src\mobile\java\com\onefilm\android\tv'
```

检查是否还有 `com/fongmi` 其他包：

```powershell
Get-ChildItem -Recurse 'app\src\*\java\com\fongmi' -Directory -ErrorAction SilentlyContinue |
  Select-Object FullName
```

Expected: 只应剩 `com.fongmi.hook` 等引擎/内部包；不要整棵删除。

- [ ] **Step 3: 替换应用包名**

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

不要替换 `com.fongmi.quickjs`、`com.fongmi.hook`、`com.fongmi.chaquo`、`com.fongmi.gradle`；不要对 `README.md`、`docs/**`、`UPSTREAM.md` 做自动替换。

- [ ] **Step 4: 更新 Gradle**

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

- [ ] **Step 5: 更新 XML 自定义 View 与 ProGuard**

检查 `app/src/main/res/layout`：

```powershell
git grep -n "com\.fongmi\.android\.tv" -- app/src/main/res
```

Expected: 无输出。若有，改为 `com.onefilm.android.tv...`。

更新 `app/proguard-rules.pro`：

```proguard
-keep class com.onefilm.android.tv.bean.** { *; }
```

保留：

```proguard
-keep class com.fongmi.quickjs.method.** { *; }
```

- [ ] **Step 6: 检查零残留**

```powershell
git grep -n "com\.fongmi\.android\.tv" -- `
  ':!docs/**' ':!README.md' ':!UPSTREAM.md'
```

Expected: 无输出。

```powershell
Get-ChildItem -Recurse 'app\src' -Directory |
  Where-Object { $_.FullName -match 'com\\fongmi\\android\\tv' }
```

Expected: 无输出。

- [ ] **Step 7: 构建两个 flavor**

```powershell
.\gradlew.bat clean :app:assembleLeanbackDebug :app:assembleMobileDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 8: 校验 applicationId**

```powershell
apkanalyzer manifest application-id app\build\outputs\apk\leanback\debug\app-leanback-debug.apk
apkanalyzer manifest application-id app\build\outputs\apk\mobile\debug\app-mobile-debug.apk
```

Expected:

```text
com.onefilm.android.tv
com.onefilm.android.tv.mobile
```

- [ ] **Step 9: 安装并 smoke**

```powershell
adb install -r app\build\outputs\apk\leanback\debug\app-leanback-debug.apk
adb install -r app\build\outputs\apk\mobile\debug\app-mobile-debug.apk
adb shell pm list packages | Select-String onefilm
```

Expected: 两个包都安装成功；冷启动不崩溃；打开首页、搜索、详情、播放、直播、设置各一次。

- [ ] **Step 10: 提交**

```powershell
git add .
git commit -m "refactor: rename app package to com.onefilm.android.tv"
```

---

### Task 5: Kotlin/Compose build spike

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: root `build.gradle`（如存在）或 `settings.gradle` 的 plugin 配置
- Modify: `app/build.gradle`
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `app/src/debug/java/com/onefilm/android/tv/debug/ComposeSmokeActivity.kt`

**Interfaces:**
- Consumes: Task 4 的 `com.onefilm.android.tv`
- Produces: 内置 Kotlin 2.3.20 + Compose 插件可用；`ComposeSmokeActivity` 能在两个 flavor 启动
- Produces: AGP 9 built-in Kotlin 处理方式已确定（见本计划 Verified Environment Baseline）

- [ ] **Step 1: 加版本与插件**

`gradle/libs.versions.toml` 增加（版本已于 2026-10-18 实测）：

```toml
[versions]
kotlin = "2.3.20"
composeBom = "2026.09.00"
activityCompose = "1.13.0"
tvMaterial = "1.1.0"
tvFoundation = "1.0.0"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
tv-material = { group = "androidx.tv", name = "tv-material", version.ref = "tvMaterial" }
tv-foundation = { group = "androidx.tv", name = "tv-foundation", version.ref = "tvFoundation" }

[plugins]
# 不要加 kotlin-android：AGP 9 内置 Kotlin，再加会构建失败。
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`app/build.gradle`：

```groovy
plugins {
    alias libs.plugins.android.application
    alias libs.plugins.kotlin.compose
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation platform(libs.compose.bom)
    implementation libs.activity.compose
    implementation libs.compose.ui
    implementation libs.compose.ui.tooling.preview
    implementation libs.compose.material3
    implementation libs.tv.material
    implementation libs.tv.foundation
    debugImplementation libs.compose.ui.tooling
}
```

**已验证（2026-10-18）**：AGP 9.3.1 内置 Kotlin 2.3.20，Compose 插件 2.3.20 可直接使用。不要再加 `org.jetbrains.kotlin.android`，也不要设 `android.builtInKotlin=false`；`kotlinOptions` 已不适用，jvmTarget 用上面的 `kotlin { compilerOptions { ... } }`。详见本计划开头 “Verified Environment Baseline”。

- [ ] **Step 2: 加 debug smoke Activity**

```kotlin
package com.onefilm.android.tv.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class ComposeSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("OneFilm Compose")
                }
            }
        }
    }
}
```

`app/src/debug/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name=".debug.ComposeSmokeActivity"
            android:exported="true" />
    </application>
</manifest>
```

- [ ] **Step 3: 构建两个 flavor**

```powershell
.\gradlew.bat clean :app:assembleLeanbackDebug :app:assembleMobileDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 启动 smoke**

```powershell
adb install -r app\build\outputs\apk\leanback\debug\app-leanback-debug.apk
adb shell am start -n com.onefilm.android.tv/com.onefilm.android.tv.debug.ComposeSmokeActivity
```

Expected: 显示 `OneFilm Compose`。

- [ ] **Step 5: 提交**

```powershell
git add gradle/libs.versions.toml app/build.gradle app/src/debug README.md
git commit -m "build: add Kotlin and Compose build spike"
```

---

### Task 6: 创建 `:core` 模块并迁移 `bean`

**Files:**
- Create: `core/build.gradle`
- Create: `core/src/main/AndroidManifest.xml`
- Create: `core/src/main/java/com/onefilm/android/tv/core/CoreContext.kt`
- Modify: `settings.gradle`
- Modify: `app/build.gradle`
- Move: `app/src/main/java/com/onefilm/android/tv/bean/**` → `core/src/main/java/com/onefilm/android/tv/bean/**`
- Modify: `app/src/main/java/com/onefilm/android/tv/App.java`（调用 `CoreContext.init`）

**Interfaces:**
- Produces: `object CoreContext { fun init(context: Context); fun requireContext(): Context }`
- Produces: `:core` 模块，包名 `com.onefilm.android.tv.core`；`bean` 类包名仍是 `com.onefilm.android.tv.bean`
- Consumes: Task 4 的包名、Task 5 的 Kotlin

- [ ] **Step 1: 创建 `:core`**

`core/build.gradle`：

```groovy
plugins {
    alias libs.plugins.android.library
}

android {
    namespace = 'com.onefilm.android.tv.core'
    compileSdk libs.versions.compileSdk.get().toInteger()

    defaultConfig {
        minSdk libs.versions.minSdk.get().toInteger()
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation libs.annotation
    implementation libs.gson
    implementation libs.simple.xml
}
```

`core/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

`settings.gradle` 增加：

```groovy
include ':core'
```

`app/build.gradle` 增加：

```groovy
implementation project(':core')
```

- [ ] **Step 2: 增加 `CoreContext`**

```kotlin
package com.onefilm.android.tv.core

import android.content.Context

object CoreContext {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requireContext(): Context =
        appContext ?: error("CoreContext is not initialized")
}
```

`App.onCreate()` 最前面调用：

```java
CoreContext.INSTANCE.init(this);
```

- [ ] **Step 3: 迁移 `bean`**

```powershell
New-Item -ItemType Directory -Force 'core\src\main\java\com\onefilm\android\tv' | Out-Null
git mv 'app\src\main\java\com\onefilm\android\tv\bean' 'core\src\main\java\com\onefilm\android\tv\bean'
```

保持包名 `com.onefilm.android.tv.bean` 不变；只改变模块归属。

- [ ] **Step 4: 编译**

```powershell
.\gradlew.bat :core:assembleDebug :app:assembleLeanbackDebug :app:assembleMobileDebug
```

Expected: `BUILD SUCCESSFUL`。如果缺少依赖，按编译错误补到 `core/build.gradle`，不要复制整个 `:app` 依赖。

- [ ] **Step 5: 安装 smoke**

```powershell
adb install -r app\build\outputs\apk\leanback\debug\app-leanback-debug.apk
```

Expected: 冷启动、首页、详情、播放正常。

- [ ] **Step 6: 提交**

```powershell
git add settings.gradle app/build.gradle core app/src/main/java/com/onefilm/android/tv/App.java
git commit -m "refactor: introduce core module and move bean models"
```

---

### Task 7: 迁移 `setting` 与 `event` 到 `:core`

**Files:**
- Move: `app/src/main/java/com/onefilm/android/tv/setting/**` → `core/src/main/java/com/onefilm/android/tv/setting/**`
- Move: `app/src/main/java/com/onefilm/android/tv/event/**` → `core/src/main/java/com/onefilm/android/tv/event/**`
- Modify: `app/src/main/java/com/onefilm/android/tv/App.java`
- Modify: `App.get()` 的调用点或增加兼容代理
- Modify: `core/build.gradle`
- Create: `app/src/main/java/com/onefilm/android/tv/compat/SettingText.java`（承接 UI 字符串 helper）
- 可能 Move: `setting`/`event` 依赖的纯 `utils` 类

**Interfaces:**
- Consumes: Task 6 的 `CoreContext`
- Produces: `:core` 提供 Setting/Event；`App.get()` 仍可作为兼容入口，但 `:core` 不再依赖 `:app`
- Produces: `SettingText.getSwitch(boolean)` 在 `:app`，供旧 UI 使用；`:core` 的 `Setting` 不再 import `R`/`ResUtil`

- [ ] **Step 1: 先做依赖审计**

```powershell
git grep -n "com\.onefilm\.android\.tv\.\(api\|db\|utils\)" -- `
  app/src/main/java/com/onefilm/android/tv/setting `
  app/src/main/java/com/onefilm/android/tv/event

git grep -n "R\.string\|ResUtil\|Notify\|Toast\|Dialog" -- `
  app/src/main/java/com/onefilm/android/tv/setting `
  app/src/main/java/com/onefilm/android/tv/event
```

按结果决定顺序：

- 只依赖 `bean`/纯工具：先移动 `setting`/`event`，把用到的纯工具一起移动。
- 依赖 `api`/`db`：先记下循环依赖，改为先迁移 `api`/`db`，或把共享类型下沉到 `:core` 的独立包。
- 依赖 `R`/`ResUtil`（例如 `Setting.getSwitch`）：把 UI 字符串 helper 拆到 `:app` 的 `SettingText`；`:core` 的 `Setting` 只保留纯偏好读写。
- 依赖 `Notify`/`FileChooser`/Dialog 等 UI 工具：这些留在 `:app`；`:core` 不得反向依赖。

- [ ] **Step 2: 移动目录**

```powershell
git mv 'app\src\main\java\com\onefilm\android\tv\setting' 'core\src\main\java\com\onefilm\android\tv\setting'
git mv 'app\src\main\java\com\onefilm\android\tv\event' 'core\src\main\java\com\onefilm\android\tv\event'
```

- [ ] **Step 3: 拆掉 Setting 的 UI 依赖**

`Setting.getSwitch` 这类只用于界面显示的字符串 helper 不要留在 `:core`。在 `:app` 新建：

```java
package com.onefilm.android.tv.compat;

import com.onefilm.android.tv.R;
import com.onefilm.android.tv.utils.ResUtil;

public final class SettingText {
    public static String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }
}
```

把旧调用 `Setting.getSwitch(...)` 改为 `SettingText.getSwitch(...)`；`:core` 的 `Setting` 删除 `getSwitch` 及 `R`/`ResUtil` import。

`:core` 需要 `Prefers` 时，加：

```groovy
implementation project(':catvod')
```

如果 `event` 也引用 `Notify`/Dialog，按同样方式把 UI 部分上移到 `:app`，`:core` 只保留事件数据。

- [ ] **Step 4: 处理 `App.get()`**

如果 `setting`/`event` 引用 `App.get()`：

- 在 `:core` 中用 `CoreContext.requireContext()` 替代。
- 保留 `:app` 的 `App.get()`，内部委托 `CoreContext.requireContext()`，避免一次性改所有旧 UI 调用。

示例：

```java
public static Context get() {
    return CoreContext.INSTANCE.requireContext();
}
```

- [ ] **Step 5: 编译**

```powershell
.\gradlew.bat :core:assembleDebug :app:assembleLeanbackDebug :app:assembleMobileDebug
```

Expected: `BUILD SUCCESSFUL`；EventBus index 仍为 `com.onefilm.android.tv.event.EventIndex`。

- [ ] **Step 6: 回归**

冷启动、设置页、配置导入、历史/收藏、播放。

- [ ] **Step 7: 提交**

```powershell
git add core app
git commit -m "refactor: move settings and events into core module"
```

---

### Task 8: 迁移 `api` 与 `db` 到 `:core`

**Files:**
- Move: `app/src/main/java/com/onefilm/android/tv/api/**` → `core/src/main/java/com/onefilm/android/tv/api/**`
- Move: `app/src/main/java/com/onefilm/android/tv/db/**` → `core/src/main/java/com/onefilm/android/tv/db/**`
- Modify: `core/build.gradle`（Room、OkHttp、EventBus 等）
- Modify: `app/build.gradle`（移除已迁移依赖）

**Interfaces:**
- Consumes: Task 7 的 Setting/Event、Task 6 的 `:core`
- Produces: `:core` 提供 API/DB；`:app` 只保留 UI/播放器/服务

- [ ] **Step 1: 依赖审计**

```powershell
git grep -n "com\.onefilm\.android\.tv\.\(api\|db\)" -- `
  app/src/main/java/com/onefilm/android/tv/api `
  app/src/main/java/com/onefilm/android/tv/db

git grep -n "Notify\|Toast\|ResUtil\|R\." -- `
  app/src/main/java/com/onefilm/android/tv/api `
  app/src/main/java/com/onefilm/android/tv/db
```

确认 `api`/`db` 依赖的 `Setting`、`Event`、`CoreContext` 已在 `:core`；如果仍有反向依赖，先拆分共享类型，不要强行移动。

如果 `api`/`db` 直接弹 Toast/Dialog，改为发 `Event` 或在 `:app` 层处理；`:core` 不得依赖 UI。

如果 `api`/`db` 引用 `App.get()`，改为 `CoreContext.requireContext()`；`:core` 不能反向依赖 `:app`。

- [ ] **Step 2: 移动 `api` 与 `db`**

```powershell
git mv 'app\src\main\java\com\onefilm\android\tv\api' 'core\src\main\java\com\onefilm\android\tv\api'
git mv 'app\src\main\java\com\onefilm\android\tv\db' 'core\src\main\java\com\onefilm\android\tv\db'
```

- [ ] **Step 3: 补 `:core` 依赖**

按编译错误补：

```groovy
implementation libs.room.runtime
annotationProcessor libs.room.compiler
implementation libs.bundles.okhttp
implementation libs.eventbus
implementation libs.gson
implementation libs.simple.xml
```

如果 `api`/`db` 依赖 `:catvod` 或 `:quickjs`，在 `:core` 加：

```groovy
implementation project(':catvod')
implementation project(':quickjs')
implementation project(':chaquo')
```

- [ ] **Step 4: 编译**

```powershell
.\gradlew.bat :core:assembleDebug :app:assembleLeanbackDebug :app:assembleMobileDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: 回归配置/搜索/播放**

```powershell
adb install -r app\build\outputs\apk\leanback\debug\app-leanback-debug.apk
```

Expected: 导入 `https://tvbox.alibabagroup.de5.net/config.json`，首页、搜索、详情、播放、直播至少各跑一次。

- [ ] **Step 6: 提交**

```powershell
git add core app/build.gradle
git commit -m "refactor: move api and db into core module"
```

---

### Task 9: 迁移非 UI `utils` 并定义播放契约

**Files:**
- Move: `app/src/main/java/com/onefilm/android/tv/utils/**` 中非 UI 类 → `core/src/main/java/com/onefilm/android/tv/utils/**`
- Create: `core/src/main/java/com/onefilm/android/tv/core/playback/PlaybackRequest.kt`
- Create: `core/src/main/java/com/onefilm/android/tv/core/playback/PlaybackLauncher.kt`
- Create: `core/src/main/java/com/onefilm/android/tv/core/playback/LegacyNavigator.kt`

**Interfaces:**
- Produces:

```kotlin
data class PlaybackRequest(
    val siteKey: String,
    val vodId: String,
    val title: String,
    val episodeName: String? = null,
    val episodeUrl: String? = null,
    val isLive: Boolean = false,
)

fun interface PlaybackLauncher {
    fun launch(request: PlaybackRequest)
}

interface LegacyNavigator {
    fun openSearch()
    fun openLive()
    fun openHistory()
    fun openSettings()
    fun openLegacyHome()
}
```

- Consumes: Task 7/8 的 `:core`

- [ ] **Step 1: 审计 `utils`**

```powershell
Get-ChildItem -Recurse 'app\src\main\java\com\onefilm\android\tv\utils' -File |
  Select-Object Name
```

把纯工具类（网络、文件、加密、日志、线程）移到 `:core`；`Notify`、Toast、Snackbar、Dialog 等 UI 工具留在 `:app`。

- [ ] **Step 2: 移动非 UI 工具**

```powershell
# 按实际文件逐个 git mv；不要整目录盲移
git mv 'app\src\main\java\com\onefilm\android\tv\utils\Util.java' 'core\src\main\java\com\onefilm\android\tv\utils\Util.java'
```

- [ ] **Step 3: 新增播放契约**

按上面的接口创建文件；包名 `com.onefilm.android.tv.core.playback`。

- [ ] **Step 4: 编译**

```powershell
.\gradlew.bat :core:assembleDebug :app:assembleLeanbackDebug :app:assembleMobileDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```powershell
git add core app
git commit -m "refactor: move non-ui utils and add playback contracts"
```

---

### Task 10: 创建 `:designsystem` 主题骨架

**Files:**
- Create: `designsystem/build.gradle`
- Create: `designsystem/src/main/AndroidManifest.xml`
- Create: `designsystem/src/main/java/com/onefilm/android/tv/designsystem/theme/Color.kt`
- Create: `designsystem/src/main/java/com/onefilm/android/tv/designsystem/theme/Type.kt`
- Create: `designsystem/src/main/java/com/onefilm/android/tv/designsystem/theme/Shape.kt`
- Create: `designsystem/src/main/java/com/onefilm/android/tv/designsystem/theme/OneFilmTheme.kt`
- Modify: `settings.gradle`

**Interfaces:**
- Produces:

```kotlin
@Composable
fun OneFilmTheme(content: @Composable () -> Unit)
```

- Produces tokens: `OneFilmColors`, `OneFilmTypography`, `OneFilmShapes`
- Consumes: 无业务依赖

- [ ] **Step 1: 创建模块**

`designsystem/build.gradle`：

```groovy
plugins {
    alias libs.plugins.android.library
    alias libs.plugins.kotlin.compose
}

android {
    namespace = 'com.onefilm.android.tv.designsystem'
    compileSdk libs.versions.compileSdk.get().toInteger()

    defaultConfig {
        minSdk libs.versions.minSdk.get().toInteger()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation platform(libs.compose.bom)
    implementation libs.compose.ui
    implementation libs.compose.material3
    implementation libs.compose.ui.tooling.preview
    debugImplementation libs.compose.ui.tooling
}
```

`settings.gradle` 增加：

```groovy
include ':designsystem'
```

- [ ] **Step 2: 写 tokens**

按 `docs/architecture.md` 第 6 节写颜色、字体、形状。`OneFilmTheme` 使用 `darkColorScheme`，不要用默认紫色。

- [ ] **Step 3: 编译**

```powershell
.\gradlew.bat :designsystem:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```powershell
git add settings.gradle designsystem
git commit -m "feat: add OneFilm design system theme skeleton"
```

---

### Task 11: 创建 `:ui` 模块、宿主 Activity 与占位首页

**Files:**
- Create: `ui/build.gradle`
- Create: `ui/src/main/AndroidManifest.xml`
- Create: `ui/src/main/java/com/onefilm/android/tv/ui/OneFilmApp.kt`
- Create: `ui/src/main/java/com/onefilm/android/tv/ui/UiDependencies.kt`
- Create: `ui/src/main/java/com/onefilm/android/tv/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/onefilm/android/tv/host/OneFilmHomeActivity.kt`
- Create: `app/src/main/java/com/onefilm/android/tv/host/AppPlaybackLauncher.kt`
- Create: `app/src/main/java/com/onefilm/android/tv/host/AppLegacyNavigator.kt`
- Modify: `settings.gradle`
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/onefilm/android/tv/App.java`（暴露 `uiDependencies`）
- Modify: `app/src/leanback/AndroidManifest.xml`（仅加 debug/experimental activity，不切 launcher）
- Modify: `app/src/mobile/AndroidManifest.xml`（同上）

**Interfaces:**
- Consumes: `:core`, `:designsystem`, Task 5 的 Compose
- Produces:

```kotlin
data class UiDependencies(
    val appName: String,
    val playbackLauncher: PlaybackLauncher,
    val legacyNavigator: LegacyNavigator,
)

@Composable
fun OneFilmApp(dependencies: UiDependencies)

@Composable
fun HomeScreen(
    onPlay: (PlaybackRequest) -> Unit,
    onOpenLegacy: () -> Unit,
)
```

- Produces: `OneFilmHomeActivity` 可手动启动；旧 `HomeActivity` 仍是默认 launcher

- [ ] **Step 1: 创建 `:ui` 模块（带 flavor）**

`ui/build.gradle`：

```groovy
plugins {
    alias libs.plugins.android.library
    alias libs.plugins.kotlin.compose
}

android {
    namespace = 'com.onefilm.android.tv.ui'
    compileSdk libs.versions.compileSdk.get().toInteger()

    defaultConfig {
        minSdk libs.versions.minSdk.get().toInteger()
    }

    flavorDimensions = ["device"]
    productFlavors {
        leanback { dimension "device" }
        mobile { dimension "device" }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation project(':core')
    implementation project(':designsystem')
    implementation platform(libs.compose.bom)
    implementation libs.activity.compose
    implementation libs.compose.ui
    implementation libs.compose.material3
    implementation libs.compose.ui.tooling.preview
    leanbackImplementation libs.tv.material
    leanbackImplementation libs.tv.foundation
    debugImplementation libs.compose.ui.tooling
}
```

`settings.gradle` 增加：

```groovy
include ':ui'
```

`app/build.gradle` 增加：

```groovy
implementation project(':ui')
```

- [ ] **Step 2: 写 `UiDependencies` 与 `OneFilmApp`**

```kotlin
package com.onefilm.android.tv.ui

import androidx.compose.runtime.Composable
import com.onefilm.android.tv.core.playback.LegacyNavigator
import com.onefilm.android.tv.core.playback.PlaybackLauncher
import com.onefilm.android.tv.designsystem.theme.OneFilmTheme
import com.onefilm.android.tv.ui.home.HomeScreen

data class UiDependencies(
    val appName: String,
    val playbackLauncher: PlaybackLauncher,
    val legacyNavigator: LegacyNavigator,
)

@Composable
fun OneFilmApp(dependencies: UiDependencies) {
    OneFilmTheme {
        HomeScreen(
            onPlay = dependencies.playbackLauncher::launch,
            onOpenLegacy = dependencies.legacyNavigator::openLegacyHome,
        )
    }
}
```

`HomeScreen` 先显示 `OneFilm`、版本、两个按钮：“播放示例”和“打开旧版首页”。不要在这一步接真实数据。

- [ ] **Step 3: 写宿主 Activity**

```kotlin
package com.onefilm.android.tv.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.onefilm.android.tv.App
import com.onefilm.android.tv.ui.OneFilmApp

class OneFilmHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deps = (application as App).uiDependencies
        setContent { OneFilmApp(deps) }
    }
}
```

`App.java` 增加：

```java
private UiDependencies uiDependencies;

public UiDependencies getUiDependencies() {
    return uiDependencies;
}
```

在 `onCreate()` 里初始化：

```java
uiDependencies = new UiDependencies(
    getString(R.string.app_name),
    new AppPlaybackLauncher(this),
    new AppLegacyNavigator(this)
);
```

- [ ] **Step 4: 写 `AppPlaybackLauncher` / `AppLegacyNavigator`**

首版可以只做导航，不接真实播放：

```kotlin
package com.onefilm.android.tv.host

import android.content.Context
import com.onefilm.android.tv.core.playback.PlaybackLauncher
import com.onefilm.android.tv.core.playback.PlaybackRequest

class AppPlaybackLauncher(private val context: Context) : PlaybackLauncher {
    override fun launch(request: PlaybackRequest) {
        // P1 Home 接真实数据后再实现
    }
}
```

```kotlin
package com.onefilm.android.tv.host

import android.content.Context
import android.content.Intent
import com.onefilm.android.tv.core.playback.LegacyNavigator
import com.onefilm.android.tv.ui.activity.HomeActivity

class AppLegacyNavigator(private val context: Context) : LegacyNavigator {
    override fun openSearch() = Unit
    override fun openLive() = Unit
    override fun openHistory() = Unit
    override fun openSettings() = Unit

    override fun openLegacyHome() {
        context.startActivity(
            Intent(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
```

`openLegacyHome` 必须实现，保证 fallback 可用；其他方法可以按 flavor 逐步补。

注意：不要在 `app/src/main` 里直接引用只在单个 flavor 存在的 Activity（如 mobile 的 `HistoryActivity`、`ScanActivity`，leanback 的 `SettingActivity`、`CastActivity`），否则另一个 flavor 会编译失败。做法：

- `openLegacyHome` 可以在 `src/main` 引用 `HomeActivity`（两个 flavor 都有）。
- flavor 专属跳转放到 `app/src/leanback/.../AppLegacyNavigator.kt` 和 `app/src/mobile/.../AppLegacyNavigator.kt`，或使用 Intent action/类名字符串。
- foundation 阶段未实现的入口先 no-op，不要为了“看起来完整”引入跨 flavor 引用。

- [ ] **Step 5: Manifest 注册（不切 launcher）**

leanback/mobile manifest 各加：

```xml
<activity
    android:name=".host.OneFilmHomeActivity"
    android:exported="true"
    android:screenOrientation="sensorLandscape" />
```

保留旧 `HomeActivity` 的 MAIN/LAUNCHER intent-filter，暂不改默认入口。

- [ ] **Step 6: 构建**

```powershell
.\gradlew.bat :ui:assembleLeanbackDebug :ui:assembleMobileDebug `
  :app:assembleLeanbackDebug :app:assembleMobileDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 7: 启动占位首页**

```powershell
adb install -r app\build\outputs\apk\leanback\debug\app-leanback-debug.apk
adb shell am start -n com.onefilm.android.tv/com.onefilm.android.tv.host.OneFilmHomeActivity
```

Expected: 显示 OneFilm 占位页；“打开旧版首页”能回到旧 UI。

- [ ] **Step 8: 提交**

```powershell
git add settings.gradle app/build.gradle app/src ui
git commit -m "feat: add ui module and OneFilm compose host shell"
```

---

### Task 12: Foundation smoke 与 tag

**Files:**
- Create: `docs/baseline-smoke.md`
- Modify: `docs/upstream.md`（同步记录）
- Tag: `v0.1.0-foundation`

**Interfaces:**
- Consumes: Task 1–11
- Produces: 可重复的 foundation 构建/安装/回归记录

- [ ] **Step 1: 跑构建**

```powershell
.\gradlew.bat clean :app:assembleLeanbackDebug :app:assembleMobileDebug
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 跑 smoke**

记录以下结果：

- TV APK：安装、冷启动、旧首页、旧搜索、旧详情、播放、直播、设置。
- 手机 APK：安装、冷启动、旧首页、搜索、详情、播放、PiP、扫码（如适用）。
- 新入口：`OneFilmHomeActivity` 显示占位页；`openLegacyHome` 返回旧首页。
- 配置：`https://tvbox.alibabagroup.de5.net/config.json` 导入成功。
- 多仓：11 条入口可用。
- 引擎：Java/JS/Python 站点各一个。
- 包名：`com.onefilm.android.tv` / `com.onefilm.android.tv.mobile`。
- App 名：`OneFilm`。

- [ ] **Step 3: 写 `docs/baseline-smoke.md`**

至少包含：日期、commit、APK 路径、设备、每项结果、失败项与后续任务。

- [ ] **Step 4: 提交并打 tag**

```powershell
git add docs/baseline-smoke.md docs/upstream.md
git commit -m "docs: record foundation smoke baseline"
git tag v0.1.0-foundation
```

Expected: tag 指向 foundation commit。

---

## Self-Review

- **Spec coverage:** 包名/身份/两 APK/模块/Compose/旧 UI fallback/上游/GPL 都在 Task 1–12 覆盖；UI 屏幕设计本身不在本计划，属于后续 P1 计划。
- **Step scan:** 每个任务都有构建/安装/smoke 验证；机械重命名任务用 grep + 启动验证，避免只“编译通过”。
- **Type consistency:** `CoreContext`、`PlaybackRequest`、`PlaybackLauncher`、`LegacyNavigator`、`UiDependencies` 在 Task 6/9/11 中名称一致。
- **Review Focus:** 五个高风险点都有对应任务：Task 4（包名残留）、Task 5（AGP/Compose）、Task 7/8/9（Setting/API/引擎/R8 回归）、Task 11（flavor 依赖）、Task 2（签名）。
- **Proportion:** 本计划只覆盖 foundation；Home/Search/Detail/Player OSD 等 UI 屏幕另写 P1/P2 计划，避免把整个产品塞进一个计划。
