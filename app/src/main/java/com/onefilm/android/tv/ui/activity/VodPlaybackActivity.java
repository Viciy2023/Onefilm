package com.onefilm.android.tv.ui.activity;

import android.content.Intent;
import android.text.TextUtils;

import com.onefilm.android.tv.api.config.VodConfig;
import com.onefilm.android.tv.bean.Site;
import com.onefilm.android.tv.db.AppDatabase;

import java.util.Objects;

/**
 * 点播播放页共享基类。
 *
 * <p>P2-2 阶段 3a：两端 VideoActivity 里与 Intent、站点、历史键相关的纯业务逻辑
 * 完全一致，集中到这里；布局、焦点、手势等 UI 仍留在各自 flavor。
 *
 * <p>单独一层而不是直接放进 {@link PlaybackActivity}：CastActivity 也继承
 * PlaybackActivity，且自带同名私有方法，直接上提会因可见性冲突编译失败。
 */
public abstract class VodPlaybackActivity extends PlaybackActivity {

    protected String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    protected String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    protected String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    protected String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    protected String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    protected boolean isSameVideo(Intent intent) {
        String key = Objects.toString(intent.getStringExtra("key"), "");
        String id = Objects.toString(intent.getStringExtra("id"), "");
        return TextUtils.equals(key, getKey()) && TextUtils.equals(id, getId());
    }

    protected Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    public String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    public String getVodKey() {
        return getKey();
    }

    public String getVodId() {
        return getId();
    }

    public String getVodPic() {
        return getPic();
    }

    public String getVodMark() {
        return getMark();
    }

    public boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    protected void checkId() {
        mVod.checkId();
    }

    public void finishVod() {
        finish();
    }
}
