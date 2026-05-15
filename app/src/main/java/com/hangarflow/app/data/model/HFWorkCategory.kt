package com.hangarflow.app.data.model

/**
 * Matches the iOS `HFWorkCategory` enum. String values stored in
 * `hf_work_logs.category` are the raw keys; display names mirror the
 * Mac admin UI label set so admins see identical terminology.
 */
enum class HFWorkCategory(val raw: String, val label: String) {
    Airframe("airframe", "Airframe"),
    Engine("engine", "Engine"),
    Propeller("propeller", "Propeller"),
    Avionics("avionics", "Avionics"),
    Interior("interior", "Interior"),
    Inspection("inspection", "Inspection"),
    Assigned("assigned", "Assigned"),
    LandingGear("landingGear", "Landing Gear"),
    Documents("documents", "Documents"),
    Review("review", "Review"),
    Misc("misc", "Misc"),
    General("general", "General");

    companion object {
        fun fromRaw(raw: String?): HFWorkCategory =
            entries.firstOrNull { it.raw == raw } ?: General
    }
}
