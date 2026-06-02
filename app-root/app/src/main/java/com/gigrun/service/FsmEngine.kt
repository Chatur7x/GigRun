package com.gigrun.service

import com.gigrun.core.utils.HaversineCalculator

/**
 * Finite State Machine engine for automatic trip detection and classification.
 *
 * States:
 * - IDLE_AT_HOME: User is at home, no tracking
 * - UNCLASSIFIED_COMMUTE: User left home, destination unknown
 * - AT_COLLEGE: User arrived at college anchor
 * - WAITING_AT_STORE: User is at a delivery hub/store
 * - DELIVERING_ORDER: User left store with an order
 * - ORDER_COMPLETE: User completed delivery (transient state)
 */
class FsmEngine {

    enum class State {
        IDLE_AT_HOME,
        UNCLASSIFIED_COMMUTE,
        AT_COLLEGE,
        WAITING_AT_STORE,
        DELIVERING_ORDER,
        ORDER_COMPLETE
    }

    data class AnchorPoint(
        val lat: Double,
        val lon: Double,
        val radiusMeters: Double
    )

    data class TransitionResult(
        val previousState: State,
        val newState: State,
        val changed: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    var currentState: State = State.IDLE_AT_HOME
        private set

    var homeAnchor: AnchorPoint? = null
    var storeAnchor: AnchorPoint? = null
    var collegeAnchor: AnchorPoint? = null

    // Confidence tracking: require 3 consecutive readings within radius
    private var consecutiveInHome = 0
    private var consecutiveInStore = 0
    private var consecutiveInCollege = 0
    private var consecutiveOutHome = 0
    private var consecutiveOutStore = 0
    private val CONFIDENCE_THRESHOLD = 3 // 3 readings at 5s = 15 seconds

    /**
     * Process a new GPS location and determine if a state transition should occur.
     * @param lat Current latitude
     * @param lon Current longitude
     * @return TransitionResult indicating whether state changed
     */
    fun processLocation(lat: Double, lon: Double): TransitionResult {
        val previousState = currentState

        val inHome = homeAnchor?.let {
            HaversineCalculator.isWithinRadius(lat, lon, it.lat, it.lon, it.radiusMeters)
        } ?: false

        val inStore = storeAnchor?.let {
            HaversineCalculator.isWithinRadius(lat, lon, it.lat, it.lon, it.radiusMeters)
        } ?: false

        val inCollege = collegeAnchor?.let {
            HaversineCalculator.isWithinRadius(lat, lon, it.lat, it.lon, it.radiusMeters)
        } ?: false

        // Update consecutive counters
        if (inHome) { consecutiveInHome++; consecutiveOutHome = 0 }
        else { consecutiveOutHome++; consecutiveInHome = 0 }

        if (inStore) { consecutiveInStore++; consecutiveOutStore = 0 }
        else { consecutiveOutStore++; consecutiveInStore = 0 }

        if (inCollege) consecutiveInCollege++ else consecutiveInCollege = 0

        when (currentState) {
            State.IDLE_AT_HOME -> {
                if (consecutiveOutHome >= CONFIDENCE_THRESHOLD) {
                    currentState = State.UNCLASSIFIED_COMMUTE
                }
            }

            State.UNCLASSIFIED_COMMUTE -> {
                when {
                    consecutiveInStore >= CONFIDENCE_THRESHOLD -> {
                        currentState = State.WAITING_AT_STORE
                    }
                    consecutiveInCollege >= CONFIDENCE_THRESHOLD -> {
                        currentState = State.AT_COLLEGE
                    }
                    consecutiveInHome >= CONFIDENCE_THRESHOLD -> {
                        currentState = State.IDLE_AT_HOME
                    }
                }
            }

            State.AT_COLLEGE -> {
                if (!inCollege && consecutiveInCollege == 0) {
                    currentState = State.UNCLASSIFIED_COMMUTE
                    resetCounters()
                }
            }

            State.WAITING_AT_STORE -> {
                if (consecutiveOutStore >= CONFIDENCE_THRESHOLD) {
                    currentState = State.DELIVERING_ORDER
                }
            }

            State.DELIVERING_ORDER -> {
                if (consecutiveInStore >= CONFIDENCE_THRESHOLD) {
                    currentState = State.ORDER_COMPLETE
                }
                // Also check if rider went home
                if (consecutiveInHome >= CONFIDENCE_THRESHOLD) {
                    currentState = State.IDLE_AT_HOME
                }
            }

            State.ORDER_COMPLETE -> {
                // Transient state — immediately transition to WAITING_AT_STORE
                currentState = State.WAITING_AT_STORE
                resetCounters()
            }
        }

        return TransitionResult(
            previousState = previousState,
            newState = currentState,
            changed = previousState != currentState
        )
    }

    /**
     * Force-reset the FSM to IDLE_AT_HOME state.
     */
    fun reset() {
        currentState = State.IDLE_AT_HOME
        resetCounters()
    }

    private fun resetCounters() {
        consecutiveInHome = 0
        consecutiveInStore = 0
        consecutiveInCollege = 0
        consecutiveOutHome = 0
        consecutiveOutStore = 0
    }
}
