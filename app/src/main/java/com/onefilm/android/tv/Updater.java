package com.onefilm.android.tv;

import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.onefilm.android.tv.impl.UpdateListener;
import com.onefilm.android.tv.setting.Setting;
import com.onefilm.android.tv.ui.dialog.UpdateDialog;
import com.onefilm.android.tv.utils.Download;
import com.onefilm.android.tv.utils.FileUtil;
import com.onefilm.android.tv.utils.Github;
import com.onefilm.android.tv.utils.Notify;
import com.onefilm.android.tv.utils.ResUtil;
import com.onefilm.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;

public class Updater implements Download.Callback, UpdateListener {

    private final Download download;
    private UpdateDialog dialog;

    private Updater() {
        this.download = Download.create(getApk(), getFile());
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getJson() {
        return Github.getJson(BuildConfig.FLAVOR);
    }

    private String getApk() {
        return Github.getApk(BuildConfig.FLAVOR + "-" + (android.os.Process.is64Bit() ? "arm64_v8a" : "armeabi_v7a"));
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            JSONObject object = new JSONObject(OkHttp.string(getJson()));
            String name = object.optString("name");
            String desc = object.optString("desc");
            int code = object.optInt("code");
            if (code <= BuildConfig.VERSION_CODE) return;
            App.post(() -> show(activity, name, desc));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, version)).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        download.cancel();
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}
