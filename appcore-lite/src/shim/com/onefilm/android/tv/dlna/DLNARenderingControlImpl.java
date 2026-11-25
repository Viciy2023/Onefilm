// LITE 鍏煎鍓湰锛堟簮鑷?app/src/leanback/java/com/onefilm/android/tv/dlna/DLNARenderingControlImpl.java锛夛細浠呬负 minSdk 21 澧炲姞 SDK_INT 瀹堝崼锛屽叾浣欓€昏緫涓?:app 瀹屽叏涓€鑷淬€俙r
// 涓嶈鐩存帴淇敼 :app 鐨勫搴旀枃浠舵潵銆屽悓姝ャ€嶆湰鏂囦欢锛涙敼 :app 鍚庨渶閲嶆柊鐢熸垚鏈壇鏈€俙r
package com.onefilm.android.tv.dlna;

import android.content.Context;
import android.media.AudioManager;

import org.jupnp.model.types.UnsignedIntegerFourBytes;
import org.jupnp.model.types.UnsignedIntegerTwoBytes;
import org.jupnp.support.model.Channel;
import org.jupnp.support.renderingcontrol.AbstractAudioRenderingControl;

public class DLNARenderingControlImpl extends AbstractAudioRenderingControl {

    private final Context context;

    public DLNARenderingControlImpl(Context context) {
        this.context = context;
    }

    private AudioManager getAudioManager() {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
        return new UnsignedIntegerFourBytes[]{getDefaultInstanceID()};
    }

    @Override
    public boolean getMute(UnsignedIntegerFourBytes instanceId, String channelName) {
        return android.os.Build.VERSION.SDK_INT >= 23 ? getAudioManager().isStreamMute(AudioManager.STREAM_MUSIC) : getAudioManager().getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
    }

    @Override
    public void setMute(UnsignedIntegerFourBytes instanceId, String channelName, boolean desiredMute) {
        getAudioManager().adjustStreamVolume(AudioManager.STREAM_MUSIC, desiredMute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
    }

    @Override
    public UnsignedIntegerTwoBytes getVolume(UnsignedIntegerFourBytes instanceId, String channelName) {
        AudioManager am = getAudioManager();
        int current = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return new UnsignedIntegerTwoBytes(max > 0 ? current * 100L / max : 100);
    }

    @Override
    public void setVolume(UnsignedIntegerFourBytes instanceId, String channelName, UnsignedIntegerTwoBytes desiredVolume) {
        AudioManager manager = getAudioManager();
        int max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volume = (int) (desiredVolume.getValue() * max / 100);
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
    }

    @Override
    protected Channel[] getCurrentChannels() {
        return new Channel[]{Channel.Master};
    }
}
