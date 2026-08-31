package com.flowerwhisp.mobile.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flowerwhisp.mobile.domain.insights.DayPartInsight
import com.flowerwhisp.mobile.domain.insights.InsightSnapshot
import com.flowerwhisp.mobile.domain.insights.LanguageInsight
import com.flowerwhisp.mobile.ui.components.RowDivider
import com.flowerwhisp.mobile.ui.components.SectionTitle
import com.flowerwhisp.mobile.ui.theme.Clay
import com.flowerwhisp.mobile.ui.theme.Error
import com.flowerwhisp.mobile.ui.theme.MutedText
import com.flowerwhisp.mobile.ui.theme.Outline
import com.flowerwhisp.mobile.ui.theme.PrimaryText
import com.flowerwhisp.mobile.ui.theme.SecondaryText
import com.flowerwhisp.mobile.ui.theme.SurfaceSelected
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun InsightsContent(
    snapshot: InsightSnapshot,
    loading: Boolean,
    error: String?,
) {
    when {
        loading -> InsightStateLine("Loading history", error = false)
        error != null -> InsightStateLine("History unavailable", error = true)
        !snapshot.hasHistory -> InsightStateLine("No saved dictations yet", error = false)
    }

    SectionTitle("Overview")
    OverviewGrid(snapshot)

    SectionTitle("Last 14 days")
    ActivityChart(snapshot)

    SectionTitle("Rhythm")
    InsightValueList(
        "Average words" to snapshot.averageWordsPerSession.formatDecimal(),
        "Average session" to snapshot.averageSessionDurationMs.formatDurationOrDash(),
        "Speaking pace" to snapshot.wordsPerMinute.formatRate(),
        "Current streak" to snapshot.currentStreakDays.formatDays(),
        "Longest streak" to snapshot.longestStreakDays.formatDays(),
        "Best day" to snapshot.bestDay?.let { day ->
            "${day.date.formatShortDate()} · ${day.words.formatCount()} words"
        }.orDash(),
        "Longest session" to snapshot.longestSessionMs.takeIf { snapshot.hasData }?.formatDurationOrDash().orDash(),
    )

    SectionTitle("Outcomes")
    InsightValueList(
        "Completion" to snapshot.completionRate.formatPercent(),
        "Attempts" to snapshot.attemptedSessions.formatCount(),
        "Failed" to snapshot.failedSessions.formatCount(),
        "Cancelled" to snapshot.cancelledSessions.formatCount(),
        "Insertion fallback" to snapshot.insertionFallbacks.formatCount(),
        "Recovery recordings" to snapshot.recoveryRecordings.formatCount(),
        "Favorites" to snapshot.favoriteSessions.formatCount(),
    )

    SectionTitle("Cleanup")
    InsightValueList(
        "Applied" to snapshot.cleanupApplied.formatCount(),
        "Unchanged" to snapshot.cleanupUnchanged.formatCount(),
        "Failed" to snapshot.cleanupFailed.formatCount(),
        "Off" to snapshot.cleanupDisabled.formatCount(),
    )

    SectionTitle("Time of day")
    DistributionList(
        items = snapshot.dayParts,
        label = { it.part.label },
        sessions = DayPartInsight::sessions,
        words = DayPartInsight::words,
        tag = "insights-time-of-day",
    )

    SectionTitle("Language modes")
    if (snapshot.languages.isEmpty()) {
        InsightValueList("Top mode" to "—")
    } else {
        DistributionList(
            items = snapshot.languages,
            label = { it.language.displayName },
            sessions = LanguageInsight::sessions,
            words = LanguageInsight::words,
            tag = "insights-languages",
        )
    }
}

@Composable
private fun InsightStateLine(label: String, error: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp)
            .testTag("insights-state"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .width(7.dp)
                .height(7.dp)
                .background(if (error) Error else Outline, RoundedCornerShape(2.dp)),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (error) Error else SecondaryText)
    }
}

@Composable
private fun OverviewGrid(snapshot: InsightSnapshot) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights-overview"),
    ) {
        val metrics = listOf(
            "Words" to snapshot.totalWords.formatCount(),
            "Sessions" to snapshot.totalSessions.formatCount(),
            "Speaking" to snapshot.speakingTimeMs.formatDuration(),
            "Active days" to snapshot.activeDays.formatCount(),
        )
        if (maxWidth < 300.dp) {
            Column {
                metrics.forEachIndexed { index, metric ->
                    OverviewMetric(metric.first, metric.second, Modifier.fillMaxWidth())
                    if (index < metrics.lastIndex) RowDivider()
                }
            }
        } else {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    OverviewMetric(metrics[0].first, metrics[0].second, Modifier.weight(1f))
                    MetricDivider()
                    OverviewMetric(metrics[1].first, metrics[1].second, Modifier.weight(1f))
                }
                RowDivider()
                Row(Modifier.fillMaxWidth()) {
                    OverviewMetric(metrics[2].first, metrics[2].second, Modifier.weight(1f))
                    MetricDivider()
                    OverviewMetric(metrics[3].first, metrics[3].second, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
    }
}

@Composable
private fun MetricDivider() {
    Box(Modifier.width(1.dp).height(72.dp).background(Outline.copy(alpha = 0.72f)))
}

@Composable
private fun ActivityChart(snapshot: InsightSnapshot) {
    val maxWords = snapshot.recentDays.maxOfOrNull { it.words }?.coerceAtLeast(1) ?: 1
    val recentWords = snapshot.recentDays.sumOf { it.words }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights-activity"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Daily words", style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
            Text("${recentWords.formatCount()} words", style = MaterialTheme.typography.bodyMedium, color = PrimaryText)
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            snapshot.recentDays.forEach { day ->
                val barHeight = if (day.words == 0) 4.dp else (10f + (58f * day.words / maxWords)).dp
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            contentDescription = "${day.date}: ${day.words} words, ${day.sessions} sessions"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.58f)
                            .height(barHeight)
                            .background(if (day.words > 0) Clay else SurfaceSelected, RoundedCornerShape(2.dp)),
                    )
                    Text(
                        day.date.dayOfMonth.toString(),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightValueList(vararg rows: Pair<String, String>) {
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(row.first, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(row.second, style = MaterialTheme.typography.bodyLarge, color = SecondaryText, textAlign = TextAlign.End)
            }
            if (index < rows.lastIndex) RowDivider()
        }
    }
}

@Composable
private fun <T> DistributionList(
    items: List<T>,
    label: (T) -> String,
    sessions: (T) -> Int,
    words: (T) -> Int,
    tag: String,
) {
    val maxSessions = items.maxOfOrNull(sessions)?.coerceAtLeast(1) ?: 1
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items.forEach { item ->
            val sessionCount = sessions(item)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label(item), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "${sessionCount.formatCount()} · ${words(item).formatCount()} words",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        textAlign = TextAlign.End,
                    )
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(SurfaceSelected, RoundedCornerShape(1.dp))) {
                    if (sessionCount > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(sessionCount.toFloat() / maxSessions)
                                .fillMaxHeight()
                                .background(Clay, RoundedCornerShape(1.dp)),
                        )
                    }
                }
            }
        }
    }
}

private fun java.time.LocalDate.formatShortDate(): String =
    format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun Int.formatCount(): String = NumberFormat.getIntegerInstance().format(this)

private fun Long.formatDuration(): String {
    val totalSeconds = coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun Long?.formatDurationOrDash(): String = this?.formatDuration() ?: "—"

private fun Double?.formatDecimal(): String = when (this) {
    null -> "—"
    else -> if (this < 10.0) String.format(Locale.getDefault(), "%.1f", this) else roundToInt().formatCount()
}

private fun Double?.formatRate(): String = this?.let { "${it.formatDecimal()} wpm" } ?: "—"

private fun Double?.formatPercent(): String = this?.let { "${(it * 100.0).roundToInt()}%" } ?: "—"

private fun Int.formatDays(): String = "$this ${if (this == 1) "day" else "days"}"

private fun String?.orDash(): String = this ?: "—"
