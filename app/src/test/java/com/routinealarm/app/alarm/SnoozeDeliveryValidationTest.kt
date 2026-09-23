package com.routinealarm.app.alarm

import org.junit.Assert.*
import org.junit.Test

/** Proposed contract simulation only; does not execute Android delivery or Room. */
class SnoozeDeliveryValidationTest {
    private data class Ticket(
        val alarm: Int = 1,
        val occurrence: String = "occurrence",
        val revision: Long = 7,
        val session: String = "session",
        val cycle: Int = 2,
        val due: Long = 1_000_000,
    )
    private data class Clock(val wall: Long = 1_000_000, val elapsed: Long = 400_000, val boot: Int? = 10)
    private enum class Phase { SNOOZED, CLAIMED, FIRING, ENDED }
    private class Simulation {
        var expected = Ticket()
        var elapsedDue: Long? = 400_000
        var boot: Int? = 10
        var phase = Phase.SNOOZED
        var starts = 0

        private fun matches(ticket: Ticket, clock: Clock): Boolean {
            if (ticket != expected || ticket.cycle <= 0 || ticket.due <= 0) return false
            val sameBoot = boot != null && boot!! >= 0 && boot == clock.boot
            return if (sameBoot && elapsedDue != null) clock.elapsed >= elapsedDue!!
            else clock.wall >= expected.due
        }

        fun receive(ticket: Ticket, clock: Clock = Clock()): Boolean {
            if (phase != Phase.SNOOZED || !matches(ticket, clock)) return false
            phase = Phase.CLAIMED // Simulates atomic Room claim, not concurrency evidence.
            return true
        }

        fun start(ticket: Ticket, clock: Clock = Clock()): Boolean {
            if (phase != Phase.CLAIMED || !matches(ticket, clock)) return false
            phase = Phase.FIRING
            starts++
            return true
        }
    }

    @Test fun previousCycleCannotClaimCurrentSnoozeEvenWithSameDue() {
        val sim = Simulation()
        assertFalse(sim.receive(sim.expected.copy(cycle = 1)))
        assertEquals(Phase.SNOOZED, sim.phase)
        assertTrue(sim.receive(sim.expected))
    }

    @Test fun futureCycleIsRejected() {
        val sim = Simulation()
        assertFalse(sim.receive(sim.expected.copy(cycle = 3)))
    }

    @Test fun changedDueInSameCycleIsRejected() {
        val sim = Simulation()
        assertFalse(sim.receive(sim.expected.copy(due = 999_999)))
        assertFalse(sim.receive(sim.expected.copy(due = 1_000_001)))
        assertTrue(sim.receive(sim.expected))
    }

    @Test fun allSessionIdentityFieldsAreRequired() {
        val sim = Simulation()
        val ticket = sim.expected
        listOf(ticket.copy(alarm = 2), ticket.copy(occurrence = "old"),
            ticket.copy(revision = 6), ticket.copy(session = "old")).forEach {
            assertFalse(sim.receive(it))
        }
        assertTrue(sim.receive(ticket))
    }

    @Test fun normalDueAndLateDeliveryStartOnce() {
        listOf(Clock(), Clock(wall = 1_000_050, elapsed = 400_050)).forEach { clock ->
            val sim = Simulation()
            assertTrue(sim.receive(sim.expected, clock))
            assertTrue(sim.start(sim.expected, clock))
            assertEquals(1, sim.starts)
        }
    }

    @Test fun earlyDeliveryDoesNotConsumeClaim() {
        val sim = Simulation()
        assertFalse(sim.receive(sim.expected, Clock(elapsed = 399_999)))
        assertEquals(Phase.SNOOZED, sim.phase)
        assertTrue(sim.receive(sim.expected))
    }

    @Test fun duplicateReceiverAndServiceRequestsDoNotRestartPlayback() {
        val sim = Simulation()
        val ticket = sim.expected
        assertTrue(sim.receive(ticket))
        assertFalse(sim.receive(ticket))
        assertTrue(sim.start(ticket))
        repeat(10) {
            assertFalse(sim.receive(ticket))
            assertFalse(sim.start(ticket))
        }
        assertEquals(1, sim.starts)
    }

    @Test fun delayedServiceRequestCannotStartNewerClaimedCycle() {
        val sim = Simulation()
        val old = sim.expected
        assertTrue(sim.receive(old))
        assertTrue(sim.start(old))
        sim.expected = old.copy(cycle = 3, due = SnoozePolicy.dueAt(old.due))
        sim.elapsedDue = SnoozePolicy.dueAt(400_000)
        sim.phase = Phase.SNOOZED
        val now = Clock(wall = 1_300_000, elapsed = 700_000)
        assertFalse(sim.start(old, now))
        assertTrue(sim.receive(sim.expected, now))
        assertFalse(sim.start(old, now))
        assertEquals(Phase.CLAIMED, sim.phase)
        assertTrue(sim.start(sim.expected, now))
        assertEquals(2, sim.starts)
    }

    @Test fun clockForwardDoesNotMakeSameBootSnoozeEarly() {
        val sim = Simulation()
        assertFalse(sim.receive(sim.expected, Clock(wall = 9_000_000, elapsed = 399_999)))
    }

    @Test fun clockBackwardDoesNotDelaySameBootSnooze() {
        val sim = Simulation()
        assertTrue(sim.receive(sim.expected, Clock(wall = 1, elapsed = 400_000)))
    }

    @Test fun rebootUsesWallDeadlineInsteadOfPreviousElapsedClock() {
        val sim = Simulation()
        assertFalse(sim.receive(sim.expected, Clock(wall = 999_999, elapsed = 999_999, boot = 11)))
        assertTrue(sim.receive(sim.expected, Clock(wall = 1_000_000, elapsed = 1, boot = 11)))
    }

    @Test fun unknownBootIsNeverAssumedSameBoot() {
        listOf<Int?>(null, -1).forEach { unknown ->
            val sim = Simulation()
            sim.boot = unknown
            assertFalse(sim.receive(sim.expected, Clock(wall = 999_999, elapsed = 999_999, boot = unknown)))
            assertTrue(sim.receive(sim.expected, Clock(elapsed = 1, boot = unknown)))
            val knownStoredBoot = Simulation()
            assertFalse(knownStoredBoot.receive(knownStoredBoot.expected,
                Clock(wall = 999_999, elapsed = 999_999, boot = unknown)))
            assertTrue(knownStoredBoot.receive(knownStoredBoot.expected,
                Clock(elapsed = 1, boot = unknown)))
        }
    }

    @Test fun missingElapsedDeadlineFallsBackToWall() {
        val sim = Simulation()
        sim.elapsedDue = null
        assertFalse(sim.receive(sim.expected, Clock(wall = 999_999)))
        assertTrue(sim.receive(sim.expected, Clock(elapsed = 1)))
    }

    @Test fun reanchoredReservationRejectsOldDueAndAcceptsReplacement() {
        val sim = Simulation()
        val old = sim.expected
        val newDue = SnoozePolicy.reanchoredWallDueAt(2_000_000, 350_000, 400_000)
        assertEquals(2_050_000L, newDue)
        sim.expected = old.copy(due = newDue)
        val now = Clock(wall = newDue, elapsed = 400_000)
        assertFalse(sim.receive(old, now))
        assertTrue(sim.receive(sim.expected, now))
        assertTrue(sim.start(sim.expected, now))
    }

    @Test fun cancellationBetweenReceiveAndStartPreventsPlayback() {
        val sim = Simulation()
        assertTrue(sim.receive(sim.expected))
        sim.phase = Phase.ENDED
        assertFalse(sim.start(sim.expected))
        assertEquals(0, sim.starts)
    }

    @Test fun identityOnlyControlCannotDistinguishOldCycle() {
        val current = Ticket()
        val old = current.copy(cycle = 1, due = 700_000)
        // Characterizes omitted information, not execution of the actual DAO.
        fun identityOnly(ticket: Ticket) = ticket.alarm == current.alarm &&
            ticket.occurrence == current.occurrence && ticket.revision == current.revision &&
            ticket.session == current.session
        assertTrue(identityOnly(old))
        assertTrue(identityOnly(current))
        val sim = Simulation()
        assertFalse(sim.receive(old))
        assertTrue(sim.receive(current))
    }
}
