package com.callalertbot

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val neededPermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    )

    private lateinit var tokenInput: EditText
    private lateinit var chatInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Telegram call alerts"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "Sends a Telegram message when someone calls you and when you miss a call."
            setPadding(0, pad / 2, 0, pad)
        })

        tokenInput = EditText(this).apply {
            hint = "Bot token (from @BotFather)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Config.token(this@MainActivity))
        }
        root.addView(tokenInput)

        chatInput = EditText(this).apply {
            hint = "Chat ID"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(Config.chatId(this@MainActivity))
        }
        root.addView(chatInput)

        root.addView(button("1. Save") { save() })
        root.addView(button("2. Grant permissions") { requestPermissions(neededPermissions, REQUEST_PERMS) })
        root.addView(button("3. Send test message") { sendTest() })
        root.addView(button("4. Battery settings (set to Unrestricted)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        status = TextView(this).apply { setPadding(0, pad, 0, 0) }
        root.addView(status)

        setContentView(ScrollView(this).apply { addView(root) })
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun save() {
        Config.save(this, tokenInput.text.toString().trim(), chatInput.text.toString().trim())
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun sendTest() {
        save()
        val token = Config.token(this)
        val chatId = Config.chatId(this)
        if (token.isBlank() || chatId.isBlank()) {
            Toast.makeText(this, "Enter the bot token and chat ID first", Toast.LENGTH_LONG).show()
            return
        }
        thread {
            val error = Telegram.send(token, chatId, "✅ Call Alert Bot is working")
            runOnUiThread {
                val result = if (error == null) "Test message sent" else "Failed: $error"
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshStatus() {
        fun mark(ok: Boolean) = if (ok) "✓" else "✗"
        fun granted(permission: String) =
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

        val configured = Config.token(this).isNotBlank() && Config.chatId(this).isNotBlank()
        status.text = buildString {
            appendLine("${mark(configured)} Bot token and chat ID saved")
            appendLine("${mark(granted(Manifest.permission.READ_PHONE_STATE))} Phone state permission")
            appendLine("${mark(granted(Manifest.permission.READ_CALL_LOG))} Call log permission (needed to see the caller's number)")
            append("${mark(granted(Manifest.permission.READ_CONTACTS))} Contacts permission (optional, shows contact names)")
        }
    }

    private companion object {
        const val REQUEST_PERMS = 1
    }
}
