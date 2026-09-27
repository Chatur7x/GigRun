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
class FsmEngine(
    /**
     * Monotonic clock (elapsedRealtime on device). Injectable so unit tests can
     * fast-forward the DELIVERING timeout without Robolectric shadows.
     */
    var clockMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) {

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

    // Confidence tracking: require 3 consecutive readings within radius.
    // At FAST GPS (5 s) that's ~15 s; at SLOW (60 s) it's ~3 min — store arrival
    // legitimately lags on slow intervals. Anchor priority in COMMUTE is
    // store → college → home, so co-located radii always resolve to store.
    private var consecutiveInHome = 0
    private var consecutiveInStore = 0
    private var consecutiveInCollege = 0
    private var consecutiveOutHome = 0
    private var consecutiveOutStore = 0
    private var consecutiveOutCollege = 0
    private val CONFIDENCE_THRESHOLD = 3 // 3 readings at 5s = 15 seconds
    // Safety valve: a delivery that never re-enters the store (customer stop,
    // GPS gap) must not stay open forever — point storage is capped downstream,
    // but stats would merge every drop into one trip.
    // Monotonic clock: wall-clock jumps (NTP/DST) must not split trips.
    private var deliveringStartMs = 0L
    private val DELIVERING_TIMEOUT_MS = 2 * 60 * 60 * 1000L // 2 h

    /**
     * Process a new GPS location and determine if a state transition should occur.
     * Non-finite or out-of-range fixes (mock glitches) are ignored, never latched.
     */
    fun processLocation(lat: Double, lon: Double): TransitionResult {
        val previousState = currentState

        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            return TransitionResult(previousState, currentState, false)
        }

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
                // R&D fix: fresh install has no home anchor — don't instantly jump to COMMUTE.
                if (homeAnchor != null && consecutiveOutHome >= CONFIDENCE_THRESHOLD) {
                    currentState = State.UNCLASSIFIED_COMMUTE
                    resetCounters()
                }
            }

            State.UNCLASSIFIED_COMMUTE -> {
                when {
                    consecutiveInStore >= CONFIDENCE_THRESHOLD -> {
                        currentState = State.WAITING_AT_STORE
                        resetCounters()
                    }
                    consecutiveInCollege >= CONFIDENCE_THRESHOLD -> {
                        currentState = State.AT_COLLEGE
                        resetCounters()
                    }
                    consecutiveInHome >= CONFIDENCE_THRESHOLD -> {
                        currentState = State.IDLE_AT_HOME
                        resetCounters()
                    }
                }
            }

            State.AT_COLLEGE -> {
                // R&D fix: hysteresis — require 3 consecutive OUT readings, not single jitter.
                if (consecutiveInCollege == 0 && !inCollege) {
                    consecutiveOutCollege++
                    if (consecutiveOutCollege >= CONFIDENCE_THRESHOLD) {
                        currentState = State.UNCLASSIFIED_COMMUTE
                        resetCounters()
                    }
                } else {
                    consecutiveOutCollege = 0
                }
            }

            State.WAITING_AT_STORE -> {
                if (consecutiveOutStore >= CONFIDENCE_THRESHOLD) {
                    currentState = State.DELIVERING_ORDER
                    deliveringStartMs = clockMs()
                    resetCounters()
                }
            }

            State.DELIVERING_ORDER -> {
                // Close on store return or home return. Timeout valve: a delivery
                // open past 2 h (dead GPS at a customer stop) closes instead of
                // merging every subsequent drop into one trip.
                if (consecutiveInStore >= CONFIDENCE_THRESHOLD) {
                    currentState = State.ORDER_COMPLETE
                    resetCounters()
                } else if (consecutiveInHome >= CONFIDENCE_THRESHOLD) {
                    currentState = State.IDLE_AT_HOME
                    resetCounters()
                } else if (deliveringStartMs > 0 && clockMs() - deliveringStartMs >= DELIVERING_TIMEOUT_MS) {
                    currentState = State.ORDER_COMPLETE
                    resetCounters()
                }
            }

            State.ORDER_COMPLETE -> {
                // Transient state — immediately transition to WAITING_AT_STORE.
                // R&D fix: emit WAITING_AT_STORE on the SAME fix (no 5–60s linger);
                // the returned TransitionResult already carries the new state.
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
        deliveringStartMs = 0L
        resetCounters()
    }

    private fun resetCounters() {
        consecutiveInHome = 0
        consecutiveInStore = 0
        consecutiveInCollege = 0
        consecutiveOutHome = 0
        consecutiveOutStore = 0
        consecutiveOutCollege = 0
        // Terminal transitions clear the valve; entering DELIVERING re-arms it.
        if (currentState == State.ORDER_COMPLETE || currentState == State.IDLE_AT_HOME) {
            deliveringStartMs = 0L
        }
    }
}
