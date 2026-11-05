package com.yk.notiffilter

import com.yk.notiffilter.data.LogAction
import com.yk.notiffilter.data.LogEntry
import com.yk.notiffilter.data.VerdictSource
import com.yk.notiffilter.ui.screens.LogFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterTest {

    private fun createLogEntry(
        id: Long,
        action: LogAction,
        title: String = "Test Title",
        text: String = "Test Text",
    ) = LogEntry(
        id = id,
        packageName = "com.test.app",
        appLabel = "Test App",
        title = title,
        text = text,
        fingerprint = "fp-$id",
        action = action,
        reason = "Test Reason",
        source = VerdictSource.USER,
        fromCache = false,
        postedAt = System.currentTimeMillis(),
    )

    @Test
    fun `LogFilter matches correct actions`() {
        assertTrue(LogFilter.ALL.matches(LogAction.KEPT))
        assertTrue(LogFilter.ALL.matches(LogAction.HIDDEN))
        assertTrue(LogFilter.ALL.matches(LogAction.OBSERVED))
        assertTrue(LogFilter.ALL.matches(LogAction.ASKED))

        assertTrue(LogFilter.KEPT.matches(LogAction.KEPT))
        assertFalse(LogFilter.KEPT.matches(LogAction.HIDDEN))
        assertFalse(LogFilter.KEPT.matches(LogAction.OBSERVED))
        assertFalse(LogFilter.KEPT.matches(LogAction.ASKED))

        assertTrue(LogFilter.HIDDEN.matches(LogAction.HIDDEN))
        assertFalse(LogFilter.HIDDEN.matches(LogAction.KEPT))
        assertFalse(LogFilter.HIDDEN.matches(LogAction.OBSERVED))
        assertFalse(LogFilter.HIDDEN.matches(LogAction.ASKED))

        assertTrue(LogFilter.OTHER.matches(LogAction.OBSERVED))
        assertTrue(LogFilter.OTHER.matches(LogAction.ASKED))
        assertFalse(LogFilter.OTHER.matches(LogAction.KEPT))
        assertFalse(LogFilter.OTHER.matches(LogAction.HIDDEN))
    }

    @Test
    fun `filtering entries with LogFilter returns only matching entries`() {
        val entries = listOf(
            createLogEntry(1, LogAction.KEPT),
            createLogEntry(2, LogAction.HIDDEN),
            createLogEntry(3, LogAction.KEPT),
            createLogEntry(4, LogAction.OBSERVED),
            createLogEntry(5, LogAction.HIDDEN),
        )

        val kept = entries.filter { LogFilter.KEPT.matches(it.action) }
        assertEquals(2, kept.size)
        assertTrue(kept.all { it.action == LogAction.KEPT })

        val hidden = entries.filter { LogFilter.HIDDEN.matches(it.action) }
        assertEquals(2, hidden.size)
        assertTrue(hidden.all { it.action == LogAction.HIDDEN })

        val other = entries.filter { LogFilter.OTHER.matches(it.action) }
        assertEquals(1, other.size)
        assertEquals(LogAction.OBSERVED, other.first().action)

        val all = entries.filter { LogFilter.ALL.matches(it.action) }
        assertEquals(5, all.size)
    }

    @Test
    fun `filtering empty entries list produces empty results for all filters`() {
        val empty = emptyList<LogEntry>()
        assertTrue(empty.filter { LogFilter.ALL.matches(it.action) }.isEmpty())
        assertTrue(empty.filter { LogFilter.KEPT.matches(it.action) }.isEmpty())
        assertTrue(empty.filter { LogFilter.HIDDEN.matches(it.action) }.isEmpty())
        assertTrue(empty.filter { LogFilter.OTHER.matches(it.action) }.isEmpty())
    }
}
