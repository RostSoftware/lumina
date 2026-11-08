package rocks.lumina.app.feature.messages

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import rocks.lumina.app.core.data.DmMessage

/** One row in the chat list: either a day separator or a message bubble. */
sealed interface ChatItem {
    val key: String

    data class Day(val date: LocalDate) : ChatItem {
        override val key: String get() = "day:$date"
    }

    data class Bubble(
        val message: DmMessage,
        val own: Boolean,
        /** True when the next message (newer) is from the same sender within a short window. */
        val groupedWithNext: Boolean,
    ) : ChatItem {
        override val key: String get() = "msg:${message.id}"
    }
}

object ChatItems {
    private const val GROUP_WINDOW_SECONDS = 5 * 60L

    /**
     * Builds chat rows from [messages] (any order), oldest first, inserting a [ChatItem.Day] before the
     * first message of each local calendar day.
     */
    fun build(messages: List<DmMessage>, me: String?, zone: ZoneId = ZoneId.systemDefault()): List<ChatItem> {
        val sorted = messages.sortedBy { it.createdAt }
        val out = ArrayList<ChatItem>(sorted.size + 4)
        var lastDay: LocalDate? = null
        sorted.forEachIndexed { index, message ->
            val day = Instant.ofEpochSecond(message.createdAt).atZone(zone).toLocalDate()
            if (day != lastDay) {
                out.add(ChatItem.Day(day))
                lastDay = day
            }
            val next = sorted.getOrNull(index + 1)
            val grouped = next != null &&
                next.sender == message.sender &&
                next.createdAt - message.createdAt < GROUP_WINDOW_SECONDS &&
                Instant.ofEpochSecond(next.createdAt).atZone(zone).toLocalDate() == day
            out.add(ChatItem.Bubble(message, own = me != null && message.sender == me, groupedWithNext = grouped))
        }
        return out
    }
}
