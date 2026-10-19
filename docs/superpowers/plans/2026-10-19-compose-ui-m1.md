# Compose UI M1（模块 / 设计系统 / 首页）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立独立 `:compose` 模块、Apple TV 风设计系统、Compose 宿主与导航，并交付可运行的 Compose 首页（真实数据）。

**Architecture:** 新增 `:compose` Android Library，承载设计系统、导航、首页；`:app` 提供 `bridge` 实现（VodConfig/SiteApi/History/Keep）并新增 `EntryActivity` 路由。旧 XML UI 保持不动，仅作为 `compose_ui=false` 的回退。

**Tech Stack:** Kotlin、Jetpack Compose（BOM 2026.09.00）、Material3、Navigation Compose、androidx.tv:tv-material、现有 Java 数据层。

**Spec:** `docs/superpowers/specs/2026-10-19-compose-ui-design.md`

## Global Constraints

- `minSdk 28`，`compileSdk/targetSdk 37`，Java 21
- AGP 9：禁止应用 `org.jetbrains.kotlin.android` 插件
- `:compose` namespace：`com.onefilm.android.tv.compose`
- `:compose` 不得依赖 `:app`；数据/播放器通过 `bridge` 接口注入
- 不引入 Hilt；沿用现有单例
- 旧 UI 不修改逻辑，只允许改 launcher 入口
- 每个任务结束必须提交；提交信息使用 `feat(compose): ...`
- 主题默认深色，浅色可选；强调色 `#2ED3C6`

## Review Focus

- 配置为空/未加载：首页必须显示空状态或可重试错误，不得崩溃
- 网络超时/站点报错：显示错误文案 + 重试按钮，重试成功后恢复内容
- 缺海报/标题/备注：卡片使用占位图与“未命名”，不得出现空白点击区
- TV 焦点/返回：首页首个焦点可见，返回不退出应用
- 浅色主题：Hero 文字与 scrim 对比度足够，不出现白底白字

---

### Task 1: 创建 `:compose` 模块

**Files:**
- Modify: `settings.gradle`
- Modify: `gradle/libs.versions.toml`
- Create: `compose/build.gradle`
- Create: `compose/src/main/AndroidManifest.xml`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/Placeholder.kt`

**Interfaces:**
- Consumes: 无
- Produces: Gradle 模块 `:compose`，可被 `:app` 依赖

- [ ] **Step 1: 在版本目录加入依赖**

在 `gradle/libs.versions.toml` 的 `[versions]` 增加：

```toml
navigationCompose = "2.7.7"
lifecycleViewModelCompose = "2.8.7"
```

在 `[libraries]` 增加：

```toml
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewModelCompose" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
```

- [ ] **Step 2: 注册模块**

`settings.gradle` 末尾增加：

```groovy
include ':compose'
```

- [ ] **Step 3: 编写模块构建脚本**

创建 `compose/build.gradle`：

```groovy
plugins {
    alias libs.plugins.android.library
    alias libs.plugins.kotlin.compose
}

android {
    namespace = 'com.onefilm.android.tv.compose'
    compileSdk libs.versions.compileSdk.get().toInteger()

    defaultConfig {
        minSdk libs.versions.minSdk.get().toInteger()
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }
}

dependencies {
    implementation platform(libs.compose.bom)
    implementation libs.compose.ui
    implementation libs.compose.material3
    implementation libs.compose.ui.tooling.preview
    implementation libs.activity.compose
    implementation libs.navigation.compose
    implementation libs.lifecycle.viewmodel.compose
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7'
    implementation libs.tv.material
    implementation libs.tv.foundation
    implementation 'io.coil-kt:coil-compose:2.7.0'

    debugImplementation libs.compose.ui.tooling
    debugImplementation libs.compose.ui.test.manifest

    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0'

    androidTestImplementation platform(libs.compose.bom)
    androidTestImplementation libs.compose.ui.test.junit4
    androidTestImplementation 'androidx.test.ext:junit:1.2.1'
}
```

- [ ] **Step 4: 创建空模块文件**

`compose/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

`compose/src/main/java/com/onefilm/android/tv/compose/Placeholder.kt`：

```kotlin
package com.onefilm.android.tv.compose

internal const val MODULE_NAME = "compose"
```

- [ ] **Step 5: 让 app 依赖模块**

在 `app/build.gradle` 的 `dependencies` 增加：

```groovy
implementation project(':compose')
```

- [ ] **Step 6: 验证构建**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Amy\.jdks\jbr-21.0.11'
.\gradlew.bat :compose:assembleDebug :app:assembleMobileDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 提交**

```powershell
git add settings.gradle gradle/libs.versions.toml compose app/build.gradle
git commit -m "feat(compose): add compose module skeleton"
```

---

### Task 2: 设计系统（颜色 / 字体 / 形状 / 间距 / 主题）

**Files:**
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/design/Color.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/design/Type.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/design/Shape.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/design/Spacing.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/design/OneFilmTheme.kt`
- Create: `compose/src/androidTest/java/com/onefilm/android/tv/compose/design/OneFilmThemeTest.kt`

**Interfaces:**
- Consumes: `:compose` 模块
- Produces:
  - `@Composable fun OneFilmTheme(darkTheme: Boolean = true, content: @Composable () -> Unit)`
  - `object OneFilmSpacing { val x1 = 4.dp ... }`
  - `OneFilmColors` 语义色（通过 `MaterialTheme.colorScheme` 暴露）

- [ ] **Step 1: 写主题测试（先失败）**

`OneFilmThemeTest.kt`：

```kotlin
package com.onefilm.android.tv.compose.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OneFilmThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun darkTheme_uses_expected_background_and_accent() {
        var background: Color? = null
        var accent: Color? = null
        compose.setContent {
            OneFilmTheme(darkTheme = true) {
                background = MaterialTheme.colorScheme.background
                accent = MaterialTheme.colorScheme.primary
            }
        }
        assertEquals(Color(0xFF0A0A0C), background)
        assertEquals(Color(0xFF2ED3C6), accent)
    }

    @Test fun lightTheme_has_dark_text_on_light_background() {
        var background: Color? = null
        var onBackground: Color? = null
        compose.setContent {
            OneFilmTheme(darkTheme = false) {
                background = MaterialTheme.colorScheme.background
                onBackground = MaterialTheme.colorScheme.onBackground
            }
        }
        assertEquals(Color(0xFFF7F7F9), background)
        assertEquals(Color(0xFF121216), onBackground)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat :compose:connectedDebugAndroidTest --console=plain
```

Expected: 编译失败，提示 `OneFilmTheme` 未定义

- [ ] **Step 3: 实现颜色与主题**

`Color.kt`：

```kotlin
package com.onefilm.android.tv.compose.design

import androidx.compose.ui.graphics.Color

object OneFilmColors {
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF141418)
    val SurfaceElevated = Color(0xFF1C1C22)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF9B9BA1)
    val Divider = Color(0xFF2A2A31)
    val Accent = Color(0xFF2ED3C6)
    val AccentPressed = Color(0xFF25B3A8)
    val Scrim = Color(0x99000000)
    val LightBackground = Color(0xFFF7F7F9)
    val LightSurface = Color(0xFFFFFFFF)
    val LightTextPrimary = Color(0xFF121216)
    val LightTextSecondary = Color(0xFF5C5C66)
}
```

`OneFilmTheme.kt`：

```kotlin
package com.onefilm.android.tv.compose.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = OneFilmColors.Accent,
    onPrimary = OneFilmColors.Background,
    background = OneFilmColors.Background,
    onBackground = OneFilmColors.TextPrimary,
    surface = OneFilmColors.Surface,
    onSurface = OneFilmColors.TextPrimary,
    surfaceVariant = OneFilmColors.SurfaceElevated,
    onSurfaceVariant = OneFilmColors.TextSecondary,
    outline = OneFilmColors.Divider,
)

private val LightScheme = lightColorScheme(
    primary = OneFilmColors.AccentPressed,
    onPrimary = OneFilmColors.LightBackground,
    background = OneFilmColors.LightBackground,
    onBackground = OneFilmColors.LightTextPrimary,
    surface = OneFilmColors.LightSurface,
    onSurface = OneFilmColors.LightTextPrimary,
    surfaceVariant = OneFilmColors.LightBackground,
    onSurfaceVariant = OneFilmColors.LightTextSecondary,
    outline = OneFilmColors.LightTextSecondary,
)

@Composable
fun OneFilmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = OneFilmTypography,
        shapes = OneFilmShapes,
        content = content,
    )
}
```

- [ ] **Step 4: 实现字体、形状、间距**

`Type.kt`：

```kotlin
package com.onefilm.android.tv.compose.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val OneFilmTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
)
```

`Shape.kt`：

```kotlin
package com.onefilm.android.tv.compose.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val OneFilmShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
```

`Spacing.kt`：

```kotlin
package com.onefilm.android.tv.compose.design

import androidx.compose.ui.unit.dp

object OneFilmSpacing {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val tvSafe = 48.dp
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:

```powershell
.\gradlew.bat :compose:connectedDebugAndroidTest --console=plain
```

Expected: `BUILD SUCCESSFUL`，2 tests passed

- [ ] **Step 6: 提交**

```powershell
git add compose/src/main/java/com/onefilm/android/tv/compose/design compose/src/androidTest
git commit -m "feat(compose): add design tokens and theme"
```

---

### Task 3: 基础组件（海报卡 / Hero / 内容架 / 焦点态）

**Files:**
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/design/component/PosterCard.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/design/component/HeroBanner.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/design/component/SectionRow.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/design/component/FocusableSurface.kt`
- Create: `compose/src/androidTest/java/com/onefilm/android/tv/compose/design/component/ComponentsTest.kt`

**Interfaces:**
- Consumes: `OneFilmTheme`、`OneFilmSpacing`
- Produces:
  - `@Composable fun PosterCard(title: String, posterUrl: String?, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun HeroBanner(title: String, description: String?, backdropUrl: String?, onPlay: () -> Unit, onDetails: () -> Unit)`
  - `@Composable fun SectionRow(title: String, items: List<PosterItem>, onItemClick: (PosterItem) -> Unit)`
  - `data class PosterItem(val id: String, val title: String, val posterUrl: String?, val subtitle: String?)`

- [ ] **Step 1: 写组件测试（先失败）**

`ComponentsTest.kt`：

```kotlin
package com.onefilm.android.tv.compose.design.component

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.onefilm.android.tv.compose.design.OneFilmTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun posterCard_showsTitle_andHandlesClick() {
        var clicked = false
        compose.setContent {
            OneFilmTheme {
                PosterCard(title = "测试影片", posterUrl = null, subtitle = "2026", onClick = { clicked = true })
            }
        }
        compose.onNodeWithText("测试影片").assertIsDisplayed().assertHasClickAction().performClick()
        assertTrue(clicked)
    }

    @Test fun hero_showsTitle_andPlayButton() {
        compose.setContent {
            OneFilmTheme {
                HeroBanner(title = "Hero 标题", description = "简介", backdropUrl = null, onPlay = {}, onDetails = {})
            }
        }
        compose.onNodeWithText("Hero 标题").assertIsDisplayed()
        compose.onNodeWithText("播放").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :compose:connectedDebugAndroidTest --console=plain`
Expected: 编译失败，缺少 `PosterCard` / `HeroBanner`

- [ ] **Step 3: 实现 `PosterCard` 与焦点态**

`FocusableSurface.kt`：

```kotlin
package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp

@Composable
fun FocusableSurface(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .scale(if (focused) 1.03f else 1f)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = OneFilmColors.Accent,
                shape = shape,
            )
            .clip(shape)
            .focusable(interactionSource = interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {},
        content = { content() },
    )
}
```

`PosterCard.kt`：

```kotlin
package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(140.dp)) {
        FocusableSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            onClick = onClick,
        ) {
            if (posterUrl.isNullOrBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("无海报", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title.ifBlank { "未命名" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

> 图片加载使用 `io.coil-kt:coil-compose:2.7.0`（已在 Task 1 加入），不要新增其他图片库。

- [ ] **Step 4: 实现 `HeroBanner` 与 `SectionRow`**

`HeroBanner.kt` 要点（完整代码由实现者按签名补全）：

```kotlin
@Composable
fun HeroBanner(
    title: String,
    description: String?,
    backdropUrl: String?,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // 背景图（backdropUrl 非空时），上面覆盖竖直渐变 scrim
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.displayLarge, maxLines = 2)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPlay) { Text("播放") }
                OutlinedButton(onClick = onDetails) { Text("详情") }
            }
        }
    }
}
```

`SectionRow.kt` 要点：

```kotlin
data class PosterItem(val key: String, val id: String, val title: String, val posterUrl: String?, val subtitle: String?)

@Composable
fun SectionRow(title: String, items: List<PosterItem>, onItemClick: (PosterItem) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(items, key = { it.id }) { item ->
                PosterCard(item.title, item.posterUrl, item.subtitle) { onItemClick(item) }
            }
        }
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `.\gradlew.bat :compose:connectedDebugAndroidTest --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交**

```powershell
git add compose/src/main/java/com/onefilm/android/tv/compose/design/component compose/src/androidTest
git commit -m "feat(compose): add poster, hero and section components"
```

---

### Task 4: Bridge 接口 + app 数据实现

**Files:**
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/bridge/HomeDataSource.kt`
- Create: `app/src/main/java/com/onefilm/android/tv/composebridge/AppHomeDataSource.kt`
- Create: `app/src/main/java/com/onefilm/android/tv/composebridge/HomeFeedMapper.kt`
- Create: `app/src/main/java/com/onefilm/android/tv/composebridge/AppDetailLauncher.kt`
- Create: `app/src/test/java/com/onefilm/android/tv/composebridge/HomeFeedMapperTest.kt`

**Interfaces:**
- Consumes: `VodConfig.ensureLoaded()` / `getHome()` / `getSites()`，`SiteApi.homeContent(Site)`，`History.get()`，`Keep.getVod()`
- Produces:
  - `data class MediaCard(key: String, id: String, title: String, subtitle: String?, posterUrl: String?, progress: Float? = null)`
  - `data class HomeSection(title: String, items: List<MediaCard>)`
  - `data class HomeFeed(hero: List<MediaCard>, sections: List<HomeSection>)`
  - `interface HomeDataSource { suspend fun load(): HomeFeed }`
  - `interface DetailLauncher { fun open(context: Context, card: MediaCard) }`

- [ ] **Step 1: 写 mapper 单元测试（先失败）**

`HomeFeedMapperTest.kt`：

```kotlin
package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.bean.Vod
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedMapperTest {
    @Test fun mapVods_keepsOrder_andFallsBackForBlankTitle() {
        val a = Vod().apply { id = "1"; name = "A"; pic = "http://x/a.jpg" }
        val b = Vod().apply { id = "2"; name = ""; pic = null }

        val cards = HomeFeedMapper.fromVods(listOf(a, b))

        assertEquals(listOf("1", "2"), cards.map { it.id })
        assertEquals("A", cards[0].title)
        assertEquals("未命名", cards[1].title)
        assertEquals("http://x/a.jpg", cards[0].posterUrl)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testMobileDebugUnitTest --tests '*HomeFeedMapperTest' --console=plain`
Expected: 编译失败，`HomeFeedMapper` 未定义

- [ ] **Step 3: 定义 bridge 模型与接口**

`HomeDataSource.kt`：

```kotlin
package com.onefilm.android.tv.compose.bridge

import android.content.Context

data class MediaCard(
    val key: String,
    val id: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val progress: Float? = null,
)

data class HomeSection(val title: String, val items: List<MediaCard>)

data class HomeFeed(val hero: List<MediaCard>, val sections: List<HomeSection>)

interface HomeDataSource {
    suspend fun load(): HomeFeed
}

interface DetailLauncher {
    fun open(context: Context, card: MediaCard)
}
```

- [ ] **Step 4: 实现 mapper（纯函数）**

要求：

- `fromVods(vods: List<Vod>): List<MediaCard>`：`title = name.ifBlank { "未命名" }`，`subtitle = remarks`，`id = id`，`posterUrl = pic`
- `fromHistory(items: List<History>, limit: Int = 10): List<MediaCard>`：`key = siteKey`，`id = vodId`，`title = vodName.ifBlank { "未命名" }`，`subtitle = vodRemarks`，`posterUrl = vodPic`，`progress = position/duration`（duration<=0 时为 null）
- `fromKeeps(items: List<Keep>, limit: Int = 10): List<MediaCard>`：Keep 的 `key` 形如 `siteKey@@@vodId`，按 `@@@` 拆分得到 `key`/`id`；`title = vodName`，`subtitle = siteName`，`posterUrl = vodPic`
- 所有空字段按 Review Focus 回退

- [ ] **Step 5: 实现 `AppHomeDataSource` 与 `AppDetailLauncher`**

`AppHomeDataSource` 要求：

```kotlin
class AppHomeDataSource : HomeDataSource {
    override suspend fun load(): HomeFeed = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        val site = VodConfig.get().getHome()
        if (site == null || site.key.isNullOrBlank()) return@withContext HomeFeed(emptyList(), emptyList())
        val result = SiteApi.homeContent(site)
        val vods = HomeFeedMapper.fromVods(result.list)
        val sections = buildList {
            HomeFeedMapper.fromHistory(History.get()).takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("继续观看", it))
            }
            HomeFeedMapper.fromKeeps(Keep.getVod()).takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("收藏", it))
            }
            if (vods.isNotEmpty()) add(HomeSection("推荐", vods))
        }
        HomeFeed(hero = vods.take(5), sections = sections)
    }
}
```

`AppDetailLauncher`：

```kotlin
class AppDetailLauncher : DetailLauncher {
    override fun open(context: Context, card: MediaCard) {
        if (context !is Activity) return
        VideoActivity.start(context, card.key, card.id, card.title, card.posterUrl)
    }
}
```

- [ ] **Step 6: 运行单测确认通过**

Run: `.\gradlew.bat :app:testMobileDebugUnitTest --tests '*HomeFeedMapperTest' --console=plain`
Expected: `BUILD SUCCESSFUL`，1 test passed

- [ ] **Step 7: 提交**

```powershell
git add compose/src/main/java/com/onefilm/android/tv/compose/bridge app/src/main/java/com/onefilm/android/tv/composebridge app/src/test/java/com/onefilm/android/tv/composebridge
git commit -m "feat(compose): add home bridge and app data source"
```

---

### Task 5: Compose 宿主与自适应导航

**Files:**
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/navigation/Route.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/navigation/OneFilmNavHost.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/navigation/AdaptiveNavigation.kt`
- Create: `app/src/main/java/com/onefilm/android/tv/composehost/ComposeMainActivity.kt`
- Create: `app/src/debug/java/com/onefilm/android/tv/debug/ComposeMainSmokeActivity.kt`（可选冒烟入口）
- Create: `compose/src/androidTest/java/com/onefilm/android/tv/compose/navigation/NavigationTest.kt`

**Interfaces:**
- Consumes: `HomeScreen`（Task 6 产出，本任务先用占位 `HomePlaceholder`）
- Produces:
  - `object Routes { const val HOME = "home"; const val SEARCH = "search"; const val LIBRARY = "library"; const val SETTINGS = "settings" }`
  - `@Composable fun OneFilmNavHost(navController: NavHostController, homeContent: @Composable () -> Unit)`
  - `@Composable fun AdaptiveNavigation(isTv: Boolean, currentRoute: String?, onNavigate: (String) -> Unit, content: @Composable () -> Unit)`

- [ ] **Step 1: 写导航测试（先失败）**

```kotlin
@Test fun bottomNavigation_switchesRoute() {
    compose.setContent {
        OneFilmTheme {
            val nav = rememberNavController()
            AdaptiveNavigation(isTv = false, currentRoute = Routes.HOME, onNavigate = { nav.navigate(it) }) {
                Text("CONTENT")
            }
        }
    }
    compose.onNodeWithText("CONTENT").assertIsDisplayed()
    compose.onNodeWithText("搜索").performClick()
    // 断言回调被触发由调用方验证；此处至少验证 tab 可点击
}
```

- [ ] **Step 2: 实现路由与导航**

要求：

- `Routes` 使用字符串常量（Navigation 2.7.7，不做 type-safe routes）
- `AdaptiveNavigation`：
  - `isTv=true`：顶部 `NavigationBar`/`TabRow` 风格，焦点可见
  - `isTv=false`：底部 `NavigationBar`，4 个 Tab（首页/搜索/收藏/设置）
  - 选中态使用青绿
- `OneFilmNavHost` 注册 `home/search/library/settings`，非首页先显示占位文案

- [ ] **Step 3: 实现 `ComposeMainActivity`**

要求：

- `ComponentActivity` + `setContent { OneFilmTheme { ... } }`
- 创建 `AppHomeDataSource` / `AppDetailLauncher`（Task 4）
- 使用 `rememberNavController()`，接 `OneFilmNavHost`
- 处理返回键：非首页先回首页，首页再退出
- 屏幕方向：手机跟随系统；TV 横屏

- [ ] **Step 4: 运行导航测试**

Run: `.\gradlew.bat :compose:connectedDebugAndroidTest --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 构建并安装**

Run:

```powershell
.\gradlew.bat :app:assembleMobileDebug --console=plain
adb install -r app\build\outputs\apk\mobile\debug\app-mobile-arm64-v8a-debug.apk
adb shell am start -n com.onefilm.android.tv.mobile/com.onefilm.android.tv.composehost.ComposeMainActivity
```

Expected: 进入 Compose 宿主，首页区域显示占位内容，底部 Tab 可点击

- [ ] **Step 6: 提交**

```powershell
git add compose/src/main/java/com/onefilm/android/tv/compose/navigation app/src/main/java/com/onefilm/android/tv/composehost compose/src/androidTest
git commit -m "feat(compose): add compose host and adaptive navigation"
```

---

### Task 6: 首页（ViewModel + 状态 + 真实数据）

**Files:**
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/feature/home/HomeUiState.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/feature/home/HomeViewModel.kt`
- Create: `compose/src/main/java/com/onefilm/android/tv/compose/feature/home/HomeScreen.kt`
- Create: `compose/src/test/java/com/onefilm/android/tv/compose/feature/home/HomeViewModelTest.kt`
- Create: `compose/src/androidTest/java/com/onefilm/android/tv/compose/feature/home/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `HomeDataSource`、`DetailLauncher`、`HomeFeed`
- Produces:
  - `sealed interface HomeUiState { data object Loading; data class Content(val feed: HomeFeed); data class Error(val message: String); data object Empty }`
  - `class HomeViewModel(private val dataSource: HomeDataSource) : ViewModel() { val state: StateFlow<HomeUiState>; fun retry() }`
  - `@Composable fun HomeScreen(state: HomeUiState, onRetry: () -> Unit, onCardClick: (MediaCard) -> Unit)`

- [ ] **Step 1: 写 ViewModel 单测（先失败）**

```kotlin
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun load_success_emitsContent() = runTest(dispatcher) {
        val vm = HomeViewModel(FakeHomeDataSource(feed))
        vm.retry()
        advanceUntilIdle()
        assertEquals(HomeUiState.Content(feed), vm.state.value)
    }

    @Test fun load_empty_emitsEmpty() = runTest(dispatcher) {
        val vm = HomeViewModel(FakeHomeDataSource(HomeFeed(emptyList(), emptyList())))
        vm.retry()
        advanceUntilIdle()
        assertEquals(HomeUiState.Empty, vm.state.value)
    }

    @Test fun load_failure_emitsError() = runTest(dispatcher) {
        val vm = HomeViewModel(FakeHomeDataSource(error = IOException("boom")))
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.state.value is HomeUiState.Error)
    }
}
```

- [ ] **Step 2: 运行单测确认失败**

Run: `.\gradlew.bat :compose:testDebugUnitTest --tests '*HomeViewModelTest' --console=plain`
Expected: 编译失败

- [ ] **Step 3: 实现状态与 ViewModel**

要求：

- `retry()` 使用 `viewModelScope.launch`
- `Loading` → `Content/Empty/Error`
- 错误信息固定为 `"加载失败，请重试"`（不直接暴露网络异常细节）
- 空 feed（hero 与 sections 都为空）→ `Empty`

- [ ] **Step 4: 实现 `HomeScreen`**

要求：

- Content：`HeroBanner`（hero 第一条，播放/详情都触发 `onCardClick`）+ 每个 `HomeSection` 一个 `SectionRow`
- `SectionRow` 接收 `PosterItem`，HomeScreen 负责 `MediaCard -> PosterItem` 映射（`key/id/title/posterUrl/subtitle`），点击后还原为 `MediaCard` 交给 `onCardClick`
- Loading：居中进度 + “正在加载”
- Empty：图标 + “暂无内容” + “重试”按钮
- Error：错误文案 + “重试”按钮
- 内容可滚动（`LazyColumn`），TV 顶部安全边距

- [ ] **Step 5: 写首页 UI 测试**

覆盖：

- Content 显示 hero 标题与 shelf 标题
- Empty 显示“暂无内容”与“重试”
- Error 点击“重试”触发回调

- [ ] **Step 6: 运行全部测试**

Run:

```
.\gradlew.bat :compose:testDebugUnitTest :compose:connectedDebugAndroidTest --console=plain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 提交**

```powershell
git add compose/src/main/java/com/onefilm/android/tv/compose/feature/home compose/src/test compose/src/androidTest
git commit -m "feat(compose): implement home screen with loading and error states"
```

---

### Task 7: 启动路由与回退开关

**Files:**
- Create: `app/src/main/java/com/onefilm/android/tv/ui/activity/EntryActivity.kt`
- Modify: `app/src/mobile/AndroidManifest.xml`
- Modify: `app/src/leanback/AndroidManifest.xml`
- Modify: `app/src/main/java/com/onefilm/android/tv/setting/PlayerSetting.java`（新增 `isComposeUi` / `putComposeUi`，默认 `true`）
- Modify: `app/src/main/java/com/onefilm/android/tv/composehost/ComposeMainActivity.kt`（接收返回键/主题参数）

**Interfaces:**
- Consumes: `ComposeMainActivity`、旧 `HomeActivity`
- Produces: 启动分发：`compose_ui` 默认 `true`

- [ ] **Step 1: 增加偏好开关**

`PlayerSetting` 增加：

```java
public static boolean isComposeUi() {
    return Prefers.getBoolean("compose_ui", true);
}

public static void putComposeUi(boolean enabled) {
    Prefers.put("compose_ui", enabled);
}
```

- [ ] **Step 2: 实现 `EntryActivity`**

要求：

- 无布局，`onCreate` 中根据开关 `startActivity` 后 `finish()`
- `compose_ui=true` → `ComposeMainActivity`
- `false` → 旧 `HomeActivity`
- 不要设置 `FLAG_ACTIVITY_NEW_TASK`，保持同任务栈

- [ ] **Step 3: 切换 LAUNCHER**

移动端/leanback manifest：

- 移除旧 `HomeActivity` 的 `MAIN/LAUNCHER` intent-filter
- 给 `EntryActivity` 添加 `MAIN/LAUNCHER`，`exported=true`
- 保留旧 `HomeActivity` 的 `exported=true`（供回退与调试）

- [ ] **Step 4: 构建两个 flavor**

Run:

```powershell
.\gradlew.bat :app:assembleMobileDebug :app:assembleLeanbackDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 真机验证默认新 UI**

```powershell
adb install -r app\build\outputs\apk\mobile\debug\app-mobile-arm64-v8a-debug.apk
adb shell am force-stop com.onefilm.android.tv.mobile
adb shell am start -n com.onefilm.android.tv.mobile/com.onefilm.android.tv.ui.activity.EntryActivity
```

Expected: 进入 `ComposeMainActivity`，首页显示真实数据或空/错误状态

- [ ] **Step 6: 验证回退**

把偏好 `compose_ui=false` 写入（`run-as` 修改 shared_prefs 或用临时设置入口），重启 App。

Expected: 进入旧 `HomeActivity`

- [ ] **Step 7: 提交**

```powershell
git add app/src/main/java/com/onefilm/android/tv/ui/activity/EntryActivity.kt app/src/mobile/AndroidManifest.xml app/src/leanback/AndroidManifest.xml app/src/main/java/com/onefilm/android/tv/setting/PlayerSetting.java
git commit -m "feat(compose): route launcher to compose ui with fallback"
```

---

### Task 8: M1 验收与文档

**Files:**
- Modify: `docs/phase2-regression.md`
- Create: `docs/compose-ui-m1.md`

**Interfaces:**
- Consumes: 全部 M1 产物
- Produces: 验收记录，供 M2 使用

- [ ] **Step 1: 手机回归**

验证并记录：

- 冷启动 → Compose 首页
- 真实配置加载（站点/首页数据）
- Hero 点击 → 旧详情页
- 返回 → 回到首页，位置保留
- 空配置/断网 → 空状态或错误态
- 深色/浅色切换（临时开关）文字对比度

- [ ] **Step 2: TV 回归**

在 TV 模拟器（API28 x86）安装 leanback APK：

- 首页首个焦点可见
- D-pad 左右切换 shelf、上下切换行
- 确认进入详情
- 返回不退出应用

> leanback x86 安装需要临时把 `x86` 加入 ABI 分裂；验证后还原，不要提交。

- [ ] **Step 3: 更新文档**

`docs/compose-ui-m1.md` 记录：

- 模块结构、入口、回退开关
- 已验证项与截图路径
- 已知问题（如 TV 焦点细节、毛玻璃降级）

`docs/phase2-regression.md` 增加：

```
| Compose M1：模块/主题/导航/首页 | 手机 + TV 模拟器 | ✅ |
```

- [ ] **Step 4: 最终构建**

Run:

```powershell
.\gradlew.bat :compose:testDebugUnitTest :app:assembleMobileDebug :app:assembleLeanbackDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```powershell
git add docs
git commit -m "docs: record compose m1 acceptance"
```

---

## 后续计划（不在本计划范围）

- M2：详情/选集/线路
- M3：播放器覆盖层、TV 侧板、手机抽屉、弹幕/字幕/音轨/偏移
- M4：搜索/收藏/历史/直播/设置
- M5：删除旧 UI、动效与性能打磨
