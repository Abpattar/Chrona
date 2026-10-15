package com.chrona.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chrona.app.R
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.design.PinnedNoteCard
import com.chrona.app.ui.today.ScreenHeader

/**
 * Settings — reminder style/cap/quiet hours, permissions, data export/delete and
 * reduced motion. Preferences are a P02/P06/P07 deliverable; P01 shows only the
 * architecture-relevant promises so the surface is not a dead end.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "settings_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_settings),
            subtitle = "Reminder style, quiet hours, permissions and your local data.",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        EmptyState(
            title = "Nothing to configure yet",
            message = "Reminder style, quiet hours, voice processing notice and the " +
                "Privacy Centre (data export and delete) are added in later phases.",
            icon = Icons.Outlined.Settings,
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PinnedNoteCard(modifier = Modifier.semantics { testTag = "settings_local_first_note" }) {
                Text(
                    text = "Local-first",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Chrona stores everything on this device. No account, no cloud sync, " +
                        "and no remote AI are required for the core planner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ChronaTheme { Surface { SettingsScreen() } }
}