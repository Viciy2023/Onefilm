package com.onefilm.android.tv;

import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.onefilm.android.tv.impl.UpdateListener;
import com.onefilm.android.tv.setting.Setting;
import com.onefilm.android.tv.ui.dialog.UpdateDialog;
import com.onefilm.android.tv.utils.Download;
import com.onefilm.android.tv.utils.FileUtil;
import com.onefilm.android.tv.utils.Notify;
import com.onefilm.android.tv.utils.ResUtil;
import com.onefilm.android.tv.utils.Task;
import com.onefilm.android.tv.utils.UpdateManifest;
import com.github.catvod.utils.Path;

import java.io.File;

public class Updater implements Download.Callback, UpdateListener {

    private Download download;
    private UpdateDialog dialog;
    private String downloadUrl;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
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
            UpdateManifest.Info info = UpdateManifest.fetch();
            if (!UpdateManifest.isNewer(info)) return;
            downloadUrl = info.url;
            App.post(() -> show(activity, info.version, info.notes));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        dialog = UpdateDialog.create()
                .title(ResUtil.getString(R.string.update_version, version))
                .desc(desc)
                .listener(this)
                .show(activity);
    }

    @Override
    public void onConfirm(View view) {
        if (downloadUrl == null || downloadUrl.isEmpty()) return;
        view.setEnabled(false);
        download = Download.create(downloadUrl, getFile());
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
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
