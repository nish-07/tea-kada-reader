package app.teakadai.reader

/**
 * The ONLY apps whose notifications are ever looked at. A notification from any other app is dropped
 * before its title or text is even read.
 *
 * Package names are matched exactly. Add a business app here only after checking its package name on a real phone.
 */
object Allowlist {
    /** Payment apps a customer's money can arrive in. */
    val PAYMENT_APPS: Map<String, String> = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.google.android.apps.nbu.paisa.merchant" to "Google Pay for Business",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "com.paytm.business" to "Paytm for Business",
        "in.org.npci.upiapp" to "BHIM",
        "com.myairtelapp" to "Airtel Thanks",
    )

    /**
     * Bank SMS and bank e-mail alerts live inside the SMS app and Gmail. Reading them would mean reading every
     * message in those apps, which Google Play's SMS policy does not allow. Kept OFF. Do not turn on for a Play release.
     */
    const val READ_SMS_AND_EMAIL_APPS = false

    private val MESSAGE_APPS: Map<String, String> = mapOf(
        "com.google.android.apps.messaging" to "Messages",
        "com.google.android.gm" to "Gmail",
    )

    fun label(pkg: String): String? =
        PAYMENT_APPS[pkg] ?: if (READ_SMS_AND_EMAIL_APPS) MESSAGE_APPS[pkg] else null

    fun isAllowed(pkg: String): Boolean = label(pkg) != null
}
