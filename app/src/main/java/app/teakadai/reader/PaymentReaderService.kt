package app.teakadai.reader

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

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

        val extras = sbn.notification?.extras ?: return
        // Some apps leave the normal title or text empty and use the "big" or ticker version instead.
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
            ?.toString().orEmpty().ifBlank { sbn.notification?.tickerText?.toString().orEmpty() }
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()

        val parsed = PaymentParser.parse(title, text) ?: return
        val row = Recent.add(appLabel, parsed.amountPaise, parsed.payerName)
        Uploader.send(settings, parsed.amountPaise, parsed.payerName, pkg, row)
    }

    override fun onListenerDisconnected() {
        // Ask Android to reconnect us if the system dropped the listener.
        try {
            requestRebind(ComponentName(this, PaymentReaderService::class.java))
        } catch (_: Exception) {
        }
    }
}
