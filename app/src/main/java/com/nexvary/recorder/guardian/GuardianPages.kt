package com.nexvary.recorder.guardian

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.family.*
import com.nexvary.recorder.ui.*

object ChildMode {
    fun enabled(context: Context)=context.getSharedPreferences("guardian_mode",Context.MODE_PRIVATE).getBoolean("child",false)
    fun activate(activity: AppCompatActivity) {
        FamilyUi.io(activity,{ GuardianGate.enabled(activity) }) { configured ->
            if(!configured) { Toast.makeText(activity,R.string.guardian_pin_required,Toast.LENGTH_LONG).show();activity.startActivity(Intent(activity,ParentSettingsActivity::class.java));return@io }
            GuardianGate.action(activity) {
                activity.getSharedPreferences("guardian_mode",Context.MODE_PRIVATE).edit().putBoolean("child",true).apply()
                GuardianGate.lock();activity.startActivity(Intent(activity,ChildHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        }
    }
}

class ChildHomeActivity: AppCompatActivity() {
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved)
        val root=StudioPage.install(this,R.string.guardian_child_mode)
        root.findViewById<android.view.View>(R.id.btnBack).visibility=android.view.View.GONE
        DeliveryUi.button(root,R.string.fs_sos,red=true) { startActivity(Intent(this,GuardianEmergencyActivity::class.java).putExtra("trigger",true)) }
        DeliveryUi.button(root,R.string.fs_safe) { FamilyUi.io(this,{ SafetyCheckManager(this).resolve(FamilyStore.state(FamilyStore(this).snapshot())?.id) }) { Toast.makeText(this,R.string.fs_safe,Toast.LENGTH_SHORT).show() } }
        DeliveryUi.button(root,R.string.fs_journey_start) {
            FamilyUi.io(this,{
                val dest=FamilyStore.config(FamilyStore(this).snapshot()).optJSONObject("destination") ?: error("CONFIGURE_JOURNEY")
                SafetyCheckManager(this).start(30,60,dest)
            }) { Toast.makeText(this,R.string.fs_check_active,Toast.LENGTH_LONG).show() }
        }
        DeliveryUi.button(root,R.string.fs_check_start) {
            FamilyUi.io(this,{ SafetyCheckManager(this).start(15,60) }) { Toast.makeText(this,R.string.fs_check_active,Toast.LENGTH_LONG).show() }
        }
        DeliveryUi.button(root,R.string.fs_parent_mode) {
            GuardianGate.lock()
            GuardianGate.prompt(this,cancel={}) {
                getSharedPreferences("guardian_mode",MODE_PRIVATE).edit().putBoolean("child",false).apply()
                startActivity(Intent(this,com.nexvary.recorder.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        }
    }
}

/** Emergency controls are never behind a PIN, subscription or cloud quota. */
class GuardianEmergencyActivity: AppCompatActivity() {
    private lateinit var status: TextView
    private val cameraPermissions=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result[android.Manifest.permission.CAMERA]==true) beginEvidence()
        else status.setText(R.string.camera_permission_denied)
    }
    private fun recordEvidence() {
        if(androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermissions.launch(arrayOf(android.Manifest.permission.CAMERA,android.Manifest.permission.RECORD_AUDIO));return
        }
        beginEvidence()
    }
    private fun beginEvidence() {
        FamilyUi.io(this,{ SafetyCheckManager(this).sos(true).id }) { event ->
            if(com.nexvary.recorder.camera.BackgroundCameraService.activeOutputPath!=null) { status.setText(R.string.fs_recording_busy);return@io }
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(this,Intent(this,com.nexvary.recorder.camera.BackgroundCameraService::class.java)
                    .setAction(com.nexvary.recorder.camera.BackgroundCameraService.ACTION_START)
                    .putExtra(com.nexvary.recorder.camera.BackgroundCameraService.EXTRA_EVIDENCE_EVENT,event)
                    .putExtra(com.nexvary.recorder.camera.BackgroundCameraService.EXTRA_QUALITY,"emergency")
                    .putExtra(com.nexvary.recorder.camera.BackgroundCameraService.EXTRA_AUDIO,androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED))
            }.onSuccess { status.setText(R.string.fs_evidence_started) }.onFailure { status.setText(R.string.camera_failed) }
        }
    }
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved)
        val root=StudioPage.install(this,R.string.rescue_title)
        status=DeliveryUi.text(root,R.string.guardian_local_always)
        DeliveryUi.button(root,R.string.fs_sos,red=true) { trigger(false) }
        DeliveryUi.button(root,R.string.guardian_silent_sos) { trigger(true) }
        DeliveryUi.button(root,R.string.fs_evidence) { recordEvidence() }
        DeliveryUi.button(root,R.string.fs_stop_evidence) { FamilyUi.io(this,{ EvidenceManager.stopOwned(this,null) }) { status.setText(R.string.camera_saving) } }
        DeliveryUi.button(root,R.string.guardian_call) { runCatching { startActivity(Intent(Intent.ACTION_DIAL)) } }
        DeliveryUi.button(root,R.string.fs_compose_sms) { manualShare(true) }
        DeliveryUi.button(root,R.string.guardian_share_location) { manualShare(false) }
        DeliveryUi.button(root,R.string.fs_contacts) { startActivity(Intent(this,com.nexvary.recorder.safety.QuickRescueActivity::class.java)) }
        if(saved==null && intent.getBooleanExtra("trigger",false)) trigger(false)
    }
    private fun trigger(silent: Boolean) {
        FamilyUi.io(this,{
            SafetyCheckManager(this).sos(true,if(silent) "SILENT_SOS" else "SOS")
        }) { state ->
            status.text=getString(R.string.fs_sos_active)+"\n"+state.id+"\n"+getString(R.string.guardian_local_always)
            FamilyUi.io(this,{ FamilyStore.config(FamilyStore(this).snapshot()).optBoolean("location") }) { consent ->
                if(consent && (androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_COARSE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED || androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED))
                    runCatching { androidx.core.content.ContextCompat.startForegroundService(this,Intent(this,SafetyLocationService::class.java)) }
            }
        }
    }
    private fun manualShare(sms: Boolean) {
        FamilyUi.io(this,{ FamilyStore(this).snapshot() }) { data ->
            val place=data.optJSONObject("lastLocation")
            val text=getString(R.string.fs_sos)+(place?.let { "\nhttps://maps.google.com/?q=${it.optDouble("latitude")},${it.optDouble("longitude")}\n"+getString(R.string.guardian_last_location) } ?: "")
            val action=if(sms) Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")).putExtra("sms_body",text)
                else Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),getString(R.string.guardian_share_location))
            runCatching { startActivity(action) }.onFailure { Toast.makeText(this,R.string.rescue_share_error,Toast.LENGTH_LONG).show() }
        }
    }
}
