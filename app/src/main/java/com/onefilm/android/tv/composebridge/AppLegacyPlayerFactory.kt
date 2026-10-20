package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.compose.bridge.LegacyPlayer
import com.onefilm.android.tv.compose.bridge.LegacyPlayerFactory

class AppLegacyPlayerFactory : LegacyPlayerFactory {
    override fun create(): LegacyPlayer = AppLegacyPlayer()
}
