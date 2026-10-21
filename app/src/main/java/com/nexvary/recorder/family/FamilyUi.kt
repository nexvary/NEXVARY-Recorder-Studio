package com.nexvary.recorder.family

import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager
import kotlinx.coroutines.*

object FamilyUi {
    fun <T> io(activity: AppCompatActivity,action: () -> T,done: (T) -> Unit = {}) {
        activity.lifecycleScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching(action) }
            result.onSuccess { if(!activity.isFinishing) done(it) }.onFailure {
                if(!activity.isFinishing) Toast.makeText(activity,R.string.fs_storage_error,Toast.LENGTH_LONG).show()
            }
        }
    }
    fun field(root: LinearLayout,label: Int,id: Int,value: String="",type: Int=InputType.TYPE_CLASS_TEXT) = DeliveryUi.field(root,label,id,value,type).apply {
        isSaveEnabled=false
        if(type==InputType.TYPE_CLASS_TEXT) textDirection=android.view.View.TEXT_DIRECTION_LOCALE
        if(type and InputType.TYPE_NUMBER_VARIATION_PASSWORD != 0 && type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER) {
            transformationMethod=android.text.method.PasswordTransformationMethod.getInstance();importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        }
    }
    fun check(root: LinearLayout,label: Int,value: Boolean=false,id: Int=android.view.View.NO_ID)=androidx.appcompat.widget.SwitchCompat(root.context).apply {
        this.id=id;setText(label);isChecked=value;minHeight=(52*resources.displayMetrics.density).toInt();root.addView(this)
    }
    fun state(phase: SafetyPhase?)=when(phase) {
        SafetyPhase.ARMED -> R.string.fs_check_active
        SafetyPhase.COUNTDOWN -> R.string.fs_are_safe
        SafetyPhase.SOS_ACTIVE -> R.string.fs_sos_active
        SafetyPhase.RESOLVED -> R.string.fs_resolved
        SafetyPhase.CANCELLED -> R.string.fs_cancelled
        else -> R.string.fs_ready
    }
    fun status(status: String)=when(status) {
        "SENT" -> R.string.mail_sent
        "QUEUED" -> R.string.mail_queued
        "SENDING" -> R.string.mail_sending
        "RETRY" -> R.string.mail_wait_retry
        "AUTH" -> R.string.mail_auth
        "TLS" -> R.string.mail_tls
        "LIMIT" -> R.string.mail_limit
        "UNCERTAIN" -> R.string.mail_uncertain
        "CONFIG" -> R.string.mail_config
        "WAITING_USER","OPENED" -> R.string.fs_sms_manual
        "CANCELLED" -> R.string.mail_cancelled
        else -> R.string.mail_failed
    }
}

abstract class ProtectedFamilyPage: GuardianProtectedActivity() {
    abstract val title: Int
    protected lateinit var root: LinearLayout
    override fun onCreate(saved: Bundle?) {
        ThemeManager.apply(this);super.onCreate(saved);root=StudioPage.install(this,title)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        build()
    }
    abstract fun build()
}
