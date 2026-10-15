package com.chrona.app

import android.app.Application

/**
 * Application entry point.
 *
 * P01 ships no composition root: there is nothing to wire until P02 introduces
 * Room, repositories and use cases. `AppContainer` is added in P02 at the point
 * those collaborators exist rather than as empty scaffolding now.
 */
class ChronaApplication : Application()