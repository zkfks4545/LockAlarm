package com.routinealarm.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlarmOccurrenceIdsTest {
    @Test
    fun sameScheduleProducesStableOccurrenceAndSessionIds() {
        val first = AlarmOccurrenceIds.regular(7, 3L, 1_800_000L)
        val second = AlarmOccurrenceIds.regular(7, 3L, 1_800_000L)

        assertEquals(first, second)
    }

    @Test
    fun revisionOrTriggerChangeInvalidatesOldOccurrence() {
        val original = AlarmOccurrenceIds.regular(7, 3L, 1_800_000L)

        assertNotEquals(original, AlarmOccurrenceIds.regular(7, 4L, 1_800_000L))
        assertNotEquals(original, AlarmOccurrenceIds.regular(7, 3L, 2_100_000L))
    }
}
