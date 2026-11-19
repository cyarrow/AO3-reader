package io.github.cyarrow.ao3reader.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.ao3.WorkSummary
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()
fun formatCount(n: Int): String = numberFormat.format(n)

/** Short label + color for an AO3 rating. */
fun ratingBadge(rating: String): Pair<String, Color> = when {
    rating.startsWith("Explicit") -> "E" to Color(0xFF9C1A1A)
    rating.startsWith("Mature") -> "M" to Color(0xFFC0571B)
    rating.startsWith("Teen") -> "T" to Color(0xFFB59200)
    rating.startsWith("General") -> "G" to Color(0xFF4C8A3C)
    else -> "?" to Color(0xFF7A7A7A)
}

@Composable
fun RatingBadge(rating: String) {
    val (label, color) = ratingBadge(rating)
    Surface(color = color, shape = RoundedCornerShape(4.dp)) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkCard(work: WorkSummary, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatingBadge(work.rating)
            Spacer(Modifier.width(8.dp))
            Text(
                work.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (work.restricted) {
                Icon(Icons.Filled.Lock, null, Modifier.padding(start = 4.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
        Text(
            work.authors.joinToString(", ") + if (work.fandoms.isNotEmpty()) " · " + work.fandoms.joinToString(", ") else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
        )
        val tags = work.relationships.take(3) + work.freeforms.take(4)
        if (tags.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), maxLines = 2,
            ) {
                tags.forEach { TagChip(it, highlighted = it in work.relationships) }
            }
        }
        if (work.summary.isNotBlank()) {
            Text(
                work.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        val words = stringResource(R.string.stat_words, formatCount(work.words))
        val chapters = stringResource(R.string.stat_chapters, work.chapters)
        val complete = stringResource(R.string.stat_complete)
        Text(
            buildString {
                append(words).append(" · ").append(chapters)
                if (work.complete) append(" · ").append(complete)
                append(" · ♥ ").append(formatCount(work.kudos))
                if (work.updated.isNotBlank()) append(" · ").append(work.updated)
            },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
fun TagChip(text: String, highlighted: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
