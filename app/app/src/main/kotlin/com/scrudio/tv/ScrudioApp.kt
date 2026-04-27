package com.scrudio.tv

import android.app.Application

/**
 * Process-wide singleton. Phase 1 keeps it intentionally empty; data layer
 * will hook into onCreate() in Phase 2 (e.g. OkHttp client, DataStore init).
 */
class ScrudioApp : Application()
