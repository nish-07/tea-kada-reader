package app.teakadai.reader

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The reader. For every notification on the phone it does exactly this, in this order:
 *   1. not set up or no owner consent  -> stop
 *   2. app is not on the payment-app allowlist -> stop (the notification text is never touched)
 *   3. read title and text, look for "money received", then let go of the text
 *   4. send only amount, payer name and app to the shop's server
 * Nothing is logged or saved on the phone.
 */
class PaymentReaderService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val appLabel = Allowlist.label(pkg) ?: return // step 2, before any content is read
        if (!Vault.hasConsent(applicationContext)) return
        val settings = Vault.load(applicationContext) ?: return

        val n = sbn.notification ?: return
        val extras = n.extras ?: return

        // Every piece of text this payment app put in the notification. Only allowlisted apps get this far.
        val pieces = ArrayList<Pair<String, String>>()
        fun add(label: String, v: CharSequence?) { if (!v.isNullOrBlank()) pieces.add(label to v.toString()) }
        add("title", extras.getCharSequence(Notification.EXTRA_TITLE))
        add("text", extras.getCharSequence(Notification.EXTRA_TEXT))
        add("big", extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add("bigtitle", extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
        add("sub", extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        add("summary", extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        add("info", extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        add("ticker", n.tickerText)
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add("line", it) }

        var parsed = PaymentParser.parseAny(pieces.map { it.second })
        if (parsed == null) {
            // Some apps draw their own notification layout; read the words from it.
            val views = customViewTexts(n)
            views.forEach { pieces.add("view" to it) }
            parsed = PaymentParser.parseAny(views)
        }
        // Shown on this phone only: app, time, and for unread ones just the shape of the text (letters and digits masked).
        Recent.noteSeen(appLabel, parsed != null, if (parsed == null) PaymentParser.diagnoseAll(pieces) else "")
        if (parsed == null) return
        val row = Recent.add(appLabel, parsed.amountPaise, parsed.payerName)
        Uploader.send(settings, parsed.amountPaise, parsed.payerName, pkg, row)
    }

    private fun customViewTexts(n: Notification): List<String> {
        val out = ArrayList<String>()
        for (rv in listOfNotNull(n.bigContentView, n.contentView)) {
            try {
                collect(rv.apply(applicationContext, FrameLayout(applicationContext)), out)
            } catch (_: Throwable) {
            }
        }
        return out
    }

    private fun collect(v: View, out: MutableList<String>) {
        if (v is TextView) {
            val t = v.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) out.add(t)
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i), out)
    }

    override fun onListenerDisconnected() {
        // Ask Android to reconnect us if the system dropped the listener.
        try {
            requestRebind(ComponentName(this, PaymentReaderService::class.java))
        } catch (_: Exception) {
        }
    }
}
