package com.chrona.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.chrona.app.R
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.today.ScreenHeader

/**
 * History — completed, postponed, skipped and moved work, plus schedule
 * revisions and guarded undo. Content arrives in P02/P04.
 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "history_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_history),
            subtitle = "Completed, postponed and moved work, with reasons.",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        EmptyState(
            title = "Nothing recorded yet",
            message = "Completed, postponed, skipped and rescheduled work will be listed here " +
                "with a plain-language reason for each change.",
            icon = Icons.Outlined.History,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    ChronaTheme { Surface { HistoryScreen() } }
}