package com.onefilm.android.tv.aisubtitle;

import android.content.Context;

/**
 * LITE 版功能桩：LITE（minSdk 21）不打包 sherpa-onnx，AI 字幕整体不可用。
 *
 * <p>之所以需要本桩：`ExoUtil` 与 `AppAiSubtitleBridge` 位于 `app/src/main/java`，
 * 会被 appcore-lite 复用编译；LITE 侧没有 `:aisubtitle` 模块依赖，
 * 用同包名桩替代即可让共享代码正常编译，且运行时功能自动关闭。
 */
public final class AiSubtitleEngine {

    public static final String DATASET = "mandarin-zipformer-ctc";
    public static void setLanguage(String code) { }
    public static String dataset() { return DATASET; }
    public static AsrCatalog selectedModel() { return AsrCatalog.ZH; }

    public interface CueListener {
        void onCue(String text, long startUs, long endUs, long lateUs, long lookaheadMs);
    }

    private static final AiSubtitleEngine INSTANCE = new AiSubtitleEngine();

    private AiSubtitleEngine() {
    }

    public static AiSubtitleEngine get() {
        return INSTANCE;
    }

    /** LITE 永远视为模型未就绪。 */
    public static boolean modelReady(Context context) {
        return false;
    }

    /** 与真实实现同签名，供设置页展示模型体积（LITE 下该目录不存在）。 */
    public static java.io.File modelDir(Context context) {
        java.io.File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        return new java.io.File(new java.io.File(base, "ai-models"), DATASET);
    }

    public boolean prepare(Context context) {
        return false;
    }

    public boolean isAvailable() {
        return false;
    }

    public void flush() {
    }

    public void feedPcm(byte[] data, int length, int sampleRate, int channelCount, boolean floatPcm) {
    }

    public void setCueListener(CueListener listener) {
    }

    /** LITE 桩：不提供播放位置（功能整体关闭时不会被调用）。 */
    public interface PositionProvider {
        long playerPositionMs();
    }

    public void setPositionProvider(PositionProvider provider) {
    }

    public void setConfiguredLookaheadMs(long ms) {
    }

    /** LITE 桩：开关提供者（功能整体关闭）。 */
    public interface EnabledProvider {
        boolean isEnabled();
    }

    public void setEnabledProvider(EnabledProvider provider) {
    }

    public void setAppContext(Context context) {
    }

    public void shutdown() {
    }

    public void onAudioPipelineConfigured() {
    }
}
