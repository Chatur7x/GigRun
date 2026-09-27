package com.gigrun.service

import org.junit.Assert.*
import org.junit.Test

class FsmEngineTest {

    private var nowMs = 1_000_000L

    private fun engineWithAnchors(): FsmEngine {
        val fsm = FsmEngine(clockMs = { nowMs })
        fsm.homeAnchor = FsmEngine.AnchorPoint(17.3850, 78.4867, 150.0)
        fsm.storeAnchor = FsmEngine.AnchorPoint(17.4000, 78.5000, 150.0)
        return fsm
    }

    @Test
    fun commuteToStoreToDelivering_fullLoop() {
        val fsm = engineWithAnchors()
        // Leave home: 3 consecutive out-of-home readings → COMMUTE
        repeat(2) { assertFalse(fsm.processLocation(17.3900, 78.4930).changed) }
        val exited = fsm.processLocation(17.3900, 78.4930)
        assertTrue(exited.changed)
        assertEquals(FsmEngine.State.UNCLASSIFIED_COMMUTE, fsm.currentState)
        // Arrive at store → WAITING
        repeat(3) { fsm.processLocation(17.4000, 78.5000) }
        assertEquals(FsmEngine.State.WAITING_AT_STORE, fsm.currentState)
        // Leave store → DELIVERING
        repeat(3) { fsm.processLocation(17.4100, 78.5100) }
        assertEquals(FsmEngine.State.DELIVERING_ORDER, fsm.currentState)
        // Back to store → ORDER_COMPLETE, which collapses to WAITING on the next fix
        repeat(3) { fsm.processLocation(17.4000, 78.5000) }
        assertEquals(FsmEngine.State.ORDER_COMPLETE, fsm.currentState)
        fsm.processLocation(17.4000, 78.5000)
        assertEquals(FsmEngine.State.WAITING_AT_STORE, fsm.currentState)
    }

    @Test
    fun deliveringTimeout_closesStaleTrip() {
        val fsm = engineWithAnchors()
        repeat(3) { fsm.processLocation(17.5000, 78.6000) }
        repeat(3) { fsm.processLocation(17.4000, 78.5000) }
        assertEquals(FsmEngine.State.WAITING_AT_STORE, fsm.currentState)
        repeat(3) { fsm.processLocation(17.4100, 78.5100) }
        assertEquals(FsmEngine.State.DELIVERING_ORDER, fsm.currentState)
        // 2 h + 1 ms of identical fixes → timeout valve closes the delivery.
        nowMs += 2 * 60 * 60 * 1000L + 1
        fsm.processLocation(17.4100, 78.5100)
        assertEquals(FsmEngine.State.ORDER_COMPLETE, fsm.currentState)
    }

    @Test
    fun freshInstallWithoutAnchors_neverLeavesHome() {
        val fsm = FsmEngine(clockMs = { nowMs })
        repeat(10) { fsm.processLocation(17.3900, 78.4930) }
        assertEquals(FsmEngine.State.IDLE_AT_HOME, fsm.currentState)
    }

    @Test
    fun collegeExit_needsSustainedReadings_notSingleJitter() {
        val fsm = FsmEngine(clockMs = { nowMs })
        fsm.collegeAnchor = FsmEngine.AnchorPoint(17.3900, 78.4930, 150.0)
        // Enter college directly from commute path: force via store-less flow
        fsm.homeAnchor = FsmEngine.AnchorPoint(17.0000, 78.0000, 150.0)
        repeat(3) { fsm.processLocation(17.5000, 78.6000) }
        assertEquals(FsmEngine.State.UNCLASSIFIED_COMMUTE, fsm.currentState)
        repeat(3) { fsm.processLocation(17.3900, 78.4930) }
        assertEquals(FsmEngine.State.AT_COLLEGE, fsm.currentState)
        // Single jitter outside must NOT exit (hysteresis needs 3)
        fsm.processLocation(17.3950, 78.4980)
        assertEquals(FsmEngine.State.AT_COLLEGE, fsm.currentState)
    }
}
