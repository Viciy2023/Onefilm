package com.onefilm.android.tv.bean;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.onefilm.android.tv.App;
import com.onefilm.android.tv.db.AppDatabase;
import com.github.catvod.utils.Prefers;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

@Entity(indices = @Index(value = {"url", "type"}, unique = true))
public class Config {

    private static final String TAG = "Config";

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    private int id;
    @SerializedName("type")
    private int type;
    @SerializedName("time")
    private long time;
    @SerializedName("url")
    private String url;
    @SerializedName("json")
    private String json;
    @SerializedName("name")
    private String name;
    @SerializedName("logo")
    private String logo;
    @SerializedName("home")
    private String home;
    @SerializedName("parse")
    private String parse;

    @Ignore
    @SerializedName("notice")
    private String notice;
    @Ignore
    @SerializedName("danmaku")
    private String danmaku;
    @Ignore
    @SerializedName("assrt")
    private String assrt;

    public static List<Config> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Config.class).getType();
        List<Config> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Config objectFrom(String str) {
        return App.gson().fromJson(str, Config.class);
    }

    public static Config create(int type) {
        return new Config().type(type);
    }

    public static Config create(int type, String url) {
        return new Config().type(type).url(url).insert();
    }

    public static Config create(int type, String url, String name) {
        return new Config().type(type).url(url).name(name).insert();
    }

    /** 只构造对象、不写数据库（多仓里的线路用这个，避免污染「配置」列表）。 */
    public static Config transientConfig(int type, String url, String name) {
        return new Config().type(type).url(url).name(name);
    }

    public static List<Config> getAll(int type) {
        return AppDatabase.get().getConfigDao().findByType(type);
    }

    public static List<Config> findUrls() {
        return AppDatabase.get().getConfigDao().findUrlByType(0);
    }

    public static void delete(String url) {
        AppDatabase.get().getConfigDao().delete(url);
        for (int type = 0; type <= 2; type++) clearPrefersIfMatch(url, type);
    }

    public static void delete(String url, int type) {
        AppDatabase.get().getConfigDao().delete(url, type);
        clearPrefersIfMatch(url, type);
    }

    /** 删除配置后同步清掉 Prefers 里的兜底备份，否则下次启动会被「恢复」回来。 */
    private static void clearPrefersIfMatch(String url, int type) {
        try {
            if (TextUtils.isEmpty(url)) return;
            if (url.equals(Prefers.getString("config_" + type, ""))) Prefers.put("config_" + type, "");
        } catch (Throwable ignored) {
        }
    }

    public static Config vod() {
        return one(0);
    }

    public static Config live() {
        return one(1);
    }

    public static Config wall() {
        return one(2);
    }

    private static Config one(int type) {
        Config item = AppDatabase.get().getConfigDao().findOne(type);
        if (item != null && !item.isEmpty()) return item;
        Config restored = restoreFromPrefers(type);
        return restored != null ? restored : create(type);
    }

    /**
     * 数据库被清空（Room 销毁式迁移、导入备份中途失败等）后的兜底恢复。
     *
     * <p>{@link #update()} 每次保存配置都会把 URL 写进 Prefers，这里把它读回来
     * 重建一条配置记录，避免用户升级/崩溃后「接口自己没了」。
     */
    private static Config restoreFromPrefers(int type) {
        try {
            String url = Prefers.getString("config_" + type, "");
            if (TextUtils.isEmpty(url)) return null;
            Log.w(TAG, "restore config from prefers: type=" + type + " url=" + url);
            return create(type, url);
        } catch (Throwable e) {
            Log.w(TAG, "restore config failed: type=" + type, e);
            return null;
        }
    }

    public static Config find(int id) {
        return AppDatabase.get().getConfigDao().findById(id);
    }

    public static Config find(String url, int type) {
        Config item = AppDatabase.get().getConfigDao().find(url, type);
        return item == null ? create(type, url) : item.type(type);
    }

    public static Config find(String url, String name, int type) {
        Config item = AppDatabase.get().getConfigDao().find(url, type);
        return item == null ? create(type, url, name) : item.type(type).name(name);
    }

    public static Config find(Config config) {
        return find(config, config.getType());
    }

    public static Config find(Config config, int type) {
        Config item = AppDatabase.get().getConfigDao().find(config.getUrl(), type);
        return item == null ? create(type, config.getUrl(), config.getName()) : item.type(type).name(config.getName());
    }

    public static Config find(Depot depot, int type) {
        Config item = AppDatabase.get().getConfigDao().find(depot.getUrl(), type);
        return item == null ? create(type, depot.getUrl(), depot.getName()) : item.type(type).name(depot.getName());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getParse() {
        return parse;
    }

    public void setParse(String parse) {
        this.parse = parse;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }

    public String getDanmaku() {
        return danmaku;
    }

    public void setDanmaku(String danmaku) {
        this.danmaku = danmaku;
    }

    public String getAssrt() {
        return assrt;
    }

    public void setAssrt(String assrt) {
        this.assrt = assrt;
    }

    public Config type(int type) {
        setType(type);
        return this;
    }

    public Config url(String url) {
        setUrl(url);
        return this;
    }

    public Config json(String json) {
        setJson(json);
        return this;
    }

    public Config name(String name) {
        setName(name);
        return this;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public String getDesc() {
        if (!TextUtils.isEmpty(getName())) return getName();
        if (!TextUtils.isEmpty(getUrl())) return getUrl();
        return "";
    }

    public Config insert() {
        if (isEmpty()) return this;
        setId(Math.toIntExact(AppDatabase.get().getConfigDao().insert(this)));
        return this;
    }

    public Config save() {
        if (isEmpty()) return this;
        AppDatabase.get().getConfigDao().insertOrUpdate(this);
        return this;
    }

    public Config update() {
        if (isEmpty()) return this;
        setTime(System.currentTimeMillis());
        Prefers.put("config_" + getType(), getUrl());
        return save();
    }

    public void delete() {
        AppDatabase.get().getConfigDao().delete(getUrl(), getType());
        clearPrefersIfMatch(getUrl(), getType());
        History.delete(getId());
        Keep.delete(getId());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Config it)) return false;
        return getId() == it.getId();
    }
}
