package com.chrona.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Structural surfaces shared by every screen.
 *
 * These exist because screens were growing their own ad-hoc layout: a flat run of
 * "label ....... value" lines with nothing separating one group from the next reads as a wall of
 * text, and the values — the part a user actually came for — were rendered in the *muted* ink
 * while the labels got the bright one. The emphasis was exactly backwards.
 *
 * So: groups get a card, numbers get their own tile with the bright ink, and labels get the muted
 * ink they belong in.
 */

/** Corner radius used by every grouped surface, so panels agree with each other. */
private val CardShape = RoundedCornerShape(18.dp)

/**
 * A titled group of related content on a raised surface.
 *
 * [accent] draws a short warm rule beside the title rather than tinting the whole card, which
 * keeps a long list of cards readable instead of turning the screen into stripes.
 */
@Composable
fun ChronaCard(
    title: String?,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    testTag: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    val tokens = ChronaTheme.tokens
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (testTag != null) {
                    Modifier.semantics { this.testTag = testTag }
                } else {
                    Modifier
                },
            ),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = tokens.surfaceRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (accent != null) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.2.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

/** One labelled number. The value carries the weight; the label is supporting text. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    testTag: String? = null,
) {
    val tokens = ChronaTheme.tokens
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.glass)
            .border(1.dp, tokens.glassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .then(
                if (testTag != null) {
                    Modifier.semantics {
                        this.testTag = testTag
                        contentDescription = "$label: $value"
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            // A clipped label reads as a rendering bug rather than as "this tile is too narrow",
            // so shorten the wording rather than truncating it.
            overflow = TextOverflow.Visible,
        )
    }
}

/**
 * A heading between cards.
 *
 * The colour is set explicitly rather than inherited. Left to the ambient content colour it picked
 * up whatever the enclosing surface happened to provide, which on one screen rendered "Days" as
 * near-black text on a near-black background — invisible, and invisible only sometimes.
 */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .then(if (testTag != null) Modifier.semantics { this.testTag = testTag } else Modifier),
    )
}

/** One entry in a [StatTileGrid]. */
data class StatTileData(
    val label: String,
    val value: String,
    val testTag: String? = null,
    val valueColor: Color? = null,
)

/**
 * A wrapping grid of labelled numbers.
 *
 * Tiles are chunked into rows rather than squeezed into one: six tiles across a 393dp screen
 * left every label wrapped into unreadable fragments ("Sche / duled"), which is worse than
 * having fewer, taller tiles. [columns] is capped by what actually fits.
 */
@Composable
fun StatTileGrid(
    tiles: List<StatTileData>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    StatTile(
                        label = tile.label,
                        value = tile.value,
                        valueColor = tile.valueColor,
                        testTag = tile.testTag,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep the last row's tiles the same width as every other row's.
                repeat(columns - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A label with a value that may be a sentence rather than a number.
 *
 * Right-aligned beside the label, a long value wraps into a narrow column of ragged fragments.
 * Stacked, it gets the full width and stays readable.
 */
@Composable
fun StatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    testTag: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(
                if (testTag != null) {
                    Modifier.semantics {
                        this.testTag = testTag
                        contentDescription = "$label: $value"
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * A label/value line inside a [ChronaCard].
 *
 * [value] defaults to the bright ink and [label] to the muted one: a reader scanning the card
 * should catch the number first, which was the opposite of what this rendered before.
 */
@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    testTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .then(
                if (testTag != null) {
                    Modifier.semantics {
                        this.testTag = testTag
                        contentDescription = "$label: $value"
                    }
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1.4f)
                .padding(start = 12.dp),
        )
    }
}

/** A hairline rule that matches the card borders, for separating rows inside a card. */
@Composable
fun ChronaDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ChronaTheme.tokens.glassBorder),
    )
}

/** Alias so [ChronaCard]'s content lambda does not leak a `ColumnScope` into caller code. */
typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope
