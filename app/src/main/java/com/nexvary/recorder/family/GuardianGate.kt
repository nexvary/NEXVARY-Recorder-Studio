package com.nexvary.recorder.family

import android.app.*
import android.os.*
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryPolicy
import kotlinx.coroutines.*
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object GuardianGate {
    @Volatile private var expires=0L
    fun lock() { expires=0 }
    fun open() { expires=SystemClock.elapsedRealtime()+5*60_000 }
    fun enabled(context: android.content.Context)=FamilyStore(context).snapshot().has("guardianPin")
    fun unlocked()=SystemClock.elapsedRealtime()<expires
    private fun derive(pin: String,salt: ByteArray): ByteArray {
        val chars=pin.toCharArray();val spec=PBEKeySpec(chars,salt,250_000,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword();chars.fill('\u0000') }
    }
    fun create(context: android.content.Context,pin: String) {
        require(pin.length in 6..12 && pin.all { it in '0'..'9' })
        val salt=ByteArray(16).also(SecureRandom()::nextBytes)
        val hash=derive(pin,salt)
        FamilyStore(context).update { root -> root.put("guardianPin",JSONObject().put("salt",Base64.getEncoder().encodeToString(salt)).put("hash",Base64.getEncoder().encodeToString(hash))) }
        hash.fill(0);open()
    }
    fun verify(context: android.content.Context,pin: String): Boolean = FamilyStore(context).update { root ->
        val saved=root.optJSONObject("guardianPin") ?: return@update true
        if(System.currentTimeMillis() < saved.optLong("lockedUntil")) return@update false
        val actual=derive(pin,Base64.getDecoder().decode(saved.getString("salt")))
        val valid=MessageDigest.isEqual(actual,Base64.getDecoder().decode(saved.getString("hash")));actual.fill(0)
        if(valid) { saved.put("failures",0).put("lockedUntil",0);open() }
        else { val failures=saved.optInt("failures")+1;saved.put("failures",failures);if(failures>=5) saved.put("lockedUntil",System.currentTimeMillis()+60_000).put("failures",0) }
        valid
    }
    fun action(activity: AppCompatActivity,allowed: () -> Unit) {
        FamilyUi.io(activity,{ enabled(activity) }) { required ->
            if(required && !unlocked()) prompt(activity,cancel={}) { allowed() } else allowed()
        }
    }
    fun prompt(activity: AppCompatActivity,cancel: () -> Unit = { activity.finish() },allowed: () -> Unit) {
        val field=EditText(activity).apply { id=R.id.fsGuardianPin;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD;isSaveEnabled=false;setHint(R.string.fs_parent_pin) }
        val dialog=MaterialAlertDialogBuilder(activity).setTitle(R.string.fs_parent_mode).setMessage(R.string.fs_parent_gate_note).setView(field)
            .setNegativeButton(R.string.cancel) { _,_ -> cancel() }.setPositiveButton(R.string.vault_unlock,null).create()
        dialog.setOnCancelListener { cancel() }
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(-1).setOnClickListener {
                val pin=DeliveryPolicy.digits(field.text.toString());field.text.clear();dialog.getButton(-1).isEnabled=false
                activity.lifecycleScope.launch {
                    val ok=withContext(Dispatchers.IO) { runCatching { verify(activity,pin) }.getOrDefault(false) }
                    if(ok) { dialog.dismiss();allowed() } else { dialog.getButton(-1).isEnabled=true;field.error=activity.getString(R.string.fs_pin_error) }
                }
            }
        };dialog.show()
    }
}

open class GuardianProtectedActivity: AppCompatActivity() {
    // Ordinary settings can be captured for support, but only after the PIN gate.
    protected open val screenshotsAllowed: Boolean = false
    private var prompt=false
    private fun showAuthorizedContent(content: View) {
        if (screenshotsAllowed) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        content.visibility=View.VISIBLE
    }
    override fun onStart() {
        super.onStart()
        if(prompt) return
        val content=findViewById<View>(android.R.id.content)
        content.visibility=View.INVISIBLE
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            val required=withContext(Dispatchers.IO) { runCatching { GuardianGate.enabled(this@GuardianProtectedActivity) }.getOrDefault(true) }
            if(required && !GuardianGate.unlocked() && !isFinishing) {
                prompt=true;content.visibility=View.INVISIBLE
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                GuardianGate.prompt(this@GuardianProtectedActivity) { prompt=false;showAuthorizedContent(content) }
            } else showAuthorizedContent(content)
        }
    }
}

class RecorderApplication: Application(),Application.ActivityLifecycleCallbacks {
    private var started=0
    private val handler=Handler(Looper.getMainLooper())
    private val lock=Runnable { if(started==0) GuardianGate.lock() }
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        rikka.shizuku.ShizukuProvider.disableAutomaticSuiInitialization()
    }
    override fun onCreate() { super.onCreate();registerActivityLifecycleCallbacks(this) }
    override fun onActivityStarted(activity: Activity) { started++;handler.removeCallbacks(lock) }
    override fun onActivityStopped(activity: Activity) { started--;if(started==0 && !activity.isChangingConfigurations) handler.postDelayed(lock,400) }
    override fun onActivityCreated(activity: Activity,state: Bundle?) { }
    override fun onActivityResumed(activity: Activity) { }
    override fun onActivityPaused(activity: Activity) { }
    override fun onActivitySaveInstanceState(activity: Activity,state: Bundle) { }
    override fun onActivityDestroyed(activity: Activity) { }
}
