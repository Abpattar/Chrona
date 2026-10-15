package com.chrona.app.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
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
 * Board — pinned notes joined by red threads.
 *
 * The canvas board and its accessible list alternative are P05. P01 reserves the
 * destination so navigation, theming and semantics are proven first.
 */
@Composable
fun BoardScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "board_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_board),
            subtitle = "Tasks and events as pinned notes, linked by typed relationships.",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        EmptyState(
            title = "The board is empty",
            message = "Pinned notes and their relationship threads appear here. " +
                "An accessible list alternative will always be available alongside the visual board.",
            icon = Icons.AutoMirrored.Outlined.ViewQuilt,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BoardScreenPreview() {
    ChronaTheme { Surface { BoardScreen() } }
}