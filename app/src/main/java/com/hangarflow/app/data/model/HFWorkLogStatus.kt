package com.hangarflow.app.data.model

import androidx.compose.ui.graphics.Color
import com.hangarflow.app.ui.theme.HFColors

/**
 * Status values written to `hf_work_logs.status`. Kept loose (a plain
 * String column on the DB) so we degrade gracefully if a future iOS
 * release adds a new status before Android ships the same version.
 */
enum class HFWorkLogStatus(val raw: String, val label: String, val color: Color) {
    Open("open", "Open", HFColors.StatusBlue),
    InProgress("inProgress", "In Progress", HFColors.StatusYellow),
    WaitingOnParts("waitingOnParts", "Waiting on Parts", HFColors.StatusOrange),
    Done("done", "Done", HFColors.StatusGreen),
    Review("review", "Review", HFColors.StatusPurple);

    companion object {
        fun fromRaw(raw: String?): HFWorkLogStatus =
            entries.firstOrNull { it.raw == raw } ?: Open
    }
}

/**
 * High-level filter buckets the sidebar exposes. Multiple underlying
 * statuses collapse into a single filter pill — matches the Mac UI.
 */
enum class HFStatusFilter(val label: String) {
    All("All"),
    Active("Active"),
    Waiting("Waiting"),
    Done("Done"),
    Review("Review");

    fun matches(status: HFWorkLogStatus): Boolean = when (this) {
        All -> true
        Active -> status == HFWorkLogStatus.Open || status == HFWorkLogStatus.InProgress
        Waiting -> status == HFWorkLogStatus.WaitingOnParts
        Done -> status == HFWorkLogStatus.Done
        Review -> status == HFWorkLogStatus.Review
    }
}
