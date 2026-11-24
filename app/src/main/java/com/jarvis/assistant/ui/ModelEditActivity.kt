package com.jarvis.assistant.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.ApiKind
import com.jarvis.assistant.ModelProfile
import com.jarvis.assistant.PROVIDERS
import com.jarvis.assistant.ProviderPreset
import com.jarvis.assistant.R
import com.jarvis.assistant.Settings
import com.jarvis.assistant.agent.Llm
import com.jarvis.assistant.databinding.ActivityModelEditBinding
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Add a new model, or edit/delete an existing one. */
class ModelEditActivity : AppCompatActivity() {

    private lateinit var ui: ActivityModelEditBinding
    private lateinit var settings: Settings
    private var existing: ModelProfile? = null
    private var preset: ProviderPreset = PROVIDERS.first()
    private val efforts = listOf("low", "medium", "high", "xhigh", "max")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityModelEditBinding.inflate(layoutInflater)
        setContentView(ui.root)
        ui.root.padForSystemBars()
        settings = Settings(this)
        existing = intent.getStringExtra(EXTRA_ID)?.let { id -> settings.profiles().firstOrNull { it.id == id } }

        ui.pageTitle.text = getString(if (existing == null) R.string.add_model else R.string.edit_model)
        ui.delete.visibility = if (existing == null) View.GONE else View.VISIBLE
        ui.back.setOnClickListener { finish() }
        ui.delete.setOnClickListener { confirmDelete() }
        ui.testButton.setOnClickListener { test() }
        ui.saveButton.setOnClickListener { save() }
        ui.getKeyButton.setOnClickListener { openApiKeyPage() }
        ui.selectModelButton.setOnClickListener { browseAndSelectModel() }

        PROVIDERS.forEach { ui.providerChips.addChoice(it.label.substringBefore(" ("), it.id) }
        efforts.forEach { ui.effortChips.addChoice(EFFORTS[it]?.let(::getString) ?: it.replaceFirstChar(Char::uppercase), it) }

        val start = existing
        if (start != null) {
            preset = start.preset
            ui.name.setText(start.name)
            ui.apiKey.setText(start.apiKey)
            ui.model.setText(start.model)
            ui.baseUrl.setText(start.baseUrl)
            ui.effortChips.select(start.effort)
            ui.primarySwitch.isChecked = settings.primaryId == start.id
        } else {
            applyPresetDefaults(preset, previous = null)
            ui.effortChips.select("medium")
            ui.primarySwitch.isChecked = settings.profiles().isEmpty()
        }
        ui.providerChips.select(preset.id)
        showFieldsFor(preset)

        ui.providerChips.setOnCheckedStateChangeListener { _, _ ->
            val next = PROVIDERS.firstOrNull { it.id == ui.providerChips.selectedTag } ?: return@setOnCheckedStateChangeListener
            if (next == preset) return@setOnCheckedStateChangeListener
            applyPresetDefaults(next, previous = preset)
            preset = next
            showFieldsFor(next)
            ui.testResult.visibility = View.GONE
        }
    }

    /** Fill in the new provider's defaults, but keep anything the user typed themselves. */
    private fun applyPresetDefaults(next: ProviderPreset, previous: ProviderPreset?) {
        fun untouched(current: String, oldDefault: String?) = current.isBlank() || current == oldDefault
        if (untouched(ui.name.text.toString(), previous?.label?.substringBefore(" ("))) ui.name.setText(next.label.substringBefore(" ("))
        if (untouched(ui.model.text.toString(), previous?.defaultModel)) ui.model.setText(next.defaultModel)
        if (untouched(ui.baseUrl.text.toString(), previous?.defaultBaseUrl)) ui.baseUrl.setText(next.defaultBaseUrl)
    }

    private fun showFieldsFor(p: ProviderPreset) {
        val local = p.id in setOf("lmstudio", "ollama", "custom")
        // Cloud providers have a fixed address, so only local/custom servers show it.
        ui.baseUrlLayout.visibility = if (local) View.VISIBLE else View.GONE
        ui.apiKeyLayout.visibility = if (p.needsKey || p.id == "custom") View.VISIBLE else View.GONE
        ui.apiKeyLayout.hint = getString(if (p.needsKey) R.string.api_key else R.string.api_key_optional)
        ui.effortSection.visibility = if (p.kind == ApiKind.ANTHROPIC) View.VISIBLE else View.GONE
        ui.providerHint.text = HINTS[p.id]?.let(::getString).orEmpty()
        ui.getKeyButton.visibility = if (p.apiKeyUrl.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun openApiKeyPage() {
        val url = preset.apiKeyUrl
        if (url.isNotBlank()) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }.onFailure {
                Toast.makeText(this, url, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun browseAndSelectModel() {
        val key = ui.apiKey.text.toString().trim()
        val base = ui.baseUrl.text.toString().trim().ifBlank { preset.defaultBaseUrl }
        val curated = CURATED_MODELS[preset.id] ?: emptyList()

        if (preset.kind == ApiKind.ANTHROPIC) {
            showModelChooserDialog(curated)
            return
        }

        if (preset.needsKey && key.isBlank()) {
            Toast.makeText(this, R.string.need_key_for_models, Toast.LENGTH_SHORT).show()
            showModelChooserDialog(curated)
            return
        }

        ui.selectModelButton.isEnabled = false
        ui.selectModelButton.text = getString(R.string.fetching_models)

        Thread {
            val live = fetchLiveModels(base, key)
            runOnUiThread {
                ui.selectModelButton.isEnabled = true
                ui.selectModelButton.setText(R.string.browse_models)
                val all = (live + curated).distinct()
                showModelChooserDialog(all)
            }
        }.start()
    }

    private fun fetchLiveModels(baseUrl: String, apiKey: String): List<String> {
        return runCatching {
            val url = baseUrl.trimEnd('/') + "/models"
            val reqBuilder = Request.Builder().url(url).get()
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return emptyList()
                val result = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val id = data.optJSONObject(i)?.optString("id") ?: continue
                    if (id.isNotBlank()) {
                        val lower = id.lowercase()
                        if (!lower.contains("whisper") && !lower.contains("embed") &&
                            !lower.contains("moderation") && !lower.contains("guard") &&
                            !lower.contains("tts") && !lower.contains("dall-e")) {
                            result.add(id)
                        }
                    }
                }
                result.sorted()
            }
        }.getOrDefault(emptyList())
    }

    private fun showModelChooserDialog(models: List<String>) {
        if (models.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_model_title)
                .setMessage(if (preset.needsKey && ui.apiKey.text.toString().isBlank()) R.string.need_key_for_models else R.string.load_failed)
                .setPositiveButton(R.string.save, null)
                .show()
            return
        }
        val currentModel = ui.model.text.toString().trim()
        val items = models.toTypedArray()
        val checkedItem = items.indexOf(currentModel).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_model_title)
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                ui.model.setText(items[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun current(): ModelProfile? {
        ui.apiKeyLayout.error = null
        ui.name.error = null
        val key = ui.apiKey.text.toString().trim()
        val model = ui.model.text.toString().trim()
        if (preset.needsKey && key.isBlank()) { ui.apiKeyLayout.error = getString(R.string.paste_api_key); return null }
        if (model.isBlank()) { ui.model.error = getString(R.string.enter_model_id); return null }
        return ModelProfile(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name = ui.name.text.toString().trim().ifBlank { preset.label.substringBefore(" (") },
            providerId = preset.id,
            baseUrl = ui.baseUrl.text.toString().trim().ifBlank { preset.defaultBaseUrl },
            apiKey = key,
            model = model,
            effort = ui.effortChips.selectedTag ?: "medium",
        )
    }

    private fun test() {
        val profile = current() ?: return
        ui.testButton.isEnabled = false
        ui.testButton.setText(R.string.testing)
        ui.testResult.visibility = View.GONE
        Thread {
            val result = runCatching { Llm.test(profile.toConfig()) }
            runOnUiThread {
                ui.testButton.isEnabled = true
                ui.testButton.setText(R.string.test_connection)
                ui.testResult.visibility = View.VISIBLE
                result.onSuccess {
                    ui.testResult.setTextColor(color(R.color.ok))
                    ui.testResult.text = getString(R.string.test_ok, profile.name, it.take(60))
                }.onFailure {
                    ui.testResult.setTextColor(color(R.color.danger))
                    ui.testResult.text = "✗ ${it.message?.take(300) ?: it.javaClass.simpleName}"
                }
            }
        }.start()
    }

    private fun save() {
        val profile = current() ?: return
        settings.saveProfile(profile)
        if (ui.primarySwitch.isChecked) settings.primaryId = profile.id
        finish()
    }

    private fun confirmDelete() {
        val profile = existing ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_model_title, profile.name))
            .setMessage(R.string.delete_model_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                settings.deleteProfile(profile.id)
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_ID = "id"
        private val EFFORTS = mapOf(
            "low" to R.string.effort_low, "medium" to R.string.effort_medium, "high" to R.string.effort_high,
            "xhigh" to R.string.effort_xhigh, "max" to R.string.effort_max,
        )
        private val HINTS = mapOf(
            "groq" to R.string.help_groq,
            "gemini" to R.string.help_gemini,
            "openrouter" to R.string.help_openrouter,
            "lmstudio" to R.string.help_lmstudio,
            "ollama" to R.string.help_ollama,
            "custom" to R.string.help_custom,
        )
        private val CURATED_MODELS = mapOf(
            "groq" to listOf(
                "qwen/qwen3.8-27b",
                "openai/gpt-oss-120b",
                "openai/gpt-oss-20b",
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant",
                "llama3-70b-8192",
                "mixtral-8x7b-32768",
                "gemma2-9b-it",
            ),
            "gemini" to listOf(
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-pro",
                "gemini-1.5-flash",
            ),
            "openrouter" to listOf(
                "meta-llama/llama-3.3-70b-instruct:free",
                "google/gemini-2.0-flash-exp:free",
                "deepseek/deepseek-r1:free",
                "qwen/qwen-2.5-72b-instruct:free",
                "mistralai/mistral-7b-instruct:free",
                "anthropic/claude-3.5-sonnet",
                "openai/gpt-4o",
            ),
            "ollama" to listOf(
                "qwen2.5:14b",
                "qwen2.5:7b",
                "llama3.2:3b",
                "llama3.1:8b",
                "mistral:7b",
            ),
            "lmstudio" to listOf(
                "local-model",
            ),
        )
    }
}
