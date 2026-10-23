package com.nexvary.recorder.family

import android.content.Intent
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.ui.FeatureCards
import org.json.JSONObject

class SetupDraft : ViewModel() { val fields = mutableMapOf<String, String>() }

/** Local profiles, explicitly distinct from remotely paired or online accounts. */
class GuidedSetupActivity : ProtectedFamilyPage() {
    override val title = R.string.setup_title
    private lateinit var body: LinearLayout
    private lateinit var progress: TextView
    private lateinit var draft: SetupDraft
    private var step = 0
    private var busy = false
    private var generation = 0
    private val titles = listOf(R.string.setup_health, R.string.setup_guardian, R.string.setup_child, R.string.setup_recording, R.string.setup_finish)
    override fun build() {
        draft = ViewModelProvider(this)[SetupDraft::class.java]
        DeliveryUi.text(root, R.string.setup_intro)
        progress = DeliveryUi.text(root, R.string.setup_title).apply { id = R.id.setupProgress }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; root.addView(this) }
    }
    override fun onResume() { super.onResume(); if (::body.isInitialized) refresh() }
    private fun refresh() {
        val token = ++generation
        FamilyUi.io(this, { FamilyStore(this).snapshot() }) { data ->
            if (token != generation) return@io
            step = GuidedSetupProgress.data(data).optInt("step").coerceIn(0, 4)
            render(data)
        }
    }
    private fun page(type: Class<out android.app.Activity>) { startActivity(Intent(this, type)) }
    private fun render(data: JSONObject) {
        body.removeAllViews()
        progress.text = getString(R.string.setup_progress, step + 1, titles.size, getString(titles[step]))
        DeliveryUi.text(body, titles[step]).apply { textSize = 21f }
        when (step) {
            0 -> {
                DeliveryUi.text(body, R.string.setup_health_note)
                FeatureCards.add(body, R.drawable.asset_info, R.string.fs_profile) {
                    startActivity(Intent(this, EmergencyProfileActivity::class.java).putExtra(GuidedSetupProgress.EXTRA_GUIDED, true))
                }
                DeliveryUi.button(body, R.string.setup_next, R.id.setupNext) { save { GuidedSetupProgress.move(it, 1) } }
                    .isEnabled = GuidedSetupProgress.data(data).optBoolean("healthReviewed")
                DeliveryUi.button(body, R.string.setup_skip_health, R.id.setupSkip) { save { GuidedSetupProgress.move(it, 1) } }
            }
            1 -> guardian(data)
            2 -> child(data)
            3 -> {
                DeliveryUi.text(body, R.string.setup_recording_note)
                FeatureCards.grid(body, listOf(
                    FeatureCards.Item(R.id.setupCamera, R.drawable.asset_camera, R.string.camera_title, R.string.more_camera_note) { page(com.nexvary.recorder.camera.BackgroundCameraActivity::class.java) },
                    FeatureCards.Item(R.id.setupScreen, R.drawable.asset_screen, R.string.screen_record_title, R.string.home_screen_hint) { page(com.nexvary.recorder.screen.ScreenRecorderActivity::class.java) },
                    FeatureCards.Item(R.id.setupAudio, R.drawable.asset_audio, R.string.guardian_record_audio, R.string.home_tools_hint) { page(com.nexvary.recorder.audio.VoiceRecorderActivity::class.java) },
                    FeatureCards.Item(R.id.setupLibrary, R.drawable.asset_library, R.string.library_title, R.string.setup_player_note) { page(com.nexvary.recorder.media.RecordingsLibraryActivity::class.java) }
                )); next(4)
            }
            4 -> {
                DeliveryUi.text(body, R.string.setup_local_ready)
                val medical = data.optJSONObject("medical") ?: JSONObject()
                val guardian = data.optJSONObject("guardianProfile") ?: JSONObject()
                DeliveryUi.text(body, R.string.setup_summary).text = getString(R.string.setup_summary_value,
                    medical.optString("name").ifBlank { getString(R.string.setup_not_set) },
                    guardian.optString("name").ifBlank { getString(R.string.setup_not_set) },
                    if (data.optJSONObject("childProfile")?.optBoolean("enabled") == true) getString(R.string.guardian_child_mode) else getString(R.string.setup_personal))
                FeatureCards.add(body, R.drawable.asset_family, R.string.fs_contacts) { page(TrustedContactsActivity::class.java) }
                FeatureCards.add(body, R.drawable.asset_settings, R.string.settings_pin) { page(ParentSettingsActivity::class.java) }
                FeatureCards.add(body, R.drawable.asset_family, R.string.fs_check_start) { page(FamilyDashboardActivity::class.java) }
                if (data.optJSONObject("childProfile")?.optBoolean("enabled") == true) {
                    DeliveryUi.button(body, R.string.guardian_child_mode, R.id.setupChildMode) { com.nexvary.recorder.guardian.ChildMode.activate(this) }
                }
                DeliveryUi.text(body, R.string.setup_cloud_note)
                DeliveryUi.button(body, R.string.settings_account, R.id.setupCloud) { page(com.nexvary.recorder.guardian.CloudSettingsActivity::class.java) }
                DeliveryUi.button(body, R.string.setup_done, R.id.setupDone) { save(finish = true) { GuidedSetupProgress.complete(it) } }
            }
        }
        if (step > 0) DeliveryUi.button(body, R.string.setup_previous, R.id.setupPrevious) { save { GuidedSetupProgress.move(it, step - 1) } }
        DeliveryUi.button(body, R.string.setup_record_now, R.id.setupRecordNow) { page(com.nexvary.recorder.camera.BackgroundCameraActivity::class.java) }
    }
    private fun field(label: Int, id: Int, key: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT) =
        FamilyUi.field(body, label, id, draft.fields[key] ?: value, type).apply { doAfterTextChanged { draft.fields[key] = it.toString() } }
    private fun guardian(data: JSONObject) {
        DeliveryUi.text(body, R.string.setup_profiles_note)
        val previous = data.optJSONObject("guardianProfile") ?: JSONObject()
        val medical = data.optJSONObject("medical") ?: JSONObject()
        val name = field(R.string.fs_full_name, R.id.setupGuardianName, "guardianName", previous.optString("name", medical.optString("guardian")))
        val phone = field(R.string.fs_emergency_phone, R.id.setupGuardianPhone, "guardianPhone", previous.optString("phone", medical.optString("phone")), InputType.TYPE_CLASS_PHONE)
        val email = field(R.string.guardian_email, R.id.setupGuardianEmail, "guardianEmail", previous.optString("email"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        DeliveryUi.text(body, R.string.setup_guardian_note)
        DeliveryUi.button(body, R.string.setup_save_next, R.id.setupNext) {
            val n = name.text.toString().trim(); val p = phone.text.toString().trim(); val e = email.text.toString().trim()
            if (n.isBlank()) { name.error = getString(R.string.setup_name_required); return@button }
            if (e.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) { email.error = getString(R.string.smtp_invalid); return@button }
            save(clearDraft = true) {
                it.put("guardianProfile", JSONObject().put("name", n.take(120)).put("phone", p.take(80)).put("email", e.take(254)))
                val profile = it.optJSONObject("medical") ?: JSONObject()
                it.put("medical", profile.put("guardian", n.take(120)).put("phone", p.take(80)))
                GuidedSetupProgress.move(it, 2)
            }
        }
        DeliveryUi.button(body, R.string.setup_personal, R.id.setupSkip) { personalUse() }
    }
    private fun child(data: JSONObject) {
        DeliveryUi.text(body, R.string.setup_child_note)
        val previous = data.optJSONObject("childProfile") ?: JSONObject()
        val medical = data.optJSONObject("medical") ?: JSONObject()
        val name = field(R.string.fs_full_name, R.id.setupChildName, "childName", medical.optString("name", previous.optString("name")))
        val age = field(R.string.fs_age, R.id.setupChildAge, "childAge", medical.optString("age", previous.optString("age")), InputType.TYPE_CLASS_NUMBER)
        DeliveryUi.button(body, R.string.setup_save_next, R.id.setupNext) {
            val n = name.text.toString().trim(); val a = com.nexvary.recorder.delivery.DeliveryPolicy.digits(age.text.toString())
            if (n.isBlank()) { name.error = getString(R.string.setup_name_required); return@button }
            if (a.isNotBlank() && (a.toIntOrNull() ?: -1) !in 0..120) { age.error = getString(R.string.setup_age_invalid); return@button }
            save(clearDraft = true) {
                it.put("childProfile", JSONObject().put("enabled", true).put("name", n.take(120)).put("age", a))
                val profile = it.optJSONObject("medical") ?: JSONObject()
                it.put("medical", profile.put("name", n.take(120)).put("age", a))
                GuidedSetupProgress.move(it, 3)
            }
        }
        DeliveryUi.button(body, R.string.setup_personal, R.id.setupSkip) { personalUse() }
    }
    private fun personalUse() = save(clearDraft = true) {
        val profile = it.optJSONObject("childProfile") ?: JSONObject()
        it.put("childProfile", profile.put("enabled", false)); GuidedSetupProgress.move(it, 3)
    }
    private fun next(target: Int) { DeliveryUi.button(body, R.string.setup_next, R.id.setupNext) { save { GuidedSetupProgress.move(it, target) } } }
    private fun save(finish: Boolean = false, clearDraft: Boolean = false, action: (JSONObject) -> Unit) {
        if (busy) return
        busy = true
        FamilyUi.io(this, { runCatching { FamilyStore(this).update { action(it) } } }) { result ->
            busy = false
            if (result.isSuccess) { if (clearDraft) draft.fields.clear(); if (finish) this.finish() else refresh() }
            else Toast.makeText(this, R.string.fs_storage_error, Toast.LENGTH_LONG).show()
        }
    }
}
