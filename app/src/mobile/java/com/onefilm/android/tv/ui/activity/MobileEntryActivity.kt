package com.onefilm.android.tv.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Mobile-only launcher. TV keeps EntryActivity + ComposeMainActivity untouched. */
class MobileEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MobileComposeActivity::class.java))
        finish()
    }
}
