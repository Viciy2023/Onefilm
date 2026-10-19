package com.onefilm.android.tv.runtime.mpv;

import android.view.Surface;

import android.view.Surface;

interface IMpvRuntime {
    void setSurface(in Surface surface);
    void open(String url);
    void play();
    void pause();
    void stop();
    void setDolbyVisionMode(int mode);
    int getVideoWidth();
    int getVideoHeight();
    void setAudioDelayMs(long delayMs);
    void setSubtitleDelayMs(long delayMs);
    int getAudioTrackCount();
    int getSubtitleTrackCount();
    int getAudioTrack();
    int getSubtitleTrack();
    void setAudioTrack(int id);
    void setSubtitleTrack(int id);
    String[] getAudioTrackDetails();
    String[] getSubtitleTrackDetails();
    void seekTo(long positionMs);
    long getPosition();
    long getDuration();
    void release();
}
