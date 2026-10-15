package com.chrona.app

import android.app.Application
import com.chrona.app.di.AppContainer

/**
 * Application entry point and owner of the composition root.
 *
 * The container is created lazily so cold start does no database work until a screen actually
 * needs data. Nothing is scheduled at startup: Chrona is local-first and has no background
 * work until P06 introduces reminder reconciliation.
 */
class ChronaApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}