package com.onefilm.android.tv.composebridge

import androidx.media3.common.DolbyVisionOutputPolicy
import com.onefilm.android.tv.App
import com.onefilm.android.tv.api.config.LiveConfig
import com.onefilm.android.tv.api.config.WallConfig
import com.onefilm.android.tv.bean.Config
import com.onefilm.android.tv.impl.Callback
import com.github.catvod.bean.Doh
import com.onefilm.android.tv.BuildConfig
import com.onefilm.android.tv.event.ServerEvent
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import com.onefilm.android.tv.compose.bridge.SettingsSnapshot
import com.onefilm.android.tv.compose.bridge.ServerEventBridge
import com.onefilm.android.tv.compose.bridge.HomeFeedCache
import com.onefilm.android.tv.compose.bridge.DetailCache
import com.onefilm.android.tv.compose.bridge.ConfigLine
import com.onefilm.android.tv.setting.DanmakuSetting
import com.onefilm.android.tv.setting.DecodeSetting
import com.onefilm.android.tv.setting.PlayerSetting
import com.onefilm.android.tv.setting.PreloadSetting
import com.onefilm.android.tv.setting.Setting
import com.onefilm.android.tv.setting.SubtitleSetting
import com.onefilm.android.tv.utils.FileUtil
import com.github.catvod.utils.Path
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class AppSettingsDataSource : SettingsDataSource {

    init {
        EventBus.getDefault().register(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServerEvent(event: ServerEvent) {
        if (event.type() == ServerEvent.Type.SETTING) {
            ServerEventBridge.pushSetting(event.text(), event.name())
        }
    }

    private var wallLoading = false

    private val subtitleColors = listOf(
        "白" to SubtitleSetting.SUBTITLE_COLOR_WHITE,
        "黄" to SubtitleSetting.SUBTITLE_COLOR_YELLOW,
        "青" to SubtitleSetting.SUBTITLE_COLOR_CYAN,
        "绿" to SubtitleSetting.SUBTITLE_COLOR_GREEN,
    )

    override fun load(): SettingsSnapshot {
        ensureWallpaperLoadedIfNeeded()
        return SettingsSnapshot(
        engineMpv = PlayerSetting.isMpv(),
        background = PlayerSetting.getBackground(),
        bufferSeconds = PlayerSetting.getBuffer(),
        playTimeoutSeconds = PlayerSetting.getPlayTimeout(),
        renderTexture = PlayerSetting.getRender() == PlayerSetting.RENDER_TEXTURE,
        tunnel = DecodeSetting.isTunnel(),
        audioPassThrough = DecodeSetting.isAudioPassThrough(),
        audioPrefer = DecodeSetting.isAudioPrefer(),
        videoPrefer = DecodeSetting.isVideoPrefer(),
        dvPolicyLabel = dolbyLabel(DecodeSetting.getDolbyVisionOutputPolicy()),
        subtitleStyle = SubtitleSetting.getStyleMode(),
        subtitleScale = SubtitleSetting.getScale(),
        subtitlePosition = SubtitleSetting.getPosition(),
        subtitleColorLabel = subtitleColors.firstOrNull {
            it.second == SubtitleSetting.getTextBaseColor()
        }?.first ?: "自定义",
        subtitleEdgeWidth = SubtitleSetting.getEdgeWidth(),
        danmakuBlockWords = com.github.catvod.utils.Prefers.getString("compose_danmaku_block_words"),
        danmakuShow = DanmakuSetting.isShow(),
        danmakuScale = DanmakuSetting.getTextScale(),
        danmakuTransparency = DanmakuSetting.getTransparency(),
        danmakuDurationMs = DanmakuSetting.getDurationMs(),
        danmakuLines = DanmakuSetting.getMaxScrollLines(),
        preloadEnabled = PreloadSetting.isEnabled(),
        preloadNext = PreloadSetting.isNextEpisodeEnabled(),
        preloadThreads = PreloadSetting.getThreads(),
        preloadSizeMb = PreloadSetting.getSizeMb(),
        ua = Setting.getUa(),
        version = BuildConfig.VERSION_NAME,
        configUrl = VodConfig.getUrl().orEmpty(),
        liveConfigUrl = LiveConfig.getUrl().orEmpty(),
        wallConfigUrl = WallConfig.getUrl().orEmpty(),
        incognito = Setting.isIncognito(),
        dohLabel = runCatching { Doh.objectFrom(Setting.getDoh()).getName() }.getOrNull().orEmpty(),
        themeMode = com.github.catvod.utils.Prefers.getInt("compose_theme_mode", 0),
        cacheDays = Setting.getHistoryDays(),
        cacheSizeLabel = FileUtil.byteCountToDisplaySize(Path.size(Path.cache())),
        wallpaperLabel = when (Setting.getWall()) {
            0 -> "无"
            5 -> "网络壁纸"
            else -> "纯色 ${Setting.getWall()}"
        },
        forceUpdate = Setting.getForceUpdate(),
        )
    }

    override fun setEngine(mpv: Boolean) {
        PlayerSetting.putEngine(if (mpv) PlayerSetting.ENGINE_MPV else PlayerSetting.ENGINE_EXO)
    }

    override fun setBackground(mode: Int) {
        PlayerSetting.putBackground(mode)
    }

    override fun setBufferSeconds(seconds: Int) {
        PlayerSetting.putBuffer(seconds)
    }

    override fun setRenderTexture(texture: Boolean) {
        PlayerSetting.putRender(if (texture) PlayerSetting.RENDER_TEXTURE else PlayerSetting.RENDER_SURFACE)
    }

    override fun setTunnel(enabled: Boolean) {
        DecodeSetting.putTunnel(enabled)
    }

    override fun setAudioPassThrough(enabled: Boolean) {
        DecodeSetting.putAudioPassThrough(enabled)
    }

    override fun setAudioPrefer(enabled: Boolean) {
        DecodeSetting.putAudioPrefer(enabled)
    }

    override fun setVideoPrefer(enabled: Boolean) {
        DecodeSetting.putVideoPrefer(enabled)
    }

    override fun cycleDolbyVisionPolicy() {
        val next = when (DecodeSetting.getDolbyVisionOutputPolicy()) {
            DolbyVisionOutputPolicy.AUTO -> DolbyVisionOutputPolicy.ASSUME_SUPPORTED
            DolbyVisionOutputPolicy.ASSUME_SUPPORTED -> DolbyVisionOutputPolicy.ASSUME_UNSUPPORTED
            else -> DolbyVisionOutputPolicy.AUTO
        }
        DecodeSetting.putDolbyVisionOutputPolicy(next)
    }

    override fun setSubtitleStyle(style: Int) {
        SubtitleSetting.putStyleMode(style)
    }

    override fun setSubtitleScale(scale: Float) {
        SubtitleSetting.putScale(scale)
    }

    override fun setSubtitlePosition(position: Float) {
        SubtitleSetting.putPosition(position)
    }

    override fun setSubtitleEdgeWidth(width: Float) {
        SubtitleSetting.putEdgeWidth(width)
    }

    override fun setDanmakuBlockWords(words: String) {
        com.github.catvod.utils.Prefers.put("compose_danmaku_block_words", words)
    }

    override fun cycleSubtitleColor() {
        val current = SubtitleSetting.getTextBaseColor()
        val index = subtitleColors.indexOfFirst { it.second == current }
        val next = subtitleColors[(index + 1).takeIf { it >= 0 }?.mod(subtitleColors.size) ?: 0]
        SubtitleSetting.putTextColor(next.second)
    }

    override fun setDanmakuShow(show: Boolean) {
        DanmakuSetting.putShow(show)
    }

    override fun setDanmakuScale(scale: Float) {
        DanmakuSetting.putTextScale(scale)
    }

    override fun setDanmakuTransparency(value: Float) {
        DanmakuSetting.putTransparency(value)
    }

    override fun setDanmakuDuration(ms: Long) {
        DanmakuSetting.putDurationMs(ms)
    }

    override fun setDanmakuLines(lines: Int) {
        DanmakuSetting.putMaxScrollLines(lines)
    }

    override fun setPreloadEnabled(enabled: Boolean) {
        PreloadSetting.putEnabled(enabled)
    }

    override fun setPreloadNext(enabled: Boolean) {
        PreloadSetting.putNextEpisodeEnabled(enabled)
    }

    override fun setPreloadThreads(threads: Int) {
        PreloadSetting.putThreads(threads)
    }

    override fun setPreloadSizeMb(sizeMb: Int) {
        PreloadSetting.putSizeMb(sizeMb)
    }

    override fun resetUa() {
        Setting.putUa("")
    }

    override fun loadConfig(
        type: Int,
        url: String,
        name: String,
        onResult: (Boolean, String) -> Unit,
    ) {
        val config = Config.find(url, type)
        if (name.isNotBlank()) config.setName(name)
        val callback = object : Callback() {
            override fun success() {
                config.update()
                if (type == 1) {
                    com.github.catvod.utils.Prefers.put("compose_live_url", url)
                }
                if (type == 2) {
                    Setting.putWall(5)
                    Setting.putWallType(Setting.getExternalWallType())
                }
                App.post { onResult(true, "") }
            }

            override fun error(msg: String) {
                App.post { onResult(false, msg) }
            }
        }
        when (type) {
            0 -> VodConfig.load(config, callback)
            1 -> LiveConfig.load(config, callback)
            else -> WallConfig.load(config, callback)
        }
    }

    override fun configLines(type: Int): List<ConfigLine> {
        val currentUrl = when (type) {
            0 -> VodConfig.getUrl()
            1 -> LiveConfig.getUrl()
            else -> WallConfig.getUrl()
        }.orEmpty()
        return Config.getAll(type).map { config ->
            ConfigLine(
                name = config.desc.orEmpty().ifBlank { config.url.orEmpty() },
                url = config.url.orEmpty(),
                current = config.url.orEmpty() == currentUrl,
                type = type,
            )
        }
    }

    override fun allConfigLines(): List<ConfigLine> =
        listOf(0, 1).flatMap { configLines(it) }

    override fun deleteConfigLine(type: Int, url: String) {
        val activeUrl = when (type) {
            0 -> VodConfig.getUrl()
            1 -> LiveConfig.getUrl()
            else -> WallConfig.getUrl()
        }.orEmpty()
        Config.delete(url, type)
        if (activeUrl == url || Config.getAll(type).isEmpty()) clearActiveConfig(type)
    }

    private fun clearActiveConfig(type: Int) {
        when (type) {
            0 -> {
                VodConfig.get().clear().config(Config.create(0))
                com.github.catvod.utils.Prefers.put("config_0", "")
            }
            1 -> {
                LiveConfig.get().clear().config(Config.create(1))
                com.github.catvod.utils.Prefers.put("config_1", "")
            }
            else -> {
                WallConfig.get().config(Config.create(2))
                com.github.catvod.utils.Prefers.put("config_2", "")
            }
        }
    }

    override fun cycleDoh() {
        val items = VodConfig.get().doh
        if (items.isEmpty()) return
        val current = items.indexOf(Doh.objectFrom(Setting.getDoh()))
        val next = items[(current + 1).takeIf { it >= 0 }?.mod(items.size) ?: 0]
        Setting.putDoh(next.toString())
    }

    override fun cyclePlayTimeout() {
        val values = listOf(5, 10, 15, 20, 30, 45, 60)
        val current = PlayerSetting.getPlayTimeout()
        PlayerSetting.putPlayTimeout(values[(values.indexOf(current).takeIf { it >= 0 } ?: 2).let { (it + 1) % values.size }])
    }

    override fun setIncognito(enabled: Boolean) {
        Setting.putIncognito(enabled)
    }

    override fun setForceUpdate(enabled: Boolean) {
        Setting.putForceUpdate(enabled)
    }

    override fun setThemeMode(mode: Int) {
        com.github.catvod.utils.Prefers.put("compose_theme_mode", mode.coerceIn(0, 2))
    }

    override fun configPushUrl(): String = runCatching {
        com.onefilm.android.tv.server.Server.get().getAddress(4)
    }.getOrDefault("")

    override fun qrBitmap(content: String, size: Int): android.graphics.Bitmap? = runCatching {
        val hints = hashMapOf<com.google.zxing.EncodeHintType, Any>(
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
            com.google.zxing.EncodeHintType.MARGIN to 1,
        )
        val matrix = com.google.zxing.MultiFormatWriter().encode(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            size,
            size,
            hints,
        )
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    }.getOrNull()

    override fun setCacheDays(days: Int) {
        Setting.putHistoryDays(days)
    }

    override fun clearCache() {
        Path.clear(Path.cache())
        HomeFeedCache.feed = null
        DetailCache.clear()
        SearchResultCache.items = emptyList()
        runCatching {
            Path.files().listFiles { _, name -> name.startsWith("vod_config_") }
                ?.forEach { it.delete() }
        }
    }

    private fun ensureWallpaperLoadedIfNeeded() {
        if (Setting.getWall() != 5) return
        ensureWallpaperLoaded()
    }

    private fun ensureWallpaperLoaded() {
        if (wallLoading || Path.wall(0).exists()) return
        wallLoading = true
        WallConfig.load(
            Config.create(2).url(WallConfig.getUrl()),
            object : Callback() {
                override fun success() {
                    wallLoading = false
                    Setting.putWallType(Setting.getExternalWallType())
                    com.onefilm.android.tv.event.ConfigEvent.wall()
                }

                override fun error(msg: String) {
                    wallLoading = false
                }
            },
        )
    }

    override fun cycleWallpaper() {
        val next = (Setting.getWall() + 1) % 6
        Setting.putWall(next)
        Setting.putWallType(
            if (next == 5) Setting.getExternalWallType() else 0,
        )
        if (next == 5) ensureWallpaperLoaded()
        com.onefilm.android.tv.event.ConfigEvent.wall()
    }

    private fun dolbyLabel(mode: Int): String = when (mode) {
        DolbyVisionOutputPolicy.ASSUME_SUPPORTED -> "强制支持"
        DolbyVisionOutputPolicy.ASSUME_UNSUPPORTED -> "强制不支持"
        else -> "自动"
    }
}
