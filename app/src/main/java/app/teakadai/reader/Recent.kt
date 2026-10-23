package app.teakadai.reader

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * The last few payments, kept in memory only, so the owner can see the reader working.
 * Nothing here is written to disk: it is gone when the app process ends or when "Forget this shop" is tapped.
 */
object Recent {
    data class Row(val time: String, val app: String, val amount: String, val payer: String, var outcome: String)

    private val rows = ArrayDeque<Row>()
    @Volatile var onChange: (() -> Unit)? = null

    @Synchronized fun add(app: String, paise: Long, payer: String?): Row {
        val r = Row(
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
            app, PaymentParser.format(paise), payer ?: "-", "sending...",
        )
        rows.addFirst(r)
        while (rows.size > 8) rows.removeLast()
        onChange?.invoke()
        return r
    }

    @Synchronized fun update(row: Row, outcome: String) {
        row.outcome = outcome
        onChange?.invoke()
    }

    /** Only app name, time and "read" / "not understood" per payment app. Never any notification text. */
    private val seen = LinkedHashMap<String, String>()

    @Synchronized fun noteSeen(app: String, understood: Boolean) {
        seen[app] = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + " - " + (if (understood) "read" else "not understood")
        onChange?.invoke()
    }

    @Synchronized fun seenSnapshot(): List<String> = seen.entries.map { it.key + ": " + it.value }

    @Synchronized fun snapshot(): List<Row> = rows.toList()

    @Synchronized fun clear() {
        rows.clear()
        seen.clear()
        onChange?.invoke()
    }
}
