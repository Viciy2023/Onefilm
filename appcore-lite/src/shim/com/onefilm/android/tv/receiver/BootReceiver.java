// LITE 鍏煎鍓湰锛堟簮鑷?app/src/leanback/java/com/onefilm/android/tv/receiver/BootReceiver.java锛夛細浠呬负 minSdk 21 澧炲姞 SDK_INT 瀹堝崼锛屽叾浣欓€昏緫涓?:app 瀹屽叏涓€鑷淬€俙r
// 涓嶈鐩存帴淇敼 :app 鐨勫搴旀枃浠舵潵銆屽悓姝ャ€嶆湰鏂囦欢锛涙敼 :app 鍚庨渶閲嶆柊鐢熸垚鏈壇鏈€俙r
package com.onefilm.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.NonNull;

import com.onefilm.android.tv.App;
import com.onefilm.android.tv.api.config.LiveConfig;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !isBootAction(intent.getAction())) return;
        registerCallback();
    }

    private boolean isBootAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action);
    }

    private void registerCallback() {
        ConnectivityManager cm = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(new Callback());
        else cm.registerNetworkCallback(new android.net.NetworkRequest.Builder().build(), new Callback());
    }

    static class Callback extends ConnectivityManager.NetworkCallback {

        @Override
        public void onAvailable(@NonNull Network network) {
            doJob();
        }

        @Override
        public void onLost(@NonNull Network network) {
        }

        private void doJob() {
            LiveConfig.get().init().load();
            ((ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(this);
        }
    }
}
