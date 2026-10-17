package com.chrona.app.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrona.app.R
import com.chrona.app.di.AppContainer
import com.chrona.app.domain.model.ReminderStyle
import com.chrona.app.domain.model.UserPreferences
import com.chrona.app.platform.permission.ReminderPermissions
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.PinnedNoteCard
import com.chrona.app.ui.today.ScreenHeader
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Binds [SettingsViewModel] and the two permission flows to [SettingsScreen]. */
@Composable
fun SettingsRoute(container: AppContainer) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            loadPreferences = { container.preferencesRepository.load() },
            savePreferences = { container.preferencesRepository.save(it) },
            notificationsAvailable = { container.notificationPresenter.isAvailable() },
            overlayAvailable = { container.overlayPresenter.isAvailable() },
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }

    SettingsScreen(
        state = state,
        onStyleChange = viewModel::setStyle,
        onCapChange = viewModel::setCap,
        onQuietHoursChange = viewModel::setQuietHours,
        onOverlayToggle = { enabled ->
            viewModel.setOverlayEnabled(enabled)
            if (enabled) {
                // Special access: it can only be granted in system settings, so we send the user
                // there after they opt in, never before.
                overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri(),
                    ),
                )
            }
        },
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(ReminderPermissions.POST_NOTIFICATIONS)
            } else {
                viewModel.refresh()
            }
        },
    )
}

/**
 * Settings — reminder style, non-urgent cap, quiet hours, permissions and the local-first promise.
 *
 * Reminder style and the daily cap are the two NOTIFY-001 controls; the cap is explicitly framed as
 * an editable hypothesis, not a law (ADR-010/AT-021). The overlay is opt-in and its permission is
 * requested only when the user turns it on (ADR-009).
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onStyleChange: (ReminderStyle) -> Unit,
    onCapChange: (Int) -> Unit,
    onQuietHoursChange: (LocalTime?, LocalTime?) -> Unit,
    onOverlayToggle: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = "settings_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_settings),
            subtitle = "Reminder style, quiet hours, permissions and your local data.",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { testTag = "settings_message" },
                )
            }

            SectionTitle(stringResource(R.string.settings_reminders_title))

            PinnedNoteCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_style_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderStyle.entries.forEach { style ->
                        FilterChip(
                            selected = state.preferences.reminderStyle == style,
                            onClick = { onStyleChange(style) },
                            label = { Text(stringResource(styleLabelRes(style))) },
                            modifier = Modifier.semantics { testTag = "style_${style.name}" },
                        )
                    }
                }
            }

            PinnedNoteCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_cap_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onCapChange(state.preferences.nonUrgentDailyCap - 1) },
                        enabled = state.preferences.nonUrgentDailyCap > 0,
                    ) { Text("−") }
                    Text(
                        text = state.preferences.nonUrgentDailyCap.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { testTag = "cap_value" },
                    )
                    OutlinedButton(
                        onClick = { onCapChange(state.preferences.nonUrgentDailyCap + 1) },
                        enabled = state.preferences.nonUrgentDailyCap < MAX_CAP,
                    ) { Text("+") }
                }
                Text(
                    text = "Only low-priority cards are limited; anything important is never held back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PinnedNoteCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_quiet_hours_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                val quiet = state.preferences.quietHours
                Text(
                    text = if (quiet == null) {
                        stringResource(R.string.settings_quiet_hours_none)
                    } else {
                        "${TIME_FORMAT.format(quiet.start)} – ${TIME_FORMAT.format(quiet.end)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "quiet_hours_value" },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        pickTime(context, quiet?.start ?: DEFAULT_QUIET_START) { start ->
                            onQuietHoursChange(start, quiet?.end ?: DEFAULT_QUIET_END)
                        }
                    }) { Text("From") }
                    TextButton(onClick = {
                        pickTime(context, quiet?.end ?: DEFAULT_QUIET_END) { end ->
                            onQuietHoursChange(quiet?.start ?: DEFAULT_QUIET_START, end)
                        }
                    }) { Text("To") }
                    if (quiet != null) {
                        TextButton(onClick = { onQuietHoursChange(null, null) }) { Text("Clear") }
                    }
                }
            }

            SectionTitle("Permissions")

            PinnedNoteCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_notifications_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (state.notificationsAvailable) {
                        stringResource(R.string.settings_notifications_enabled_state)
                    } else {
                        stringResource(R.string.settings_notifications_denied_state)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "notifications_state" },
                )
                if (!state.notificationsAvailable) {
                    TextButton(
                        onClick = onRequestNotifications,
                        modifier = Modifier.semantics { testTag = "request_notifications" },
                    ) { Text(stringResource(R.string.settings_notifications_enable)) }
                }
            }

            PinnedNoteCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_overlay_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.preferences.overlayEnabled,
                        onCheckedChange = onOverlayToggle,
                        modifier = Modifier.semantics { testTag = "overlay_switch" },
                    )
                }
                Text(
                    text = stringResource(R.string.settings_overlay_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (state.overlayAvailable) {
                        stringResource(R.string.settings_overlay_enabled_state)
                    } else {
                        stringResource(R.string.settings_overlay_denied_state)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "overlay_state" },
                )
            }

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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@androidx.annotation.StringRes
private fun styleLabelRes(style: ReminderStyle): Int = when (style) {
    ReminderStyle.QUIET -> R.string.settings_style_quiet
    ReminderStyle.BALANCED -> R.string.settings_style_balanced
    ReminderStyle.MORE_GUIDANCE -> R.string.settings_style_more_guidance
}

private fun pickTime(
    context: android.content.Context,
    initial: LocalTime,
    onPicked: (LocalTime) -> Unit,
) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        true,
    ).show()
}

private const val MAX_CAP = 10
private val DEFAULT_QUIET_START: LocalTime = LocalTime.of(21, 0)
private val DEFAULT_QUIET_END: LocalTime = LocalTime.of(7, 0)
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ChronaTheme {
        Surface {
            SettingsScreen(
                state = SettingsUiState(isLoading = false, preferences = UserPreferences()),
                onStyleChange = {},
                onCapChange = {},
                onQuietHoursChange = { _, _ -> },
                onOverlayToggle = {},
                onRequestNotifications = {},
            )
        }
    }
}
