package com.onefilm.android.tv.aisubtitle;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * LITE 功能桩：LITE 不打包 sherpa-onnx，也不加载任何 ASR 模型。
 *
 * <p>保留与真实 {@code AsrCatalog} 相同的公开成员，供共享源集里的
 * {@code AppAiSubtitleBridge} 编译通过；LITE 的 aiSubtitle bridge 为 null，
 * 这些方法实际不会被调用。
 */
public enum AsrCatalog {

    ZH("zh", "普通话", "mandarin-zipformer-ctc", Kind.ZIPFORMER_CTC, "zipformer2_ctc",
            "model.int8.onnx", "tokens.txt");

    public enum Kind {
        ZIPFORMER_CTC,
        TRANSDUCER,
        TONE_CTC,
        WENET_CTC,
        MOONSHINE;

        public boolean isOffline() {
            return this == WENET_CTC || this == MOONSHINE;
        }
    }

    public final String code;
    public final String label;
    public final String dataset;
    public final Kind kind;
    public final String modelType;

    AsrCatalog(String code, String label, String dataset, Kind kind, String modelType, String... files) {
        this.code = code;
        this.label = label;
        this.dataset = dataset;
        this.kind = kind;
        this.modelType = modelType;
    }

    public static AsrCatalog fromCode(String code) {
        return ZH;
    }

    public static List<AsrCatalog> all() {
        return Collections.singletonList(ZH);
    }

    public boolean isInstalled(File dir) {
        return false;
    }
}
