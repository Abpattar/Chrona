package com.chrona.app.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Radius shared by every card, chip and panel, so the app reads as one surface family. */
val ChronaShape: Shape = RoundedCornerShape(20.dp)

/**
 * A card surface: graphite, hairline border, soft shadow, and an optional warm accent edge.
 *
 * The accent stripe and the pin are decoration, so they are excluded from the accessibility
 * tree; the card's own text carries all meaning.
 */
@Composable
fun PinnedNoteCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = ChronaTheme.tokens
    Card(
        modifier = modifier,
        shape = ChronaShape,
        colors = CardDefaults.cardColors(containerColor = tokens.surfaceRaised),
        border = BorderStroke(1.dp, tokens.glassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (accent != null) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(accent, accent.copy(alpha = 0.35f)),
                            ),
                        )
                        .clearAndSetSemantics { },
                )
            }
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * Translucent surface for anything floating above scrolling content.
 *
 * Kept as one modifier so every floating element shares the same translucency, border and shadow
 * instead of each control inventing its own glass.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = ChronaShape,
    container: Color = ChronaTheme.tokens.glass,
    border: Color = ChronaTheme.tokens.glassBorder,
    elevation: Dp = 12.dp,
): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(container)
    .border(1.dp, border, shape)

/**
 * A restrained amber halo. Used only to signal that Chrona is actively listening; static
 * surfaces stay flat so the halo keeps its meaning.
 */
fun Modifier.ambientGlow(color: Color, strength: Float): Modifier = this.background(
    Brush.radialGradient(
        colors = listOf(color.copy(alpha = 0.28f * strength), Color.Transparent),
        radius = 260f,
    ),
)

/**
 * Shared empty state. Every surface uses it so "nothing here yet" always explains itself and
 * names the next available action.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .clearAndSetSemantics { },
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = 20.dp)) { action() }
        }
    }
}

/** Decorative pin head; never announced to screen readers. */
@Composable
fun Pin(modifier: Modifier = Modifier, color: Color = ChronaTheme.tokens.pin) {
    Box(
        modifier = modifier
            .size(12.dp)
            .background(color, CircleShape)
            .clearAndSetSemantics { },
    )
}