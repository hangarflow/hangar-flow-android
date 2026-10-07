package com.hangarflow.app.data.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which feature just changed on somebody else's device.
 *
 * The existing realtime path had exactly one reaction to an incoming org
 * event: pull the whole snapshot — eighteen tables. That is correct and was
 * cheap while events were rare, but the logbook, part releases, the guided
 * walkthrough and the troubleshooting tree all write per *step*: a mechanic
 * ticking twenty walkthrough steps would have cost twenty full snapshot pulls
 * on every other device in the shop. That is what this exists to prevent.
 *
 * So each of those writes announces a TYPE (see `hfAnnounce`), and a type the
 * receiver recognises bumps one counter here instead of pulling anything. An
 * unrecognised type still falls through to the snapshot — a new event type
 * added later is stale-but-correct rather than silently ignored.
 *
 * The counters are the signal rather than the data on purpose. These features
 * have no in-memory store on this client (unlike Apple's `HFLogbookStore`);
 * every screen fetches what it needs when it opens. A screen that is not open
 * should not fetch at all, so the counter goes in that screen's
 * `LaunchedEffect` key list and the fetch happens only where someone is
 * actually looking. Nothing on screen → nothing on the wire.
 */
object HFFeatureEvents {

    private val _logbook = MutableStateFlow(0L)
    private val _partRelease = MutableStateFlow(0L)
    private val _guidedRun = MutableStateFlow(0L)
    private val _troubleshoot = MutableStateFlow(0L)
    private val _equipmentCustody = MutableStateFlow(0L)

    /** Bumps every time another device writes to an aircraft log. */
    val logbook: StateFlow<Long> get() = _logbook

    /** Bumps on a receiving-inspection write — a release saved, accepted, rejected. */
    val partRelease: StateFlow<Long> get() = _partRelease

    /** Bumps on a guided-walkthrough step or close-out. */
    val guidedRun: StateFlow<Long> get() = _guidedRun

    /** Bumps on a troubleshooting-tree outcome or close-out. */
    val troubleshoot: StateFlow<Long> get() = _troubleshoot

    /**
     * Bumps when a tool changes hands anywhere in the shop.
     *
     * The highest-frequency event of the five and the one that most needs to
     * be instant: the whole feature is worthless if the mechanic in the other
     * hangar is looking at who had the wrench an hour ago.
     */
    val equipmentCustody: StateFlow<Long> get() = _equipmentCustody

    /**
     * Routes one incoming event type.
     *
     * Returns true when this was a known, feature-scoped event and the caller
     * can skip the snapshot pull entirely; false when the caller still owes a
     * full reconcile. Anything unknown — including `snapshot_push` itself and
     * any event type a future release adds — returns false deliberately.
     */
    fun consume(eventType: String): Boolean {
        when (eventType) {
            "logbook_changed" -> _logbook.value += 1
            "part_release_changed" -> _partRelease.value += 1
            "guided_run_changed" -> _guidedRun.value += 1
            "troubleshoot_changed" -> _troubleshoot.value += 1
            "equipment_custody_changed" -> _equipmentCustody.value += 1
            else -> return false
        }
        return true
    }
}
