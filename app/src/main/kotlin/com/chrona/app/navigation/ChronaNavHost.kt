package com.chrona.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chrona.app.di.AppContainer
import com.chrona.app.ui.board.BoardRoute
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.glassSurface
import com.chrona.app.ui.history.HistoryScreen
import com.chrona.app.ui.questcard.QuestCardRoute
import com.chrona.app.ui.schedule.ScheduleRoute
import com.chrona.app.ui.settings.SettingsRoute
import com.chrona.app.ui.today.TodayRoute
import com.chrona.app.ui.voice.VoiceCaptureViewModel
import com.chrona.app.ui.voice.VoiceMicButton
import com.chrona.app.ui.voice.VoiceMicCaption
import com.chrona.app.ui.voice.VoicePanel
import com.chrona.app.ui.voice.VoiceUiState
import java.time.ZoneId
import java.util.Locale

/** What the shell tells Today about a capture or a typed entry started elsewhere. */
data class TaskEntryRequests(
    /** Prefill for the typed editor, set when a capture ends in "type it instead". */
    val textPrefill: String? = null,
    /** Bumped whenever tasks were stored, so the list reloads from the same repository. */
    val createdCount: Int = 0,
)

/**
 * Navigation shell: five top-level destinations, all peers (UI-001).
 *
 * It also owns the two things that must behave identically on every screen: the microphone and
 * the text-entry destination. The capture state machine therefore lives here rather than inside
 * any one destination, so a task can be spoken from the Board exactly as easily as from Today, and
 * the button cannot drift between screens.
 */
@Composable
fun ChronaNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val zoneId = remember { ZoneId.systemDefault() }
    val localeTag = remember { Locale.getDefault().toLanguageTag() }

    // Scoped to the activity, not to a destination: the capture outlives a tab switch.
    val voiceViewModel: VoiceCaptureViewModel = viewModel(
        factory = VoiceCaptureViewModel.Factory(
            speech = container.speechRecognition,
            parser = container.taskUnderstanding,
            createTasksFromIntents = { drafts -> container.createTaskFromIntent.createAll(drafts) },
            timeSource = container.timeSource,
            zoneId = zoneId,
            localeTag = localeTag,
            parsePreferences = container.parsePreferences,
        ),
    )
    val voiceState by voiceViewModel.state.collectAsStateWithLifecycle()

    var requests by remember { mutableStateOf(TaskEntryRequests()) }

    // A confirmation means new records exist, so Today reloads from the same repository the
    // typed path writes to rather than keeping its own copy.
    val createdTasks = (voiceState as? VoiceUiState.Created)?.tasks?.size
    LaunchedEffect(createdTasks) {
        if (createdTasks != null) {
            requests = requests.copy(createdCount = requests.createdCount + createdTasks)
        }
    }

    val openTypedEntry: (String) -> Unit = { prefill ->
        voiceViewModel.dismiss()
        requests = requests.copy(textPrefill = prefill)
        navController.navigateToTopLevel(ChronaDestination.TODAY)
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { ChronaNavBar(currentRoute, navController::navigateToTopLevel) },
        // Centred, not the Scaffold default of end: the microphone is a global control, and a
        // thumb reaching for it should never depend on which hand holds the phone.
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VoiceMicCaption(state = voiceState)
                VoiceMicButton(
                    state = voiceState,
                    onStart = voiceViewModel::pressToListen,
                    onStop = voiceViewModel::release,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ChronaDestination.TODAY.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(ChronaDestination.TODAY.route) {
                    TodayRoute(
                        container = container,
                        requests = requests,
                        onTextEntryHandled = { requests = requests.copy(textPrefill = null) },
                        onOpenQuestCards = { navController.navigate(QUEST_CARD_ROUTE) },
                    )
                }
                composable(ChronaDestination.SCHEDULE.route) { ScheduleRoute(container) }
                composable(ChronaDestination.BOARD.route) { BoardRoute(container) }
                composable(ChronaDestination.HISTORY.route) { HistoryScreen() }
                composable(ChronaDestination.SETTINGS.route) { SettingsRoute(container) }
                // Quest Cards are a detail surface reached from Today, not a sixth bottom-bar tab.
                composable(QUEST_CARD_ROUTE) { QuestCardRoute(container) }
            }

            // The capture overlay sits above the current screen, clear of the microphone and the
            // navigation bar, so nothing important is covered while it is up.
            VoicePanel(
                state = voiceState,
                onPermissionResult = voiceViewModel::onPermissionResult,
                onAnswer = voiceViewModel::answer,
                onEditTitle = voiceViewModel::editTitle,
                onEditDuration = voiceViewModel::editDurationMinutes,
                onEditDate = voiceViewModel::editDate,
                onCreate = voiceViewModel::create,
                onRetry = voiceViewModel::retry,
                onDismiss = voiceViewModel::dismiss,
                onTypeInstead = { openTypedEntry("") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 96.dp,
                    ),
            )
        }
    }
}

/**
 * The glass navigation bar.
 *
 * Five peers, one indicator: the selected destination is marked by an amber pill, an amber icon
 * and a heavier label at once, so the current screen is obvious without relying on colour alone.
 */
@Composable
private fun ChronaNavBar(
    currentRoute: String?,
    onSelect: (ChronaDestination) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .glassSurface(container = ChronaTheme.tokens.glass, elevation = 18.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .semantics { testTag = "chrona_bottom_bar" },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChronaDestination.entries.forEach { destination ->
                NavBarItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onSelect(destination) },
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    destination: ChronaDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(destination.labelRes)
    val tint by animateColorAsState(
        targetValue = if (selected) {
            ChronaTheme.tokens.accent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(180),
        label = "nav_tint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(180),
        label = "nav_scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) ChronaTheme.tokens.glassStrong else Color.Transparent)
            // Before `selectable`, so this configuration merges into the node it makes clickable:
            // one node carrying the tag, the label, the selected state and the tap target, rather
            // than two overlapping ones for a screen reader.
            .semantics {
                testTag = "nav_${destination.route}"
                contentDescription = label
            }
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .scale(iconScale),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

/** Quest Cards live outside the bottom bar: a detail surface opened from Today. */
const val QUEST_CARD_ROUTE: String = "questcard"

/**
 * Top-level destinations keep a single entry on the back stack, so switching tabs never grows
 * an unbounded history the user has to back out of.
 */
private fun NavHostController.navigateToTopLevel(destination: ChronaDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}