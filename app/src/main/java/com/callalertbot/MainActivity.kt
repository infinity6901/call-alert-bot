package com.callalertbot

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.callalertbot.databinding.ActivityMainBinding
import com.callalertbot.databinding.ItemActivityBinding
import com.callalertbot.databinding.ItemOptionBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val activityListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> renderActivity() }

    private enum class Tone { NEUTRAL, SUCCESS, ERROR }

    private class OptionRow(val option: Config.Option, val title: Int, val desc: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tokenInput.setText(Config.token(this))
        b.chatInput.setText(Config.chatId(this))

        bindSwitch(b.switchEnabled, Config.Option.ENABLED)
        for (spec in OPTION_ROWS) {
            val item = ItemOptionBinding.inflate(layoutInflater, b.optionsList, false)
            item.optionTitle.setText(spec.title)
            item.optionDesc.setText(spec.desc)
            bindSwitch(item.optionSwitch, spec.option)
            item.root.setOnClickListener { item.optionSwitch.toggle() }
            b.optionsList.addView(item.root)
        }

        b.buttonSaveTest.setOnClickListener { saveAndTest() }
        b.buttonFindChat.setOnClickListener { findChatId() }
        b.buttonGrant.setOnClickListener { askForPermissions() }
        b.buttonBattery.setOnClickListener { openBatterySettings() }
        b.buttonClearLog.setOnClickListener {
            ActivityLog.clear(this)
            renderActivity()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        renderActivity()
        ActivityLog.prefs(this).registerOnSharedPreferenceChangeListener(activityListener)
    }

    override fun onPause() {
        ActivityLog.prefs(this).unregisterOnSharedPreferenceChangeListener(activityListener)
        saveFields()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    // ------------------------------------------------------------------------------------ settings

    private fun bindSwitch(view: CompoundButton, option: Config.Option) {
        view.isChecked = Config.isOn(this, option)
        view.setOnCheckedChangeListener { _, on ->
            Config.set(this, option, on)
            refresh()
        }
    }

    /** Saves what is typed in the two fields, tidying up the token on the way. */
    private fun saveFields() {
        val token = Telegram.normalizeToken(b.tokenInput.text?.toString().orEmpty())
        val chat = b.chatInput.text?.toString().orEmpty().trim()
        if (token != Config.token(this) || chat != Config.chatId(this)) {
            Config.save(this, token, chat)
        }
        if (b.tokenInput.text?.toString() != token) b.tokenInput.setText(token)
    }

    // ------------------------------------------------------------------------------------ Telegram

    private fun saveAndTest() {
        saveFields()
        val token = Config.token(this)
        val chatId = Config.chatId(this)
        when {
            token.isBlank() -> return showTelegram(getString(R.string.error_need_token), Tone.ERROR)
            chatId.isBlank() -> return showTelegram(getString(R.string.error_need_chat), Tone.ERROR)
        }

        setBusy(true)
        showTelegram(getString(R.string.checking), Tone.NEUTRAL)
        val silent = Config.isOn(this, Config.Option.SILENT)
        val testMessage = getString(R.string.test_message)
        val testLog = getString(R.string.test_log)
        val app = applicationContext
        thread {
            val bot = Telegram.verifyBot(token)
            if (!bot.ok) {
                finishRequest(Telegram.friendly(bot), Tone.ERROR)
                return@thread
            }
            val sent = Telegram.sendMessage(token, chatId, testMessage, silent)
            ActivityLog.add(app, "test", testLog, sent.ok, if (sent.ok) "" else Telegram.friendly(sent))
            if (sent.ok) {
                finishRequest(getString(R.string.test_success, bot.value.orEmpty()), Tone.SUCCESS)
            } else {
                finishRequest(Telegram.friendly(sent), Tone.ERROR)
            }
        }
    }

    private fun findChatId() {
        saveFields()
        val token = Config.token(this)
        if (token.isBlank()) return showTelegram(getString(R.string.error_need_token), Tone.ERROR)

        setBusy(true)
        showTelegram(getString(R.string.checking), Tone.NEUTRAL)
        thread {
            val chat = Telegram.detectChat(token)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                setBusy(false)
                if (chat.ok) {
                    b.chatInput.setText(chat.value)
                    saveFields()
                    showTelegram(getString(R.string.chat_found, chat.message.ifBlank { chat.value.orEmpty() }), Tone.SUCCESS)
                } else {
                    showTelegram(Telegram.friendly(chat), Tone.ERROR)
                }
            }
        }
    }

    private fun finishRequest(message: String, tone: Tone) {
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            setBusy(false)
            showTelegram(message, tone)
        }
    }

    private fun setBusy(busy: Boolean) {
        b.buttonSaveTest.isEnabled = !busy
        b.buttonFindChat.isEnabled = !busy
    }

    private fun showTelegram(message: String, tone: Tone) {
        b.telegramStatus.text = message
        b.telegramStatus.setTextColor(
            getColor(
                when (tone) {
                    Tone.SUCCESS -> R.color.cab_ok_text
                    Tone.ERROR -> R.color.cab_error_text
                    Tone.NEUTRAL -> R.color.cab_neutral_text
                }
            )
        )
        b.telegramStatus.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------------------------ status

    private fun refresh() {
        val callsOk = granted(Manifest.permission.READ_PHONE_STATE) && granted(Manifest.permission.READ_CALL_LOG)
        val contactsOk = granted(Manifest.permission.READ_CONTACTS)
        val batteryOk = ignoringBatteryOptimizations()

        when {
            !Config.isOn(this, Config.Option.ENABLED) -> showStatus(
                R.drawable.ic_pause_circle, R.string.status_paused_title, R.string.status_paused_body,
                R.color.cab_paused_bg, R.color.cab_paused_fg
            )
            !Config.isConfigured(this) -> showStatus(
                R.drawable.ic_warning, R.string.status_setup_title, R.string.status_need_telegram,
                R.color.cab_warn_bg, R.color.cab_warn_fg
            )
            !callsOk -> showStatus(
                R.drawable.ic_warning, R.string.status_setup_title, R.string.status_need_permissions,
                R.color.cab_warn_bg, R.color.cab_warn_fg
            )
            else -> showStatus(
                R.drawable.ic_check_circle, R.string.status_active_title, R.string.status_active_body,
                R.color.cab_ok_bg, R.color.cab_ok_fg
            )
        }

        showRow(b.rowCalls, callsOk, required = true)
        showRow(b.rowContacts, contactsOk, required = false)
        showRow(b.rowBattery, batteryOk, required = false)
        b.buttonGrant.visibility = if (callsOk && contactsOk) View.GONE else View.VISIBLE
        b.buttonBattery.visibility = if (batteryOk) View.GONE else View.VISIBLE
    }

    private fun showStatus(icon: Int, title: Int, body: Int, background: Int, foreground: Int) {
        val fg = getColor(foreground)
        b.statusCard.setCardBackgroundColor(getColor(background))
        b.statusIcon.setImageResource(icon)
        b.statusIcon.imageTintList = ColorStateList.valueOf(fg)
        b.statusTitle.setText(title)
        b.statusTitle.setTextColor(fg)
        b.statusSubtitle.setText(body)
        b.statusSubtitle.setTextColor(fg)
    }

    private fun showRow(view: TextView, ok: Boolean, required: Boolean) {
        val icon = when {
            ok -> R.drawable.ic_check_circle
            required -> R.drawable.ic_cancel
            else -> R.drawable.ic_warning
        }
        val color = when {
            ok -> R.color.cab_ok_text
            required -> R.color.cab_error_text
            else -> R.color.cab_warn_text
        }
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        view.compoundDrawableTintList = ColorStateList.valueOf(getColor(color))
    }

    // ------------------------------------------------------------------------------------ permissions

    private fun granted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun askForPermissions() {
        val missing = PERMISSIONS.filter { !granted(it) }
        if (missing.isEmpty()) return

        val ui = getSharedPreferences("ui", MODE_PRIVATE)
        val askedBefore = ui.getBoolean("asked_permissions", false)
        // After Android stops showing the permission dialog, the only way forward is the app's settings page.
        if (askedBefore && missing.none { shouldShowRequestPermissionRationale(it) }) {
            openAppSettings()
            return
        }
        ui.edit().putBoolean("asked_permissions", true).apply()
        requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    private fun ignoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    // ------------------------------------------------------------------------------------ activity log

    private fun renderActivity() {
        val entries = ActivityLog.entries(this)
        b.activityList.removeAllViews()
        b.activityEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        b.buttonClearLog.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE

        for (entry in entries.take(MAX_SHOWN)) {
            val row = ItemActivityBinding.inflate(layoutInflater, b.activityList, false)
            row.entryText.text = entry.text
            val whenText = DateUtils.getRelativeDateTimeString(
                this, entry.time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0
            )
            val outcome = if (entry.ok) {
                getString(R.string.activity_sent)
            } else {
                getString(R.string.activity_failed) + if (entry.detail.isNotBlank()) ": ${entry.detail.take(90)}" else ""
            }
            row.entryMeta.text = "$whenText · $outcome"
            row.entryIcon.setImageResource(if (entry.ok) R.drawable.ic_check_circle else R.drawable.ic_cancel)
            row.entryIcon.imageTintList =
                ColorStateList.valueOf(getColor(if (entry.ok) R.color.cab_ok_text else R.color.cab_error_text))
            b.activityList.addView(row.root)
        }
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
        const val MAX_SHOWN = 15

        val PERMISSIONS = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )

        val OPTION_ROWS = listOf(
            OptionRow(Config.Option.INCOMING, R.string.opt_incoming, R.string.opt_incoming_desc),
            OptionRow(Config.Option.MISSED, R.string.opt_missed, R.string.opt_missed_desc),
            OptionRow(Config.Option.ENDED, R.string.opt_ended, R.string.opt_ended_desc),
            OptionRow(Config.Option.NAMES, R.string.opt_names, R.string.opt_names_desc),
            OptionRow(Config.Option.UNKNOWN_ONLY, R.string.opt_unknown, R.string.opt_unknown_desc),
            OptionRow(Config.Option.SILENT, R.string.opt_silent, R.string.opt_silent_desc)
        )
    }
}
