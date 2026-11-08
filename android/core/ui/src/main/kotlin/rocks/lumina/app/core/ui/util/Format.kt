package rocks.lumina.app.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import rocks.lumina.app.core.ui.R

/** Compact counts: 999, 1.2K, 3.4M. */
public fun formatCount(value: Long): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> trim(value / 1_000.0) + "K"
    else -> trim(value / 1_000_000.0) + "M"
}

public fun formatCount(value: Int): String = formatCount(value.toLong())

private fun trim(v: Double): String {
    val s = String.format(java.util.Locale.US, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

public fun formatSats(value: Long): String = NumberFormat.getIntegerInstance().format(value)

/** Instagram-like relative time ("now", "5m", "3h", "2d", "4w", then a date). */
@Composable
public fun relativeTime(createdAt: Long): String {
    val diff = (System.currentTimeMillis() / 1000 - createdAt).coerceAtLeast(0)
    return when {
        diff < 60 -> stringResource(R.string.time_now)
        diff < 3600 -> stringResource(R.string.time_minutes, (diff / 60).toInt())
        diff < 86_400 -> stringResource(R.string.time_hours, (diff / 3600).toInt())
        diff < 7 * 86_400 -> stringResource(R.string.time_days, (diff / 86_400).toInt())
        diff < 5 * 7 * 86_400 -> stringResource(R.string.time_weeks, (diff / (7 * 86_400)).toInt())
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(createdAt * 1000))
    }
}

public fun formatTime(createdAt: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(createdAt * 1000))

public fun formatDate(createdAt: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(createdAt * 1000))
