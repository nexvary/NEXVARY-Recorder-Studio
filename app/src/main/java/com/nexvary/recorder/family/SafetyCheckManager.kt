package com.nexvary.recorder.family

import android.app.*
import android.content.*
import android.os.*
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.nexvary.recorder.R
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SafetyCheckManager(private val context: Context) {
    private val store = FamilyStore(context)
    private fun boot() = Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)
    fun start(minutes: Long, graceSeconds: Int, journey: JSONObject? = null): SafetyState {
        require(SafetyPolicy.validMinutes(minutes) && SafetyPolicy.validGrace(graceSeconds))
        if(journey!=null) check(com.nexvary.recorder.billing.EntitlementManager(context).canUse(com.nexvary.recorder.billing.SafetyFeature.JOURNEY))
        if(journey!=null) require(FamilyStore.contacts(store.snapshot()).let { contacts -> (0 until contacts.length()).any { contacts.getJSONObject(it).optString("id")==journey.optString("contactId") } })
        val state = SafetyState(UUID.randomUUID().toString(),SafetyPhase.ARMED,
            SafetyDeadline(System.currentTimeMillis()+minutes*60_000,SystemClock.elapsedRealtime()+minutes*60_000,boot()),graceSeconds,if(journey==null) "CHECK_IN" else "JOURNEY_TIMEOUT")
        store.update { root ->
            check(FamilyStore.state(root)?.phase !in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN,SafetyPhase.SOS_ACTIVE))
            FamilyStore.putState(root,state); root.remove("alertPrepared");root.remove("batterySent");root.remove("arrivalCandidate");root.remove("reminderSent");root.remove("batterySaverActive")
            if(journey != null) root.put("journey",journey) else root.remove("journey")
            FamilyStore.event(root,if(journey == null) "CHECK_STARTED" else "JOURNEY_STARTED")
        }
        schedule();notifyState(state);return state
    }
    fun sos(immediate: Boolean = false, reason: String = "SOS", expectedId: String? = null): SafetyState {
        val state = store.update { root ->
            val existing = FamilyStore.state(root)
            if(expectedId!=null) check(existing!=null && existing.id==expectedId && existing.phase in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN,SafetyPhase.SOS_ACTIVE))
            if(existing?.phase == SafetyPhase.SOS_ACTIVE) {
                FamilyStore.event(root,"SOS_MARKER",existing.id)
                root.optJSONObject("lastLocation")?.let { location ->
                    val markers=root.optJSONArray("sosMarkers") ?: org.json.JSONArray().also { root.put("sosMarkers",it) }
                    if(markers.length()<1000) markers.put(JSONObject().put("eventId",existing.id).put("timestamp",System.currentTimeMillis()).put("location",location))
                }
                return@update existing
            }
            if(existing?.phase == SafetyPhase.COUNTDOWN && !immediate) return@update existing
            if(existing?.phase !in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN)) {
                root.remove("journey");root.remove("batterySent");root.remove("batterySaverActive");root.remove("locationAlertFor");root.remove("reminderSent")
            }
            val state = SafetyState(existing?.takeIf { it.phase in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN) }?.id ?: UUID.randomUUID().toString(),
                if(immediate) SafetyPhase.SOS_ACTIVE else SafetyPhase.COUNTDOWN,
                SafetyDeadline(System.currentTimeMillis()+20_000,SystemClock.elapsedRealtime()+20_000,boot()),20,reason)
            FamilyStore.putState(root,state);root.remove("alertPrepared");FamilyStore.event(root,if(immediate) "SOS_STARTED" else "SOS_COUNTDOWN")
            state
        }
        if(state.phase == SafetyPhase.SOS_ACTIVE) prepareEmergency()
        schedule();notifyState(state);return state
    }
    fun resolve(expectedId: String?, cancelled: Boolean = false, journeyArrival: Boolean = false): Boolean {
        val changed = store.update { root ->
            val old = FamilyStore.state(root) ?: return@update false
            if(expectedId != null && old.id != expectedId) return@update false
            if(old.phase !in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN,SafetyPhase.SOS_ACTIVE)) return@update false
            if(journeyArrival) {
                if(!SafetyPolicy.arrivalAllowed(old)) return@update false
                val payload=AlertPayload.create(context,root,"SAFE_ARRIVAL",UUID.randomUUID().toString())
                EmergencyQueue.enqueue(root,payload);FamilyStore.event(root,"JOURNEY_COMPLETED")
            }
            FamilyStore.putState(root,old.copy(phase=if(cancelled) SafetyPhase.CANCELLED else SafetyPhase.RESOLVED))
            FamilyStore.event(root,if(cancelled) "SOS_CANCELLED" else "CHECK_CONFIRMED")
            FamilyStore.jobs(root).let { jobs -> jobs.keys().forEach { id -> jobs.getJSONObject(id).let { job ->
                val payload=job.optJSONObject("payload")
                if(payload?.optString("sessionId")==old.id && payload.optString("eventType") !in listOf("SAFE_ARRIVAL","TEST_ALERT") && job.optString("status") !in listOf("SENT","SENDING")) job.put("status","CANCELLED")
            } } }
            true
        }
        if(changed) {
            context.stopService(Intent(context,SafetyLocationService::class.java))
            EvidenceManager.stopOwned(context,expectedId)
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
            schedule();if(journeyArrival) SafetyAlertWorker.reconcile(context)
        }
        return changed
    }
    fun extend(expectedId: String?, minutes: Long = 15): Boolean {
        require(SafetyPolicy.validMinutes(minutes))
        val changed = store.update { root ->
            val state = FamilyStore.state(root) ?: return@update false
            if(state.id != expectedId || state.phase !in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN)) return@update false
            FamilyStore.putState(root,state.copy(phase=SafetyPhase.ARMED,
                deadline=SafetyDeadline(System.currentTimeMillis()+minutes*60_000,SystemClock.elapsedRealtime()+minutes*60_000,boot())))
            root.remove("reminderSent");FamilyStore.event(root,"CHECK_EXTENDED");true
        }
        if(changed) { schedule(); FamilyStore.state(store.snapshot())?.let(::notifyState) };return changed
    }
    fun evaluate(reschedule: Boolean = false) {
        val changed = store.update { root ->
            val before = FamilyStore.state(root) ?: return@update null
            val after = SafetyPolicy.evaluate(before,System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot())
            if(before == after) {
                if(before.phase==SafetyPhase.ARMED && before.deadline.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot())<=60_000 && root.optString("reminderSent")!=before.id) {
                    root.put("reminderSent",before.id);FamilyStore.event(root,"CHECK_REMINDER");return@update before
                }
                return@update null
            }
            FamilyStore.putState(root,after);FamilyStore.event(root,if(after.phase == SafetyPhase.COUNTDOWN) "CHECK_PROMPTED" else "SOS_STARTED")
            after
        }
        if(changed != null) notifyState(changed)
        prepareEmergency();if(changed != null || reschedule) schedule();SafetyAlertWorker.reconcile(context)
    }
    private fun prepareEmergency() {
        store.update { root ->
            val state = FamilyStore.state(root) ?: return@update
            if(state.phase != SafetyPhase.SOS_ACTIVE || root.optString("alertPrepared") == state.id) return@update
            val payload = AlertPayload.create(context,root,state.reason,state.id,state.test)
            root.put("lastAlert",payload);EmergencyQueue.enqueue(root,payload)
            root.put("alertPrepared",state.id)
            if(FamilyStore.config(root).optString("evidence","NONE") != "NONE") {
                EvidenceManager.action(root,state.id,"USER_ACTION_REQUIRED")
                FamilyStore.event(root,"EVIDENCE_USER_ACTION_REQUIRED")
            }
        }
        SafetyAlertWorker.reconcile(context)
    }
    fun emit(type: String, manual: Boolean = false, test: Boolean = false) {
        store.update { root ->
            if(!manual && !test && FamilyStore.state(root)?.phase !in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN,SafetyPhase.SOS_ACTIVE)) return@update
            val payload = AlertPayload.create(context,root,type,UUID.randomUUID().toString(),test)
            root.put("lastAlert",payload);EmergencyQueue.enqueue(root,payload,manual)
            FamilyStore.event(root,if(test) "TEST_ALERT_QUEUED" else type)
        }
        SafetyAlertWorker.reconcile(context)
    }
    fun schedule() {
        val state = FamilyStore.state(store.snapshot())
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context,10,Intent(context,SafetyActionReceiver::class.java).setAction("TICK"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.cancel(pending)
        val work = WorkManager.getInstance(context)
        if(state == null || state.phase !in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN)) { work.cancelUniqueWork("safety-deadline");return }
        val remaining = state.deadline.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot())
        val delay = (if(state.phase==SafetyPhase.ARMED && store.snapshot().optString("reminderSent")!=state.id && remaining>60_000) remaining-60_000 else remaining).coerceAtLeast(100)
        try {
            if(Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                val open = PendingIntent.getActivity(context,10,Intent(context,FamilyDashboardActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis()+delay,open),pending)
            }
            else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+delay,pending)
        } catch(_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+delay,pending) }
        work.enqueueUniqueWork("safety-deadline",ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SafetyTickWorker>().setInitialDelay(delay,TimeUnit.MILLISECONDS).build())
    }
    fun notifyState(state: SafetyState) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,context.getString(R.string.fs_title),NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null,null);enableVibration(false);lockscreenVisibility=Notification.VISIBILITY_PRIVATE
        })
        val audible=state.reason!="SILENT_SOS" && FamilyStore.config(store.snapshot()).optBoolean("audible") && state.phase in listOf(SafetyPhase.COUNTDOWN,SafetyPhase.SOS_ACTIVE)
        if(audible) manager.createNotificationChannel(NotificationChannel(AUDIBLE_CHANNEL,context.getString(R.string.fs_audible),NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility=Notification.VISIBILITY_PRIVATE })
        fun action(name: String) = PendingIntent.getBroadcast(context,name.hashCode(),Intent(context,SafetyActionReceiver::class.java).setAction(name).putExtra("id",state.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context,0,Intent(context,FamilyDashboardActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = if(state.phase == SafetyPhase.COUNTDOWN || (state.phase==SafetyPhase.ARMED && store.snapshot().optString("reminderSent")==state.id)) R.string.fs_are_safe else if(state.phase == SafetyPhase.SOS_ACTIVE) R.string.fs_sos_active else R.string.fs_check_active
        val notification = NotificationCompat.Builder(context,if(audible) AUDIBLE_CHANNEL else CHANNEL).setSmallIcon(R.drawable.ic_feature_screen)
            .setContentTitle(context.getString(R.string.fs_title)).setContentText(context.getString(message)).setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOngoing(state.phase in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN))
            .addAction(0,context.getString(R.string.fs_safe),action("SAFE"))
            .addAction(0,context.getString(R.string.fs_extend),action("EXTEND"))
            .addAction(0,context.getString(R.string.fs_sos),action("TRIGGER_SOS")).setAutoCancel(false).build()
        runCatching { manager.notify(NOTIFICATION,notification) }
    }
    companion object { const val CHANNEL = "family_safety_silent";const val AUDIBLE_CHANNEL="family_safety_audible";const val NOTIFICATION = 3601 }
}

class SafetyTickWorker(context: Context,params: WorkerParameters): Worker(context,params) {
    override fun doWork(): Result = try { SafetyCheckManager(applicationContext).evaluate(true);Result.success() } catch(_: Exception) { Result.retry() }
}
class SafetyActionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                val manager = SafetyCheckManager(context)
                when(intent.action) {
                    "SAFE" -> manager.resolve(intent.getStringExtra("id"))
                    "EXTEND" -> manager.extend(intent.getStringExtra("id"))
                    "TRIGGER_SOS" -> if(FamilyStore.state(FamilyStore(context).snapshot())?.id == intent.getStringExtra("id")) manager.sos(expectedId=intent.getStringExtra("id"))
                    else -> manager.evaluate(true)
                }
            } catch(_: Exception) { WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SafetyTickWorker>().build()) }
            finally { pending.finish() }
        }
    }
    companion object { private val EXECUTOR = Executors.newSingleThreadExecutor() }
}
