package com.onefilm.android.tv.ui.activity;

import androidx.annotation.Nullable;

import com.onefilm.android.tv.App;
import com.onefilm.android.tv.Constant;
import com.onefilm.android.tv.api.config.LiveConfig;
import com.onefilm.android.tv.bean.Channel;
import com.onefilm.android.tv.bean.Group;
import com.onefilm.android.tv.bean.Live;
import com.onefilm.android.tv.model.LiveViewModel;
import com.onefilm.android.tv.playback.live.LivePlaybackController;
import com.onefilm.android.tv.ui.adapter.GroupAdapter;

import java.time.ZoneId;

/**
 * 直播播放页共享基类。
 *
 * <p>P2-2 阶段 2b：leanback / mobile 两份 LiveActivity 里有一批实现完全一致、
 * 且不直接操作 ViewBinding 的字段与方法，统一放到这里，避免同一 bug 修两遍。
 * 真正的 UI（绑定、焦点、手势）仍保留在各自 flavor 的 Activity 中。
 */
public abstract class LivePlaybackActivity extends PlaybackActivity {

    protected LiveViewModel mViewModel;
    protected LivePlaybackController mLive;
    protected GroupAdapter mGroupAdapter;
    protected Channel mChannel;
    protected String mPlaybackKey;
    protected Runnable mR1;
    protected Runnable mR3;
    protected int count;

    protected Group getKeep() {
        return mGroupAdapter.get(0);
    }

    public Group getGroup(int position) {
        return mGroupAdapter.get(position);
    }

    public int getGroupCount() {
        return mGroupAdapter.getItemCount();
    }

    protected Live getHome() {
        return LiveConfig.get().getHome();
    }

    protected boolean isEmpty() {
        return getIntent().getBooleanExtra("empty", true);
    }

    public ZoneId getZoneId() {
        return mViewModel.getZoneId();
    }

    protected void nextChannel() {
        mLive.nextChannel();
    }

    protected void prevChannel() {
        mLive.prevChannel();
    }

    protected void nextLine(boolean show) {
        mLive.nextLine(show);
    }

    protected void onLine() {
        nextLine(false);
    }

    protected void onPlay() {
        controller().play();
    }

    protected void onPaused() {
        controller().pause();
    }

    protected void checkPlay() {
        if (player().isPlaying()) onPaused();
        else onPlay();
    }

    protected void resetPass() {
        this.count = 0;
    }

    protected void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    protected void setR3Callback() {
        App.post(mR3, Constant.INTERVAL_HIDE);
    }

    protected void onError(String msg) {
        mLive.playbackError(msg);
    }

    protected void onReclaim() {
        mLive.refresh();
    }

    protected void onServiceConnected() {
        mLive.onPlaybackServiceReady();
    }

    protected void onXmlParsed(boolean success) {
        if (mChannel != null && success) mViewModel.getEpg(mChannel);
    }

    @Override
    protected String getPlaybackKey() {
        return mPlaybackKey;
    }

    public void restorePlaybackKey(@Nullable String key) {
        if (key == null || !player().hasPlaySpec() || !key.equals(player().getKey())) return;
        updateNavigationKey(mPlaybackKey = key);
    }
}
