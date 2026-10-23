package app.teakadai.reader

import java.util.Locale

/** What the reader keeps from a notification: nothing else is ever stored or sent. */
data class ParsedPayment(val amountPaise: Long, val payerName: String?)

/**
 * Turns a payment-app notification into "money came IN: amount and payer name", or null.
 * Pure Kotlin, no Android classes, so it can be tested anywhere.
 *
 * Only credit wording is accepted. Anything that merely mentions a rupee amount (chat, offers, cashback,
 * requests, money going out, balance messages) returns null.
 */
object PaymentParser {
    const val MAX_PAISE = 1_000_000L // Rs 10,000, same cap as the server

    private const val AMT = """(?<![A-Za-z])(?:₹|Rs\.?|INR)\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val opts = setOf(RegexOption.IGNORE_CASE)

    private val rejectWords = Regex(
        """\b(cashback|cash back|reward|rewards|offer|offers|coupon|scratch|refund|refunded|request|requested|requests|reminder|""" +
            """remind|failed|declined|expired|pending|debited|withdrawn|spent|you paid|you sent|paid to|sent to|payment successful|""" +
            """money sent|bill|due|emi|loan|win|earn|save|saved|bachaye|get up to)\b""",
        opts,
    )

    // "N MUTHAIAH paid you ₹1.00"   "Raj sent you ₹5"
    private val paidYou = Regex("""^\s*(.{1,80}?)\s+(?:paid|sent)\s+you\s+$AMT""", opts)
    // "Received ₹1 from RAJ KUMAR"   "Payment received ₹1 from RAJ"
    private val receivedFrom = Regex(
        """received\s+$AMT\s+from\s+(.{1,80}?)(?=\s+(?:on|via|using|in|to|through|with|at|ref|upi|deposited|credited)\b|[.,;:(]|$)""", opts,
    )
    // "₹1 received from RAJ"   "₹1 credited"
    private val amountReceived = Regex(
        """$AMT\s+(?:received|credited)(?:\s+(?:from|by)\s+(.{1,80}?)(?=\s+(?:on|via|using|in|to|through|with|at|ref|upi|deposited|credited)\b|[.,;:(]|$))?""", opts,
    )
    // "Rs.100.37 credited to A/c"   "A/c credited with Rs 45"   "deposited ₹45"
    private val creditedAmount = Regex("""(?:credited|deposited)(?:\s+with)?\s+$AMT""", opts)

    /** Tries the title, then the body, then both together. */
    fun parse(title: String, text: String): ParsedPayment? {
        for (candidate in listOf(title, text, "$title $text")) {
            val c = candidate.trim()
            if (c.isEmpty()) continue
            val r = parseOne(c)
            if (r != null) return r
        }
        return null
    }

    private fun parseOne(s: String): ParsedPayment? {
        if (rejectWords.containsMatchIn(s)) return null

        paidYou.find(s)?.let { return build(it.groupValues[2], it.groupValues[1]) }
        receivedFrom.find(s)?.let { return build(it.groupValues[1], it.groupValues[2]) }
        amountReceived.find(s)?.let { return build(it.groupValues[1], it.groupValues[2]) }
        creditedAmount.find(s)?.let { return build(it.groupValues[1], null) }
        return null
    }

    private fun build(rawAmount: String, rawName: String?): ParsedPayment? {
        val paise = toPaise(rawAmount) ?: return null
        if (paise <= 0 || paise > MAX_PAISE) return null
        return ParsedPayment(paise, cleanName(rawName))
    }

    fun toPaise(raw: String): Long? {
        val parts = raw.replace(",", "").split(".")
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L else 0L
        return rupees * 100 + paise
    }

    /** Keeps letters, digits, spaces and . - ' only; max 60 characters; empty becomes null. */
    fun cleanName(raw: String?): String? {
        if (raw == null) return null
        val kept = raw.filter { it.isLetterOrDigit() || it == ' ' || it == '.' || it == '-' || it == '\'' }
        val n = kept.replace(Regex("""\s+"""), " ").trim().trimEnd('.', '-', '\'').trim().take(60).trim()
        return if (n.length >= 2) n else null
    }

    fun format(paise: Long): String = "₹" + (paise / 100) + "." + (paise % 100).toString().padStart(2, '0')

    @Suppress("unused")
    private fun lower(s: String) = s.lowercase(Locale.ROOT)
}
