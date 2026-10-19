package com.onefilm.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.onefilm.android.tv.compose.host.ComposeMainActivity;
import com.onefilm.android.tv.setting.PlayerSetting;

/** OneFilm: launcher router between the Compose UI and the legacy XML UI. */
public class EntryActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, PlayerSetting.isComposeUi() ? ComposeMainActivity.class : HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
