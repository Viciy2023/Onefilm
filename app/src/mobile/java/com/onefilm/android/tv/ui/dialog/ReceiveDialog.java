package com.onefilm.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.onefilm.android.tv.api.config.VodConfig;
import com.onefilm.android.tv.bean.History;
import com.onefilm.android.tv.databinding.DialogReceiveBinding;
import com.onefilm.android.tv.event.CastEvent;
import com.onefilm.android.tv.impl.Callback;
import com.onefilm.android.tv.ui.activity.VideoActivity;
import com.onefilm.android.tv.utils.ImgUtil;
import com.onefilm.android.tv.utils.Notify;

public class ReceiveDialog extends BaseBottomSheetDialog {

    private DialogReceiveBinding binding;
    private CastEvent event;

    public static ReceiveDialog create() {
        return new ReceiveDialog();
    }

    public ReceiveDialog event(CastEvent event) {
        this.event = event;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof ReceiveDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        for (Fragment f : fragment.getChildFragmentManager().getFragments()) if (f instanceof ReceiveDialog) return;
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogReceiveBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        History item = event.history();
        binding.name.setText(item.getVodName());
        binding.from.setText(event.device().getName());
        ImgUtil.load(item.getVodName(), item.getVodPic(), binding.image);
    }

    @Override
    protected void initEvent() {
        binding.frame.setOnClickListener(v -> onReceiveCast());
    }

    private void showProgress() {
        binding.frame.setEnabled(false);
        binding.play.setVisibility(View.GONE);
        binding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        binding.frame.setEnabled(true);
        binding.play.setVisibility(View.VISIBLE);
        binding.progress.getRoot().setVisibility(View.GONE);
    }

    private void onReceiveCast() {
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(requireActivity(), event.history());
            dismiss();
        } else {
            showProgress();
            VodConfig.load(event.config(), getCallback());
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                onReceiveCast();
                hideProgress();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                hideProgress();
            }
        };
    }
}
