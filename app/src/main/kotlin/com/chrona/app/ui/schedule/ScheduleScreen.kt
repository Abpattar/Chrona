package com.chrona.app.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Surface
import com.chrona.app.R
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.today.ScreenHeader

/**
 * Schedule — day-first timeline.
 *
 * P02 adds stored fixed commitments and P04 adds generated placements and the
 * recovery entry point. P01 ships only the surface and an honest empty state.
 */
@Composable
fun ScheduleScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "schedule_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_schedule),
            subtitle = "Fixed commitments and planned blocks appear here.",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        EmptyState(
            title = "No day planned yet",
            message = "Manual fixed commitments and generated schedule blocks will be " +
                "listed here in time order, with deadlines kept distinct from preferred times.",
            icon = Icons.Outlined.CalendarMonth,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleScreenPreview() {
    ChronaTheme { Surface { ScheduleScreen() } }
}