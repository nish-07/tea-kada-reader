package app.teakadai.reader

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Sends "amount, payer name, source app" to the shop's own server over HTTPS. Nothing else leaves the phone.
 * A failed send is retried from memory for about two minutes with the same client_event_id, so the server
 * can never count a payment twice.
 */
object Uploader {
    private val pool: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val retryDelays = longArrayOf(3, 10, 25, 60) // seconds, about 100 s in total

    enum class Result { OK, RETRY, REJECTED }

    /** Server address must be https:// with a host. Returns the cleaned base URL or null. */
    fun cleanBaseUrl(raw: String): String? {
        val t = raw.trim().trimEnd('/')
        if (!t.startsWith("https://", ignoreCase = true)) return null
        return try {
            val u = URL(t)
            if (u.host.isNullOrBlank() || u.userInfo != null) null else t
        } catch (e: Exception) {
            null
        }
    }

    fun send(s: Vault.Settings, paise: Long, payer: String?, sourcePackage: String, row: Recent.Row) {
        val eventId = UUID.randomUUID().toString()
        attempt(s, paise, payer, sourcePackage, eventId, row, 0)
    }

    private fun attempt(s: Vault.Settings, paise: Long, payer: String?, pkg: String, id: String, row: Recent.Row, n: Int) {
        pool.execute {
            val (res, text) = post(s, paise, payer, pkg, id)
            when {
                res == Result.OK -> Recent.update(row, text)
                res == Result.REJECTED -> Recent.update(row, text)
                n < retryDelays.size -> {
                    Recent.update(row, "no connection, retrying")
                    pool.schedule({ attempt(s, paise, payer, pkg, id, row, n + 1) }, retryDelays[n], TimeUnit.SECONDS)
                }
                else -> Recent.update(row, "not sent - tap Mark paid on the screen")
            }
        }
    }

    private fun post(s: Vault.Settings, paise: Long, payer: String?, pkg: String, id: String): Pair<Result, String> {
        val base = cleanBaseUrl(s.serverUrl) ?: return Result.REJECTED to "server address must start with https://"
        var c: HttpURLConnection? = null
        return try {
            c = (URL("$base/v1/owner/shops/${s.shopSlug}/payments").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 10000
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-owner-token", s.ownerCode)
            }
            val body = JSONObject().put("amount_paise", paise).put("source_app", pkg).put("client_event_id", id)
            if (payer != null) body.put("payer_name", payer)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            when {
                code in 200..299 -> Result.OK to describe(text)
                code == 429 || code >= 500 -> Result.RETRY to "server busy"
                code == 401 -> Result.REJECTED to "owner code not accepted"
                code == 403 -> Result.REJECTED to "shop is switched off"
                else -> Result.REJECTED to "server said $code"
            }
        } catch (e: Exception) {
            Result.RETRY to "no connection"
        } finally {
            c?.disconnect()
        }
    }

    private fun describe(json: String): String = try {
        when (JSONObject(json).optString("result")) {
            "matched" -> "order confirmed"
            "unmatched" -> "no matching order"
            "duplicate" -> "duplicate ignored"
            else -> "sent"
        }
    } catch (e: Exception) {
        "sent"
    }

    /** "Test connection": asks the server for the live order list with the owner code. Reads nothing from the phone. */
    fun test(s: Vault.Settings): String {
        val base = cleanBaseUrl(s.serverUrl) ?: return "Server address must start with https://"
        var c: HttpURLConnection? = null
        return try {
            c = (URL("$base/v1/owner/shops/${s.shopSlug}/orders").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                instanceFollowRedirects = false
                setRequestProperty("x-owner-token", s.ownerCode)
            }
            when (val code = c.responseCode) {
                200 -> "Connected. The shop and owner code are correct."
                401 -> "The shop name or owner code is wrong."
                403 -> "This shop is switched off."
                else -> "The server answered $code."
            }
        } catch (e: Exception) {
            "Could not reach the server. Check the address and the internet."
        } finally {
            c?.disconnect()
        }
    }
}
