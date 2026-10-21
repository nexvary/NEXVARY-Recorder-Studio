package com.nexvary.recorder.family

import android.Manifest
import android.app.*
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
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.camera.*
import com.nexvary.recorder.delivery.*
import com.nexvary.recorder.ui.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

class FamilyDashboardActivity: AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var medical: TextView
    private lateinit var duration: EditText
    private lateinit var grace: EditText
    private var snapshot=JSONObject()
    private val handler=Handler(Looper.getMainLooper())
    private var refreshing=false
    private var lastVolume=0L
    private var photoFile: File?=null
    private var photoEvent=""
    private var locationRequested=false
    private var pendingTest=""
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        status.setText(R.string.fs_permissions_review)
    }
    private val photo=registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file=photoFile;val event=photoEvent
        if(success && file!=null) FamilyUi.io(this,{ EvidenceManager.finalized(this,file,event) }) { status.setText(R.string.fs_evidence_saved) }
        else { file?.delete();status.setText(R.string.fs_photo_cancelled) }
        photoFile=null
    }
    private val tick=object: Runnable { override fun run() { refresh();handler.postDelayed(this,1000) } }
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved)
        photoEvent=saved?.getString("photoEvent").orEmpty()
        pendingTest=saved?.getString("pendingTest").orEmpty()
        saved?.getString("photoName")?.let { name -> if(name == File(name).name) photoFile=File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),name) }
        val root=StudioPage.install(this,R.string.fs_title)
        status=DeliveryUi.text(root,R.string.fs_ready).apply { id=R.id.fsStatus;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE;textSize=19f }
        medical=DeliveryUi.text(root,R.string.fs_profile).apply { visibility=View.GONE }
        DeliveryUi.button(root,R.string.fs_safe,R.id.fsSafe) { val id=FamilyStore.state(snapshot)?.id;FamilyUi.io(this,{ SafetyCheckManager(this).resolve(id) }) { refresh() } }.apply { minHeight=(72*resources.displayMetrics.density).toInt();textSize=22f }
        DeliveryUi.button(root,R.string.fs_sos,R.id.fsSos,true) { triggerSos() }.apply { minHeight=(72*resources.displayMetrics.density).toInt();textSize=24f }
        DeliveryUi.button(root,R.string.fs_extend,R.id.fsExtend) { val id=FamilyStore.state(snapshot)?.id;FamilyUi.io(this,{ SafetyCheckManager(this).extend(id) }) { refresh() } }
        DeliveryUi.button(root,R.string.fs_cancel,R.id.fsCancel) {
            MaterialAlertDialogBuilder(this).setMessage(R.string.fs_cancel_note).setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.fs_cancel) { _,_ -> val id=FamilyStore.state(snapshot)?.id;FamilyUi.io(this,{ SafetyCheckManager(this).resolve(id,true) }) { refresh() } }.show()
        }
        val panel=DeliveryUi.section(root,R.string.fs_check_start)
        val choices=Spinner(this).apply { id=R.id.fsPresets;adapter=ArrayAdapter(this@FamilyDashboardActivity,android.R.layout.simple_spinner_dropdown_item,resources.getStringArray(R.array.fs_durations));panel.addView(this) }
        duration=FamilyUi.field(panel,R.string.fs_minutes,R.id.fsMinutes,saved?.getString("minutes") ?: "15",android.text.InputType.TYPE_CLASS_NUMBER)
        grace=FamilyUi.field(panel,R.string.fs_grace,R.id.fsGrace,saved?.getString("grace") ?: "60",android.text.InputType.TYPE_CLASS_NUMBER)
        choices.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { }
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) { listOf("15","30","60","120").getOrNull(position)?.let { duration.setText(it) } }
        }
        DeliveryUi.button(panel,R.string.fs_check_start,R.id.fsStart) { start(false) }
        DeliveryUi.button(root,R.string.fs_journey_start,R.id.fsJourney) { start(true) }
        DeliveryUi.button(root,R.string.fs_evidence,R.id.fsEvidence) { evidence(false) }
        DeliveryUi.button(root,R.string.fs_test_evidence,R.id.fsTestEvidence) { evidence(true) }
        DeliveryUi.button(root,R.string.fs_stop_evidence,R.id.fsStopEvidence,true) { FamilyUi.io(this,{ EvidenceManager.stopOwned(this,null) }) { refresh() } }
        DeliveryUi.button(root,R.string.fs_test_alert,R.id.fsTestAlert) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.fs_test_alert).setMessage(R.string.fs_test_alert_note)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.fs_test_alert) { _,_ ->
                    FamilyUi.io(this,{ SafetyCheckManager(this).emit("TEST_ALERT",manual=true,test=true) }) { refresh() }
                }.show()
        }
        listOf(R.string.fs_contacts to TrustedContactsActivity::class.java,R.string.fs_settings to FamilySettingsActivity::class.java,
            R.string.fs_profile to EmergencyProfileActivity::class.java,R.string.fs_queue to SafetyQueueActivity::class.java,
            R.string.fs_history to SafetyHistoryActivity::class.java,R.string.fs_integrity to EvidenceActivity::class.java,
            R.string.fs_privacy to PrivacyCenterActivity::class.java,R.string.fs_parent_mode to ParentSettingsActivity::class.java,
            R.string.fs_subscriptions to com.nexvary.recorder.billing.SubscriptionActivity::class.java).forEach { (label,page) ->
            DeliveryUi.button(root,label) { startActivity(Intent(this,page)) }
        }
        DeliveryUi.text(root,R.string.fs_limits_note)
        if(saved==null && intent.getBooleanExtra("trigger",false)) handler.post { triggerSos() }
    }
    override fun onStart() { super.onStart();locationRequested=false;handler.post(tick);FamilyUi.io(this,{ SafetyCheckManager(this).evaluate(true) }) }
    override fun onStop() { handler.removeCallbacks(tick);super.onStop() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("minutes",duration.text.toString());out.putString("grace",grace.text.toString())
        out.putString("photoEvent",photoEvent);out.putString("photoName",photoFile?.name);super.onSaveInstanceState(out)
        out.putString("pendingTest",pendingTest)
    }
    private fun refresh() {
        if(refreshing) return;refreshing=true
        FamilyUi.io(this,{ runCatching { FamilyStore(this).snapshot() } }) { result ->
            refreshing=false
            val root=result.getOrElse { status.setText(R.string.fs_storage_error);return@io };snapshot=root
            val state=FamilyStore.state(root);val config=FamilyStore.config(root)
            val active=state?.phase in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN,SafetyPhase.SOS_ACTIVE)
            listOf(R.id.fsSafe,R.id.fsCancel).forEach { findViewById<View>(it).apply { isEnabled=active;alpha=if(active) 1f else .45f } }
            findViewById<View>(R.id.fsExtend).isEnabled=state?.phase in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN)
            findViewById<View>(R.id.fsStart).isEnabled=!active;findViewById<View>(R.id.fsJourney).isEnabled=!active
            val remaining=state?.deadline?.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),Settings.Global.getInt(contentResolver,Settings.Global.BOOT_COUNT,-1)) ?: 0
            val first=FamilyStore.contacts(root).optJSONObject(0)?.optString("name").orEmpty()
            val battery=getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            status.text=getString(FamilyUi.state(state?.phase))+"\n"+getString(R.string.fs_dashboard_status,remaining/60_000,(remaining/1000)%60,first.ifEmpty { getString(R.string.fs_none) },battery,AlertPayload.network(this))+
                "\n"+getString(if(config.optBoolean("autoShare")) R.string.fs_sharing_on else R.string.fs_local_only)
            root.optJSONObject("journey")?.optDouble("distanceMeters",Double.NaN)?.takeIf { it.isFinite() && state?.phase==SafetyPhase.ARMED }?.let { status.append("\n"+getString(R.string.fs_distance,it.toInt())) }
            medical.visibility=if(state?.phase==SafetyPhase.SOS_ACTIVE && config.optBoolean("showMedical")) View.VISIBLE else View.GONE
            if(medical.visibility==View.VISIBLE) medical.text=root.optJSONObject("medical")?.let { m -> listOf("name","age","blood","allergies","medications","notes","guardian","phone","secondary").mapNotNull { m.optString(it).takeIf(String::isNotBlank) }.joinToString("\n") }.orEmpty()
            if(active && !locationRequested) maybeLocation()
            if(pendingTest.isNotBlank()) root.optJSONObject("evidence")?.let { items ->
                items.keys().asSequence().map { items.getJSONObject(it) }.firstOrNull { it.optString("eventId")==pendingTest }?.let { manifest ->
                    pendingTest=""
                    MaterialAlertDialogBuilder(this).setTitle(R.string.fs_test_evidence).setMessage(getString(R.string.fs_evidence_details,manifest.optString("fileName"),manifest.optLong("fileSize"),manifest.optLong("durationMs"),manifest.optString("sha256"))+"\n"+getString(R.string.fs_test_local_only))
                        .setPositiveButton(R.string.back,null).show()
                }
            }
        }
    }
    private fun start(journey: Boolean) {
        val mins=DeliveryPolicy.digits(duration.text.toString()).toLongOrNull()
        val seconds=DeliveryPolicy.digits(grace.text.toString()).toIntOrNull()
        if(mins==null || seconds==null || !SafetyPolicy.validMinutes(mins) || !SafetyPolicy.validGrace(seconds)) { status.setText(R.string.fs_timer_invalid);return }
        val missing=mutableListOf<String>()
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.POST_NOTIFICATIONS)
        val config=FamilyStore.config(snapshot)
        if(config.optBoolean("location") && !locationGranted()) missing.addAll(listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        if(missing.isNotEmpty()) { permissions.launch(missing.toTypedArray());return }
        if(!NotificationManagerCompat.from(this).areNotificationsEnabled()) { status.setText(R.string.fs_notifications_required);return }
        if(getSystemService(NotificationManager::class.java).getNotificationChannel(SafetyCheckManager.CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE) { status.setText(R.string.fs_notifications_required);return }
        val destination=if(journey) config.optJSONObject("destination") else null
        if(journey && (destination==null || !locationGranted() || !config.optBoolean("location"))) { status.setText(R.string.fs_journey_configure);return }
        MaterialAlertDialogBuilder(this).setTitle(if(journey) R.string.fs_journey_start else R.string.fs_check_start)
            .setMessage(getString(R.string.fs_arm_summary,mins,seconds,FamilyStore.contacts(snapshot).length())+"\n"+getString(if(config.optBoolean("autoShare")) R.string.fs_sharing_on else R.string.fs_local_only)+"\n"+getString(if(exact()) R.string.fs_exact_on else R.string.fs_inexact))
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.fs_begin) { _,_ ->
                FamilyUi.io(this,{ SafetyCheckManager(this).start(mins,seconds,destination) }) { maybeLocation();refresh() }
            }.show()
    }
    private fun exact()=Build.VERSION.SDK_INT<31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun locationGranted()=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun maybeLocation() {
        if(FamilyStore.config(snapshot).optBoolean("location") && locationGranted()) runCatching { locationRequested=true;ContextCompat.startForegroundService(this,Intent(this,SafetyLocationService::class.java)) }
            .onFailure { status.setText(R.string.fs_location_unavailable) }
    }
    private fun triggerSos() {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        FamilyUi.io(this,{ SafetyCheckManager(this).sos(FamilyStore.config(FamilyStore(this).snapshot()).optBoolean("immediate")) }) { maybeLocation();refresh() }
    }
    private fun evidence(test: Boolean) {
        val mode=if(test) "VIDEO" else FamilyStore.config(snapshot).optString("evidence","NONE")
        if(mode=="NONE") { status.setText(R.string.fs_evidence_configure);return }
        if(BackgroundCameraService.activeOutputPath!=null || AudioEvidenceService.activeEvent!=null) { status.setText(R.string.fs_recording_busy);return }
        val required=if(mode=="AUDIO") listOf(Manifest.permission.RECORD_AUDIO) else listOf(Manifest.permission.CAMERA)
        val missing=required.filter { ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()) { permissions.launch(missing.toTypedArray());return }
        if(mode=="PHOTO") { photoEvent=FamilyStore.state(snapshot)?.id ?: UUID.randomUUID().toString();photoFile=File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"NEXVARY_PHOTO_${System.currentTimeMillis()}.jpg")
            runCatching { photo.launch(FileProvider.getUriForFile(this,"$packageName.files",photoFile!!)) }.onFailure { status.setText(R.string.rescue_share_error) };return }
        val event=if(test) UUID.randomUUID().toString() else FamilyStore.state(snapshot)?.id ?: UUID.randomUUID().toString()
        FamilyUi.io(this,{ check(EvidenceManager.storageBytes(this)>=20_000_000) }) {
            if(test) pendingTest=event
            runCatching {
                if(mode=="AUDIO") ContextCompat.startForegroundService(this,Intent(this,AudioEvidenceService::class.java).putExtra("event",event).putExtra("duration",if(test) 10_000 else 0))
                else ContextCompat.startForegroundService(this,Intent(this,BackgroundCameraService::class.java).setAction(BackgroundCameraService.ACTION_START)
                    .putExtra(BackgroundCameraService.EXTRA_EVIDENCE_EVENT,event).putExtra(BackgroundCameraService.EXTRA_DURATION,if(test) 10_000L else 0L)
                    .putExtra(BackgroundCameraService.EXTRA_QUALITY,"emergency"))
            }.onSuccess { status.setText(R.string.fs_evidence_started) }.onFailure { status.setText(R.string.camera_failed) }
        }
    }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean {
        if(keyCode==KeyEvent.KEYCODE_VOLUME_DOWN && event.repeatCount==0 && FamilyStore.config(snapshot).optBoolean("volumeTrigger")) {
            val now=SystemClock.elapsedRealtime();if(now-lastVolume<700) { lastVolume=0;triggerSos() } else lastVolume=now;return true
        }
        return super.onKeyDown(keyCode,event)
    }
}
