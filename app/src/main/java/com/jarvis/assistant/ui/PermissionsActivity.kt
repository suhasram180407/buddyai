package com.jarvis.assistant.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.databinding.ActivityPermissionsBinding

/**
 * Dedicated permissions and system roles screen matching Buddy AI's black/graphite styling.
 * Displays real-time Android state and provides direct intent/role dispatch.
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var ui: ActivityPermissionsBinding

    private val requestMicLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        render()
    }

    private val requestNotifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        render()
    }

    private val requestRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.back.setOnClickListener { finish() }

        ui.btnAssistant.setOnClickListener { requestAssistantRole() }
        ui.btnMic.setOnClickListener { requestMicPermission() }
        ui.btnA11y.setOnClickListener { openAccessibilitySettings() }
        ui.btnOverlay.setOnClickListener { openOverlaySettings() }
        ui.btnBattery.setOnClickListener { requestBatteryExemption() }
        ui.btnNotif.setOnClickListener { requestNotificationPermission() }
        ui.btnAppInfo.setOnClickListener { openAppInfo() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val accentColor = ContextCompat.getColor(this, R.color.accent)
        val dimColor = ContextCompat.getColor(this, R.color.text_dim)
        val warnColor = ContextCompat.getColor(this, R.color.warn)

        // 1. Assistant Role
        val isAssistant = isDefaultAssistant()
        ui.assistantStatus.text = if (isAssistant) "Active" else "Not set"
        ui.assistantStatus.setTextColor(if (isAssistant) accentColor else dimColor)
        ui.btnAssistant.isEnabled = !isAssistant
        ui.btnAssistant.text = if (isAssistant) "Default Assistant Active" else getString(R.string.set_default)

        // 2. Microphone
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ui.micStatus.text = if (hasMic) getString(R.string.granted) else "Missing"
        ui.micStatus.setTextColor(if (hasMic) accentColor else warnColor)
        ui.btnMic.visibility = if (hasMic) View.GONE else View.VISIBLE

        // 3. Accessibility Phone Control
        val hasA11y = isAccessibilityEnabled(this)
        ui.a11yStatus.text = if (hasA11y) "Enabled" else "Disabled"
        ui.a11yStatus.setTextColor(if (hasA11y) accentColor else warnColor)
        ui.btnA11y.visibility = if (hasA11y) View.GONE else View.VISIBLE

        // 4. Display Over Other Apps
        val hasOverlay = Settings.canDrawOverlays(this)
        ui.overlayStatus.text = if (hasOverlay) "Allowed" else "Not allowed"
        ui.overlayStatus.setTextColor(if (hasOverlay) accentColor else dimColor)
        ui.btnOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        // 5. Battery Optimization
        val isIgnoringBattery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        ui.batteryStatus.text = if (isIgnoringBattery) "Unrestricted" else "Optimized"
        ui.batteryStatus.setTextColor(if (isIgnoringBattery) accentColor else dimColor)
        ui.btnBattery.visibility = if (isIgnoringBattery) View.GONE else View.VISIBLE

        // 6. Notifications
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        ui.notifStatus.text = if (hasNotif) getString(R.string.granted) else "Missing"
        ui.notifStatus.setTextColor(if (hasNotif) accentColor else dimColor)
        ui.btnNotif.visibility = if (hasNotif) View.GONE else View.VISIBLE
    }

    private fun isDefaultAssistant(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            }
        }
        val currentAssist = Settings.Secure.getString(contentResolver, "assistant") ?: return false
        val me = ComponentName(this, MainActivity::class.java).flattenToString()
        return currentAssist.contains(packageName, ignoreCase = true)
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                requestRoleLauncher.launch(intent)
                return
            }
        }
        val fallbackIntents = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        )
        for (intent in fallbackIntents) {
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        Toast.makeText(this, "Please select Buddy AI under Default Apps in Settings.", Toast.LENGTH_LONG).show()
        openAppInfo()
    }

    private fun requestMicPermission() {
        requestMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(this, getString(R.string.toast_a11y), Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun requestBatteryExemption() {
        val attempts = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
        val opened = attempts.indexOfFirst { runCatching { startActivity(it) }.isSuccess }
        if (opened > 0) {
            Toast.makeText(this, getString(R.string.toast_battery), Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    companion object {
        fun isAccessibilityEnabled(context: Context): Boolean {
            if (JarvisAccessibilityService.instance != null) return true
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, JarvisAccessibilityService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}

