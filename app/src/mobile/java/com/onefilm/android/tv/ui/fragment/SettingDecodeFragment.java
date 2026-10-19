package com.onefilm.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.DolbyVisionOutputPolicy;
import androidx.viewbinding.ViewBinding;

import com.onefilm.android.tv.R;
import com.onefilm.android.tv.databinding.FragmentSettingDecodeBinding;
import com.onefilm.android.tv.setting.DecodeSetting;
import com.onefilm.android.tv.setting.PlayerSetting;
import com.onefilm.android.tv.setting.Setting;
import com.onefilm.android.tv.ui.base.BaseFragment;
import com.onefilm.android.tv.utils.ResUtil;

public class SettingDecodeFragment extends BaseFragment {

    private FragmentSettingDecodeBinding mBinding;

    public static SettingDecodeFragment newInstance() {
        return new SettingDecodeFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingDecodeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setVisible();
        refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.aac.setOnClickListener(this::setAAC);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.audioPrefer.setOnClickListener(this::setAudioPrefer);
        mBinding.videoPrefer.setOnClickListener(this::setVideoPrefer);
        mBinding.dolbyVisionOutput.setOnClickListener(this::setDolbyVisionOutput);
        mBinding.audioPassThrough.setOnClickListener(this::setAudioPassThrough);
    }

    private void setVisible() {
        boolean exo = PlayerSetting.isExo();
        mBinding.aac.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.tunnel.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.audioPrefer.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.videoPrefer.setVisibility(exo ? View.VISIBLE : View.GONE);
    }

    private void refresh() {
        mBinding.aacText.setText(Setting.getSwitch(DecodeSetting.isPreferAAC()));
        mBinding.tunnelText.setText(Setting.getSwitch(DecodeSetting.isTunnel()));
        mBinding.audioPreferText.setText(Setting.getSwitch(DecodeSetting.isAudioPrefer()));
        mBinding.videoPreferText.setText(Setting.getSwitch(DecodeSetting.isVideoPrefer()));
        mBinding.dolbyVisionOutputText.setText(ResUtil.getStringArray(R.array.select_dolby_vision_output)[DecodeSetting.getDolbyVisionOutputPolicy()]);
        mBinding.audioPassThroughText.setText(Setting.getSwitch(DecodeSetting.isAudioPassThrough()));
    }

    private void setTunnel(View view) {
        if (PlayerSetting.isMpv()) return;
        DecodeSetting.putTunnel(!DecodeSetting.isTunnel());
        mBinding.tunnelText.setText(Setting.getSwitch(DecodeSetting.isTunnel()));
    }

    private void setAudioPassThrough(View view) {
        DecodeSetting.putAudioPassThrough(!DecodeSetting.isAudioPassThrough());
        mBinding.audioPassThroughText.setText(Setting.getSwitch(DecodeSetting.isAudioPassThrough()));
    }

    private void setAudioPrefer(View view) {
        DecodeSetting.putAudioPrefer(!DecodeSetting.isAudioPrefer());
        mBinding.audioPreferText.setText(Setting.getSwitch(DecodeSetting.isAudioPrefer()));
    }

    private void setVideoPrefer(View view) {
        DecodeSetting.putVideoPrefer(!DecodeSetting.isVideoPrefer());
        mBinding.videoPreferText.setText(Setting.getSwitch(DecodeSetting.isVideoPrefer()));
    }

    private void setDolbyVisionOutput(View view) {
        int mode = (DecodeSetting.getDolbyVisionOutputPolicy() + 1) % (DolbyVisionOutputPolicy.ASSUME_UNSUPPORTED + 1);
        DecodeSetting.putDolbyVisionOutputPolicy(mode);
        mBinding.dolbyVisionOutputText.setText(ResUtil.getStringArray(R.array.select_dolby_vision_output)[mode]);
    }

    private void setAAC(View view) {
        DecodeSetting.putPreferAAC(!DecodeSetting.isPreferAAC());
        mBinding.aacText.setText(Setting.getSwitch(DecodeSetting.isPreferAAC()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}
