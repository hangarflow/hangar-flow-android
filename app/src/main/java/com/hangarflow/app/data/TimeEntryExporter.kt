package com.hangarflow.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hangarflow.app.data.model.HFTimeEntry
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Exports time entries to a .csv file and opens the Android share sheet
 * so the admin can email it, AirDrop it, or save it to Drive.
 */
object TimeEntryExporter {

    fun export(context: Context, entries: List<HFTimeEntry>, label: String = "time_entries") {
        val csv = buildCsv(entries)
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val fileName = "hangar_flow_${label}_$date.csv"
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(csv) }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Hangar Flow Time Report — $date")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Export time entries").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun buildCsv(entries: List<HFTimeEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("Date,User,Minutes,Hours,Plane,Notes,Linked Work Log")
        entries.sortedByDescending { it.entryDate }.forEach { e ->
            val date = e.entryDate.take(10)
            val user = csvEscape(e.userName)
            val minutes = e.minutesWorked
            val hours = "%.2f".format(minutes / 60.0)
            val plane = csvEscape(e.planeTailNumber ?: "")
            val notes = csvEscape(e.notes)
            val workLog = csvEscape(e.linkedWorkLogId ?: "")
            sb.appendLine("$date,$user,$minutes,$hours,$plane,$notes,$workLog")
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val cleaned = value.replace("\"", "\"\"")
        return if (cleaned.contains(',') || cleaned.contains('"') || cleaned.contains('\n'))
            "\"$cleaned\"" else cleaned
    }
}
