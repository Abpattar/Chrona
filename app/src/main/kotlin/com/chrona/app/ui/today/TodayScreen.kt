package com.chrona.app.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PostAdd
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

/**
 * Today — "What should I do next?".
 *
 * P01 establishes the surface, its meaning and its empty state. Task content is
 * delivered by P02; until then the screen must not pretend work exists.
 */
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "today_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_today),
            subtitle = stringResource(R.string.phase_banner),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        EmptyState(
            title = "Nothing queued yet",
            message = "Once tasks and fixed commitments are stored locally, " +
                "your next action and the rest of the day appear here.",
            icon = Icons.Outlined.PostAdd,
        )
    }
}

@Composable
internal fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    PinnedNoteCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenPreview() {
    ChronaTheme { Surface { TodayScreen() } }
}