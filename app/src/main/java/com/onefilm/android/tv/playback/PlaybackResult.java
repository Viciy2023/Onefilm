package com.onefilm.android.tv.playback;

import com.onefilm.android.tv.bean.Result;

public record PlaybackResult<T>(T request, Result result) {
}
