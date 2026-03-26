package com.jarvis.assistant.core.commands

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import com.jarvis.assistant.services.JarvisAccessibilityService
import org.json.JSONException
import org.json.JSONObject

class CommandExecutor(private val context: Context) {

    data class Result(
        val speech: String,
        val isCommand: Boolean,
        val requiresConfirmation: Boolean = false,
        val pendingAction: String? = null,
        val pendingParams: JSONObject? = null,
        val pendingJson: JSONObject? = null
    )

    fun tryExecute(response: String): Result {
        val trimmed = response.trim()
        if (!trimmed.startsWith("{")) return Result(trimmed, isCommand = false)

        return try {
            val json = JSONObject(trimmed)
            val action = json.getString("action")
            val params = json.optJSONObject("params") ?: JSONObject()
            val speech = json.optString("speech", "Done.")
            val sensitive = json.optBoolean("sensitive", false)

            if (action == "CHAT") return Result("", isCommand = false)

            if (sensitive) {
                return Result(
                    speech = speech,
                    isCommand = true,
                    requiresConfirmation = true,
                    pendingAction = action,
                    pendingParams = params,
                    pendingJson = json
                )
            }

            dispatch(action, params, json)
            Result(speech, isCommand = true)
        } catch (e: JSONException) {
            Result(trimmed, isCommand = false)
        } catch (e: Exception) {
            Result("Something went wrong: ${e.message}", isCommand = true)
        }
    }

    fun executeConfirmed(action: String, params: JSONObject, fullJson: JSONObject? = null) {
        dispatch(action, params, fullJson)
    }

    private fun dispatch(action: String, params: JSONObject, fullJson: JSONObject?) {
        when (action) {
            "CALL"          -> makeCall(params)
            "SEND_WHATSAPP" -> sendWhatsApp(params)
            "SEND_SMS"      -> sendSms(params)
            "SET_ALARM"     -> setAlarm(params)
            "OPEN_APP"      -> openApp(params)
            "WIFI"          -> toggleWifi()
            "BLUETOOTH"     -> toggleBluetooth()
            "VOLUME"        -> setVolume(params)
            "BRIGHTNESS"    -> setBrightness(params)
            "TAKE_PHOTO"    -> openCamera()
            "PLAY_MUSIC"    -> playMusic(params)
            "NAVIGATE"      -> navigate(params)
            "GOOGLE_SEARCH" -> googleSearch(params)
            "MULTI_STEP"    -> executeMultiStep(fullJson)
            else            -> Log.w(TAG, "Unknown action: $action")
        }
    }

    private fun makeCall(p: JSONObject) {
        val number = resolveContactNumber(p.optString("contact")) ?: p.optString("contact")
        context.startActivity(
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun sendWhatsApp(p: JSONObject) {
        val number = resolveContactNumber(p.optString("contact")) ?: p.optString("contact")
        val message = Uri.encode(p.optString("message"))
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$message")).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            Handler(Looper.getMainLooper()).postDelayed(
                { JarvisAccessibilityService.instance?.tapSendButton() },
                3500L
            )
        } catch (e: Exception) {
            googleSearch(JSONObject().put("query", "whatsapp web $number"))
        }
    }

    private fun sendSms(p: JSONObject) {
        val number = resolveContactNumber(p.optString("contact")) ?: p.optString("contact")
        context.startActivity(
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", p.optString("message"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun setAlarm(p: JSONObject) {
        context.startActivity(
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, p.optInt("hour", 7))
                putExtra(AlarmClock.EXTRA_MINUTES, p.optInt("minute", 0))
                putExtra(AlarmClock.EXTRA_MESSAGE, p.optString("label", "Jarvis Alarm"))
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun openApp(p: JSONObject) {
        val packageName = resolvePackageName(p.optString("name"))
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun toggleWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.startActivity(
                Intent("android.settings.panel.action.WIFI").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun toggleBluetooth() {
        context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun setVolume(p: JSONObject) {
        val level = p.optInt("level", 50)
        JarvisAccessibilityService.instance?.setVolume(level) ?: run {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (level / 100.0 * max).toInt().coerceIn(0, max), 0)
        }
    }

    private fun setBrightness(p: JSONObject) {
        JarvisAccessibilityService.instance?.setBrightness(p.optInt("level", 80))
    }

    private fun openCamera() {
        context.startActivity(
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun playMusic(p: JSONObject) {
        val query = p.optString("query")
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            googleSearch(JSONObject().put("query", "play $query"))
        }
    }

    private fun navigate(p: JSONObject) {
        val destination = Uri.encode(p.optString("destination"))
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$destination")).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$destination"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun googleSearch(p: JSONObject) {
        context.startActivity(
            Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, p.optString("query"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun executeMultiStep(fullJson: JSONObject?) {
        val steps = fullJson?.optJSONArray("steps") ?: return
        val handler = Handler(Looper.getMainLooper())
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            handler.postDelayed({
                try {
                    dispatch(step.getString("action"), step.optJSONObject("params") ?: JSONObject(), null)
                } catch (e: Exception) {
                    Log.e(TAG, "Multi-step step $i failed", e)
                }
            }, i * 2500L)
        }
    }

    private fun resolveContactNumber(name: String): String? {
        if (name.matches(Regex("[+\\d\\s()\\-]+"))) return name
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            null, null, null
        ) ?: return null
        cursor.use {
            while (it.moveToNext()) {
                val displayName = it.getString(1) ?: continue
                if (displayName.contains(name, ignoreCase = true)) {
                    return it.getString(0)?.replace("\\s".toRegex(), "")
                }
            }
        }
        return null
    }

    private fun resolvePackageName(name: String): String = APP_PACKAGES[name.lowercase()]
        ?: "com.android.${name.lowercase().replace(" ", "")}"

    companion object {
        private const val TAG = "CommandExecutor"

        private val APP_PACKAGES = mapOf(
            "whatsapp"   to "com.whatsapp",
            "instagram"  to "com.instagram.android",
            "youtube"    to "com.google.android.youtube",
            "spotify"    to "com.spotify.music",
            "chrome"     to "com.android.chrome",
            "maps"       to "com.google.android.apps.maps",
            "gmail"      to "com.google.android.gm",
            "settings"   to "com.android.settings",
            "telegram"   to "org.telegram.messenger",
            "netflix"    to "com.netflix.mediaclient",
            "twitter"    to "com.twitter.android",
            "snapchat"   to "com.snapchat.android",
            "facebook"   to "com.facebook.katana",
            "camera"     to "com.android.camera2",
            "calculator" to "com.android.calculator2",
            "clock"      to "com.android.deskclock",
            "phone"      to "com.android.dialer",
            "messages"   to "com.google.android.apps.messaging",
            "photos"     to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "zoom"       to "us.zoom.videomeetings"
        )
    }
}
