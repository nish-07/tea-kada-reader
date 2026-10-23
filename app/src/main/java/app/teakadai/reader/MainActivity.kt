package app.teakadai.reader

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

/** One screen that shows the right step: set up, consent, turn on access, then the live status. */
class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private var message: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Payer names must not appear in screenshots or the recent-apps preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.WHITE) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        Recent.onChange = { runOnUiThread { render() } }
        render()
    }

    override fun onPause() {
        super.onPause()
        Recent.onChange = null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float = 16f, bold: Boolean = false, bottom: Int = 12): TextView =
        TextView(this).apply {
            this.text = s
            textSize = size
            setTextColor(Color.BLACK)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(bottom))
            root.addView(this)
        }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.BLACK)
            setOnClickListener { onClick() }
            root.addView(this)
        }

    private fun field(hint: String, value: String, secret: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(value)
            setTextColor(Color.BLACK)
            setSingleLine()
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            root.addView(this)
        }

    private fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { it.startsWith("$packageName/") }
    }

    private fun render() {
        root.removeAllViews()
        text("Tea Kadai Reader", 24f, bold = true, bottom = 4)
        text("Confirms customer payments on this phone.", 15f, bottom = 18)

        val settings = Vault.load(this)
        when {
            settings == null -> renderSetup()
            !Vault.hasConsent(this) -> renderConsent()
            !listenerEnabled() -> renderEnableAccess()
            else -> renderStatus(settings)
        }
        if (message.isNotEmpty()) text(message, 15f, bold = true, bottom = 12)
    }

    private fun renderSetup() {
        text("Step 1 of 3: Your shop", 18f, bold = true)
        text("Enter the details you were given for your shop.", 15f)
        val url = field("Server address (https://...)", "https://")
        val shop = field("Shop name (as in the menu link)", "")
        val code = field("Owner code", "", secret = true)
        button("Save and test") {
            val base = Uploader.cleanBaseUrl(url.text.toString())
            val slug = shop.text.toString().trim().lowercase()
            val owner = code.text.toString().trim()
            when {
                base == null -> { message = "The server address must start with https://"; render() }
                !Regex("^[a-z0-9-]{3,60}$").matches(slug) -> { message = "Shop name looks wrong."; render() }
                owner.length < 8 -> { message = "Owner code looks too short."; render() }
                else -> {
                    val s = Vault.Settings(base, slug, owner)
                    message = "Testing..."
                    render()
                    thread {
                        val result = Uploader.test(s)
                        runOnUiThread {
                            if (result.startsWith("Connected")) {
                                if (Vault.save(this, s)) message = result else message = "This phone could not store the code safely."
                            } else message = result
                            render()
                        }
                    }
                }
            }
        }
    }

    private fun renderConsent() {
        text("Step 2 of 3: Your permission", 18f, bold = true)
        text(
            "Tea Kadai Reader can read the notifications that your payment apps (Google Pay, PhonePe, Paytm, BHIM, " +
                "Airtel Thanks) show when money arrives, so that your customer's order turns green by itself.",
            15f,
        )
        text("What it reads", 16f, bold = true, bottom = 4)
        text("Only notifications from those payment apps. Notifications from every other app, such as WhatsApp, " +
            "messages and Gmail, are never opened.", 15f)
        text("What it sends", 16f, bold = true, bottom = 4)
        text("Only three things, to your own shop server over a secure connection: the amount, the payer's name as " +
            "shown in the notification, and which app it came from. The notification text is not saved and is not sent.", 15f)
        text("What it never does", 16f, bold = true, bottom = 4)
        text("It never sells or shares this data, never shows it to other shops, and never stores notifications on this phone.", 15f)
        text("You are in control", 16f, bold = true, bottom = 4)
        text("You can turn it off at any time here or in the phone's settings. If you say no, the app still works: " +
            "customers tap \"I've paid\" and you tap \"Mark paid\".", 15f)
        button("Agree") {
            Vault.saveConsent(this, true)
            message = ""
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            render()
        }
        button("Not now") { message = "Reader is off. You can mark payments yourself."; render() }
        button("Forget this shop") { forget() }
    }

    private fun renderEnableAccess() {
        text("Step 3 of 3: Turn on notification access", 18f, bold = true)
        text("In the next screen, switch on \"Tea Kadai Reader\".", 15f)
        text(
            "If Android says \"App was denied access\": open the phone's Settings > Apps > Tea Kadai Reader, tap the three " +
                "dots at the top right, tap \"Allow restricted settings\", then switch it on again.", 14f,
        )
        button("Open notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button("Open app info") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:$packageName")),
            )
        }
        button("Stop (withdraw permission)") { Vault.saveConsent(this, false); message = "Reader is off."; render() }
    }

    private fun renderStatus(s: Vault.Settings) {
        text("Reader is ON", 20f, bold = true, bottom = 4)
        text("Shop: ${s.shopSlug}", 15f, bottom = 4)
        text("Watching only: " + Allowlist.PAYMENT_APPS.values.joinToString(", "), 14f, bottom = 16)
        text("Last notification seen from each payment app (time only, no text):", 14f, bottom = 4)
        val seenRows = Recent.seenSnapshot()
        if (seenRows.isEmpty()) text("None yet.", 14f, bottom = 8)
        for (line in seenRows) text(line, 14f, bottom = 2)
        text("", 6f, bottom = 8)
        text("Recent payments (kept in memory only)", 16f, bold = true, bottom = 6)
        val rows = Recent.snapshot()
        if (rows.isEmpty()) text("Nothing yet. Ask a friend to pay you Rs 1.", 15f)
        for (r in rows) text("${r.time}  ${r.amount}  ${r.payer}\n${r.app} - ${r.outcome}", 14f, bottom = 8)
        button("Test connection") {
            message = "Testing..."
            render()
            thread {
                val result = Uploader.test(s)
                runOnUiThread { message = result; render() }
            }
        }
        button("Stop reading (withdraw permission)") {
            Vault.saveConsent(this, false)
            Recent.clear()
            message = "Reader is off. You can mark payments yourself."
            render()
        }
        button("Forget this shop") { forget() }
        text("To remove the notification access completely: phone Settings > Notifications > Device & app notifications.", 13f)
        root.getChildAt(root.childCount - 1).visibility = View.VISIBLE
    }

    private fun forget() {
        Vault.wipe(this)
        Recent.clear()
        message = "This phone has forgotten the shop."
        render()
    }
}
