package com.nexvary.recorder.family

import android.Manifest
import android.app.AlarmManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.*
import com.nexvary.recorder.ui.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class SafetyQueueActivity: AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root=StudioPage.install(this,R.string.fs_queue);DeliveryUi.text(root,R.string.fs_queue_note)
        DeliveryUi.button(root,R.string.refresh) { render() }
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;root.addView(this) };render()
    }
    private fun render() {
        FamilyUi.io(this,{ SafetyAlertWorker.reconcile(this);EmergencyQueue(this).all() }) { jobs ->
            list.removeAllViews()
            if(jobs.isEmpty()) DeliveryUi.text(list,R.string.mail_empty)
            jobs.take(100).forEach { job ->
                val card=DeliveryUi.section(list,R.string.fs_alert)
                card.addView(TextView(this).apply { text=job.optString("recipient");textDirection=View.TEXT_DIRECTION_LTR;textSize=17f })
                DeliveryUi.text(card,FamilyUi.status(job.optString("status")))
                if(job.optString("channel")=="SMS" && job.optString("status") in listOf("WAITING_USER","OPENED")) DeliveryUi.button(card,R.string.fs_compose_sms) {
                    MaterialAlertDialogBuilder(this).setTitle(R.string.rescue_review).setMessage(job.getJSONObject("payload").toString(2))
                        .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.rescue_continue) { _,_ ->
                            runCatching { startActivity(Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",job.getString("recipient"),null)).putExtra("sms_body",job.getJSONObject("payload").toString(2))) }
                                .onSuccess { FamilyUi.io(this,{ FamilyStore(this).update { FamilyStore.jobs(it).getJSONObject(job.getString("id")).put("status","OPENED") } }) { render() } }
                                .onFailure { Toast.makeText(this,R.string.rescue_share_error,Toast.LENGTH_LONG).show() }
                        }.show()
                }
                if(job.optString("channel")=="EMAIL" && job.optString("status") in listOf("FAILED","AUTH","TLS","CONFIG","LIMIT","UNCERTAIN")) DeliveryUi.button(card,R.string.mail_retry) {
                    MaterialAlertDialogBuilder(this).setMessage(if(job.optString("status")=="UNCERTAIN") R.string.mail_retry_uncertain else R.string.mail_retry_confirm)
                        .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.mail_retry) { _,_ ->
                            FamilyUi.io(this,{ EmergencyQueue(this).retry(job.getString("id"));SafetyAlertWorker.reconcile(this) }) { render() }
                        }.show()
                }
                if(job.optString("status") !in listOf("SENT","SENDING","CANCELLED")) DeliveryUi.button(card,R.string.mail_cancel,red=true) {
                    FamilyUi.io(this,{ EmergencyQueue(this).cancel(job.getString("id")) }) { render() }
                }
            }
        }
    }
}

class SafetyHistoryActivity: AppCompatActivity() {
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root=StudioPage.install(this,R.string.fs_history)
        FamilyUi.io(this,{ FamilyStore.events(FamilyStore(this).snapshot()) }) { events ->
            DeliveryUi.text(root,R.string.fs_history_note)
            for(i in events.length()-1 downTo maxOf(0,events.length()-100)) {
                val event=events.getJSONObject(i)
                val label=when(event.optString("type")) {
                    "CHECK_STARTED" -> R.string.fs_event_check
                    "CHECK_CONFIRMED" -> R.string.fs_safe
                    "CHECK_PROMPTED" -> R.string.fs_are_safe
                    "SOS_STARTED","SOS_COUNTDOWN" -> R.string.fs_sos
                    "SOS_CANCELLED" -> R.string.fs_cancelled
                    "JOURNEY_STARTED" -> R.string.fs_journey_start
                    "JOURNEY_COMPLETED" -> R.string.fs_event_arrival
                    "POSSIBLE_IMPACT" -> R.string.fs_possible_impact
                    "EVIDENCE_SAVED" -> R.string.fs_evidence_saved
                    "ALERT_SENT" -> R.string.mail_sent
                    "NETWORK_LOST" -> R.string.fs_network_lost
                    "BATTERY_SAVER_ACTIVE" -> R.string.fs_battery_saver
                    else -> R.string.fs_event_other
                }
                DeliveryUi.text(root,label).append("\n"+DateFormat.getDateTimeInstance().format(Date(event.optLong("time"))))
            }
        }
    }
}

class EvidenceActivity: ProtectedFamilyPage() {
    override val title=R.string.fs_integrity
    private var exporting=""
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null && exporting.isNotBlank()) {
            val content=exporting;FamilyUi.io(this,{ checkNotNull(contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(content) } }) { Toast.makeText(this,R.string.fs_exported,Toast.LENGTH_LONG).show() }
        }
        exporting=""
    }
    override fun build() {
        DeliveryUi.text(root,R.string.fs_integrity_note)
        FamilyUi.io(this,{ FamilyStore(this).snapshot().optJSONObject("evidence") ?: JSONObject() }) { evidence ->
            if(evidence.length()==0) DeliveryUi.text(root,R.string.fs_evidence_empty)
            evidence.keys().asSequence().map { evidence.getJSONObject(it) }.sortedByDescending { it.optString("closedAt") }.take(100).forEach { manifest ->
                val card=DeliveryUi.section(root,R.string.fs_evidence_saved)
                card.addView(TextView(this).apply {
                    text=getString(R.string.fs_evidence_details,manifest.optString("fileName"),manifest.optLong("fileSize"),manifest.optLong("durationMs"),manifest.optString("sha256"))
                    setTextIsSelectable(true);textSize=15f;textDirection=View.TEXT_DIRECTION_LTR
                })
                DeliveryUi.button(card,R.string.fs_verify_hash) {
                    FamilyUi.io(this,{ EvidenceManager.verify(this,manifest) }) { valid ->
                        MaterialAlertDialogBuilder(this).setMessage(if(valid) R.string.fs_hash_valid else R.string.fs_hash_invalid).setPositiveButton(R.string.back,null).show()
                    }
                }
                DeliveryUi.button(card,R.string.fs_export_manifest) {
                    MaterialAlertDialogBuilder(this).setMessage(R.string.fs_export_note).setNegativeButton(R.string.cancel,null)
                        .setPositiveButton(R.string.rescue_continue) { _,_ -> exporting=manifest.toString(2);export.launch("evidence.json") }.show()
                }
            }
        }
    }
}

class PrivacyCenterActivity: AppCompatActivity() {
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved)
        val root=StudioPage.install(this,R.string.fs_privacy)
        DeliveryUi.text(root,R.string.fs_privacy_note)
        listOf(Triple(Manifest.permission.CAMERA,R.string.camera_title,R.string.fs_camera_why),
            Triple(Manifest.permission.RECORD_AUDIO,R.string.camera_mic,R.string.fs_mic_why),
            Triple(Manifest.permission.ACCESS_COARSE_LOCATION,R.string.rescue_location,R.string.fs_location_why)).forEach { (permission,title,note) ->
            val card=DeliveryUi.section(root,title);DeliveryUi.text(card,if(ContextCompat.checkSelfPermission(this,permission)==PackageManager.PERMISSION_GRANTED) R.string.fs_permission_granted else R.string.fs_permission_denied);DeliveryUi.text(card,note)
        }
        val notifications=DeliveryUi.section(root,R.string.fs_notifications)
        DeliveryUi.text(notifications,if(NotificationManagerCompat.from(this).areNotificationsEnabled()) R.string.fs_permission_granted else R.string.fs_permission_denied)
        DeliveryUi.text(notifications,R.string.fs_notifications_why)
        DeliveryUi.button(notifications,R.string.fs_open_notifications) { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName)) }
        DeliveryUi.button(root,R.string.fs_open_permissions) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) }
        val exact=Build.VERSION.SDK_INT<31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        DeliveryUi.text(root,if(exact) R.string.fs_exact_on else R.string.fs_inexact)
        if(Build.VERSION.SDK_INT>=31) DeliveryUi.button(root,R.string.fs_open_alarms) { runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName"))) } }
        DeliveryUi.text(root,R.string.fs_background_note)
        DeliveryUi.text(root,if(getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) R.string.fs_battery_unrestricted else R.string.fs_battery_restricted)
        DeliveryUi.button(root,R.string.fs_open_battery) { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
        DeliveryUi.text(root,R.string.fs_contacts_note);DeliveryUi.text(root,R.string.fs_upload_unconfigured)
        DeliveryUi.text(root,R.string.fs_storage_estimate).also { label ->
            FamilyUi.io(this,{ EvidenceManager.storageBytes(this) }) { available -> label.text=getString(R.string.fs_storage_available,available/1_000_000) }
        }
    }
}
