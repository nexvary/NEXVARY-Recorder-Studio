package com.nexvary.recorder.family

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.location.*
import android.net.*
import android.os.*
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/** Only an explicitly started safety session requests location. No background-location permission. */
class SafetyLocationService: Service(), LocationListener, SensorEventListener {
    private val thread = HandlerThread("FamilySafety").apply { start() }
    private val handler = Handler(thread.looper)
    private val store by lazy { FamilyStore(this) }
    private val location by lazy { getSystemService(LocationManager::class.java) }
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private var motion: MotionDetector? = null
    private var previousBattery = 101
    private var lastNetwork = ""
    private var arrivalCandidate = 0L
    private var registered = false
    private var critical = false
    private val tick = object: Runnable {
        override fun run() {
            runCatching {
                val state = FamilyStore.state(store.snapshot())
                if(state?.phase !in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN,SafetyPhase.SOS_ACTIVE)) { stopSelf();return }
                SafetyCheckManager(this@SafetyLocationService).evaluate()
            }
            handler.postDelayed(this,5_000)
        }
    }
    private val battery = object: BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) { val level=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1); handler.post { runCatching { batteryChanged(level) } } }
    }
    private val network = object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(net: Network) { handler.post { runCatching { connectivityChanged() } } }
        override fun onLost(net: Network) { handler.post { runCatching { connectivityChanged() } } }
        override fun onCapabilitiesChanged(net: Network,capabilities: NetworkCapabilities) { handler.post { runCatching { connectivityChanged() } } }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(registered) return START_NOT_STICKY
        val allowed = ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if(!allowed) { stopSelf();return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("family_location",getString(com.nexvary.recorder.R.string.fs_location_active),NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null) })
        val open=PendingIntent.getActivity(this,0,Intent(this,FamilyDashboardActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"family_location").setSmallIcon(com.nexvary.recorder.R.drawable.ic_feature_screen)
            .setContentTitle(getString(com.nexvary.recorder.R.string.fs_title)).setContentText(getString(com.nexvary.recorder.R.string.fs_location_active)).setContentIntent(open).setOngoing(true).build()
        try {
            if(Build.VERSION.SDK_INT >= 29) startForeground(3602,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(3602,notification)
        } catch(_: Exception) { stopSelf();return START_NOT_STICKY }
        registered=true
        handler.post {
            runCatching {
                val config=FamilyStore.config(store.snapshot())
                check(config.optBoolean("location"))
                registerLocations(60_000)
                ContextCompat.registerReceiver(this,battery,IntentFilter(Intent.ACTION_BATTERY_CHANGED),ContextCompat.RECEIVER_NOT_EXPORTED)
                getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(network)
                if(config.optBoolean("motion")) {
                    motion=MotionDetector(config.optInt("sensitivity",1))
                    sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_UI,handler) }
                }
                handler.post(tick)
            }.onFailure { stopSelf() }
        }
        // A restart must be explicitly initiated from a visible screen after reboot/process death.
        return START_NOT_STICKY
    }
    private fun registerLocations(interval: Long) {
        runCatching { location.removeUpdates(this) }
        location.getProviders(true).filter { it in listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER) }.forEach { provider ->
            runCatching { location.requestLocationUpdates(provider,interval,25f,this,thread.looper) }
        }
    }
    override fun onLocationChanged(fix: Location) { runCatching { acceptLocation(fix) } }
    private fun acceptLocation(fix: Location) {
        if(!fix.hasAccuracy() || !fix.latitude.isFinite() || !fix.longitude.isFinite()) return
        val age=(SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1_000_000
        if(age !in 0..120_000) return
        var arrived=false
        store.update { root ->
            if(FamilyStore.state(root)?.phase !in listOf(SafetyPhase.ARMED,SafetyPhase.COUNTDOWN,SafetyPhase.SOS_ACTIVE)) return@update
            val snapshot=JSONObject().put("latitude",fix.latitude).put("longitude",fix.longitude).put("accuracy",fix.accuracy)
                .put("timestamp",fix.time).put("elapsed",fix.elapsedRealtimeNanos/1_000_000).put("boot",android.provider.Settings.Global.getInt(contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)).put("source","ACTIVE_SAFETY_SESSION")
            root.put("lastLocation",snapshot)
            val trail=root.optJSONArray("trail") ?: JSONArray();val next=JSONArray()
            for(i in maxOf(0,trail.length()-719) until trail.length()) next.put(trail.get(i))
            next.put(snapshot);root.put("trail",next)
            root.optJSONObject("journey")?.let { journey ->
                val result=FloatArray(1)
                Location.distanceBetween(fix.latitude,fix.longitude,journey.getDouble("latitude"),journey.getDouble("longitude"),result)
                journey.put("distanceMeters",result[0])
                if(SafetyPolicy.arrived(result[0],fix.accuracy,age)) {
                    if(arrivalCandidate == 0L) arrivalCandidate=SystemClock.elapsedRealtime()
                    arrived=SystemClock.elapsedRealtime()-arrivalCandidate >= 10_000
                } else arrivalCandidate=0
            }
        }
        if(arrived) {
            SafetyCheckManager(this).emit("SAFE_ARRIVAL")
            val state=FamilyStore.state(store.snapshot());SafetyCheckManager(this).resolve(state?.id)
            store.update { FamilyStore.event(it,"JOURNEY_COMPLETED") }
        }
    }
    private fun batteryChanged(level: Int) {
        if(level !in 0..100) return
        val config=FamilyStore.config(store.snapshot())
        val sent=store.snapshot().optJSONArray("batterySent")?.let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() } ?: emptySet()
        if(config.optBoolean("batteryAlerts")) SafetyPolicy.batteryCrossings(previousBattery,level,sent).forEach { threshold ->
            store.update { root -> val rows=root.optJSONArray("batterySent") ?: JSONArray();rows.put(threshold);root.put("batterySent",rows) }
            SafetyCheckManager(this).emit("LOW_BATTERY_$threshold")
        }
        if(level <= 5 && config.optBoolean("batterySaver") && !critical) {
            critical=true;registerLocations(300_000);sensors.unregisterListener(this)
            store.update { root -> root.put("batterySaverActive",true);FamilyStore.event(root,"BATTERY_SAVER_ACTIVE") }
        }
        previousBattery=level
    }
    private fun connectivityChanged() {
        val value=AlertPayload.network(this)
        if(value != lastNetwork) {
            store.update { FamilyStore.event(it,if(value == "OFFLINE") "NETWORK_LOST" else "NETWORK_CHANGED",value) }
            lastNetwork=value
            if(value !in listOf("OFFLINE","UNVALIDATED")) runCatching { SafetyAlertWorker.reconcile(this) }
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        if(event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
        motion?.sample(event.values[0],event.values[1],event.values[2],SystemClock.elapsedRealtime())?.let { g ->
            runCatching {
                store.update { root -> root.put("lastImpact",JSONObject().put("g",g.toDouble()).put("timestamp",System.currentTimeMillis()));FamilyStore.event(root,"POSSIBLE_IMPACT") }
                SafetyCheckManager(this).sos(false,"POSSIBLE_IMPACT")
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?,accuracy: Int) { }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { location.removeUpdates(this) };sensors.unregisterListener(this)
        runCatching { unregisterReceiver(battery) };runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(network) }
        thread.quitSafely();super.onDestroy()
    }
}
