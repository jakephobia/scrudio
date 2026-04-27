package com.scrudio.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.scrudio.tv.ui.home.MainFragment

/**
 * Single-activity host for the Leanback BrowseSupportFragment.
 *
 * On Android TV the convention is one FragmentActivity per top-level screen;
 * navigation between screens is via startActivity, not a NavController, so the
 * Leanback transitions stay buttery on low-end hardware.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MainFragment())
                .commit()
        }
    }
}
