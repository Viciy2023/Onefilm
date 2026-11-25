package com.onefilm.android.tv.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.onefilm.android.tv.compose.bridge.ComposeDependencies

/** Mobile-only launcher. TV keeps EntryActivity + ComposeMainActivity untouched. */
class MobileEntryActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.github.catvod.utils.LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (val incoming = intent) {
            null -> startCompose()
            else -> when (incoming.action) {
                Intent.ACTION_SEARCH -> startCompose()
                Intent.ACTION_SEND, Intent.ACTION_VIEW -> {
                    ComposeDependencies.externalIntentHandler.handle(this, incoming)
                }
                else -> startCompose()
            }
        }
        finish()
    }

    private fun startCompose() {
        startActivity(Intent(this, MobileComposeActivity::class.java))
    }
}
