package com.nexvary.recorder.safety

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-triggered SOS cards, stored privately in app preferences.
 * SMS is composed in the default SMS app for explicit final sending. Location is attached only when available.
 */
class QuickRescueActivity : Activity() {
    private val store by lazy { getSharedPreferences("sos_cards", MODE_PRIVATE) }
    private val cards = mutableListOf<JSONObject>()
    private lateinit var list: LinearLayout
    private var pendingIndex: Int? = null
    private var editingImage: Uri? = null
    private var imageStatusView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingIndex = savedInstanceState?.getInt("pending_sos_index", -1)?.takeIf { it >= 0 }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
        }
        root.addView(TextView(this).apply { text = "الإنقاذ السريع — NEXVARY SOS"; textSize = 23f })
        root.addView(TextView(this).apply {
            text = "أضف أرقامًا ورسائل مختلفة. اضغط إرسال لكل جهة. يضاف رابط الموقع عند توفر إذن وموقع حديث. عند الخطر اتصل بالطوارئ أيضًا."
        })
        root.addView(Button(this).apply {
            text = "إضافة جهة استغاثة"
            setOnClickListener { editCard(null) }
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) })
        setContentView(root)
        load()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pending_sos_index", pendingIndex ?: -1)
        super.onSaveInstanceState(outState)
    }

    private fun load() {
        val saved = runCatching { JSONArray(store.getString("cards", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until saved.length()) saved.optJSONObject(i)?.let(cards::add)
    }

    private fun persist() {
        val array = JSONArray()
        cards.forEach(array::put)
        store.edit().putString("cards", array.toString()).apply()
    }

    private fun editCard(index: Int?) {
        val original = index?.let { cards[it] }
        editingImage = original?.optString("image")?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val name = EditText(this).apply {
            hint = "اسم جهة الاتصال"; setSingleLine(true)
            setText(original?.optString("name").orEmpty())
        }
        val phone = EditText(this).apply {
            hint = "رقم الهاتف"; inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(original?.optString("phone").orEmpty())
        }
        val message = EditText(this).apply {
            hint = "رسالة الاستغاثة"; minLines = 3
            setText(original?.optString("message").orEmpty())
        }
        val includeLocation = CheckBox(this).apply {
            text = "إرفاق موقعي الحالي"
            isChecked = original?.optBoolean("location", true) ?: true
        }
        val imageStatus = TextView(this).apply { text = if (editingImage == null) "لا توجد صورة" else "صورة مرفقة" }
        imageStatusView = imageStatus
        val imageButton = Button(this).apply {
            text = "اختيار أو تغيير الصورة من الهاتف"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }, 602)
            }
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            listOf(name, phone, message, includeLocation, imageStatus, imageButton).forEach(::addView)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (index == null) "إضافة جهة" else "تعديل جهة")
            .setView(form)
            .setPositiveButton("حفظ") { _, _ ->
                val number = phone.text.toString().trim()
                val body = message.text.toString().trim()
                if (name.text.isBlank() || !number.matches(Regex("^\\+?[0-9]{7,15}$")) || body.isBlank()) {
                    Toast.makeText(this, "تحقق من الاسم ورقم الهاتف والرسالة", Toast.LENGTH_LONG).show()
                } else {
                    val item = JSONObject().put("name", name.text.toString().trim())
                        .put("phone", number).put("message", body)
                        .put("location", includeLocation.isChecked)
                        .put("image", editingImage?.toString().orEmpty())
                    if (index == null) cards.add(item) else cards[index] = item
                    persist(); render()
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun render() {
        list.removeAllViews()
        cards.forEachIndexed { index, card ->
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 16, 8, 16)
            }
            panel.addView(TextView(this).apply {
                text = "${card.optString("name")} — ${card.optString("phone")}"
                textSize = 19f
            })
            panel.addView(TextView(this).apply { text = card.optString("message") })
            panel.addView(Button(this).apply {
                text = "إرسال استغاثة"
                setOnClickListener { requestSend(index) }
            })
            if (card.optString("image").isNotBlank()) panel.addView(TextView(this).apply { text = "📎 صورة مرفقة" })
            panel.addView(Button(this).apply {
                text = "تعديل"
                setOnClickListener { editCard(index) }
            })
            panel.addView(Button(this).apply {
                text = "حذف"
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@QuickRescueActivity)
                        .setMessage("حذف جهة الاستغاثة؟")
                        .setPositiveButton("حذف") { _, _ ->
                            cards.removeAt(index); persist(); render()
                        }.setNegativeButton("إلغاء", null).show()
                }
            })
            list.addView(panel)
        }
    }

    private fun requestSend(index: Int) {
        val card = cards.getOrNull(index) ?: return
        if (card.optBoolean("location", true) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingIndex = index
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 401)
            return
        }
        send(index)
    }

    private fun send(index: Int) {
        val card = cards.getOrNull(index) ?: return
        var body = card.optString("message")
        if (card.optBoolean("location", true)) {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            val location = runCatching {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) null
                else manager.getProviders(true).mapNotNull { provider ->
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                }.filter { System.currentTimeMillis() - it.time in 0..120000 }
                    .maxByOrNull { it.time }
            }.getOrNull()
            body += if (location == null) "\nالموقع الحالي غير متاح أو غير حديث."
            else "\nموقعي: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        }
        val phone = card.optString("phone")
        // Default path uses the system SMS app so delivery remains user-visible.
        val image = card.optString("image").takeIf { it.isNotBlank() }?.let(Uri::parse)
        val sms = if (image != null) Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, image)
            putExtra(Intent.EXTRA_TEXT, body + "\nالرقم المقصود: " + phone)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}"))
            .putExtra("sms_body", body)
        runCatching { startActivity(if (image != null) Intent.createChooser(sms, "مشاركة الاستغاثة والصورة") else sms) }.onFailure {
            Toast.makeText(this, "لا يوجد تطبيق SMS متاح", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Uses legacy result for document picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 602 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                editingImage = uri
                imageStatusView?.text = "تم اختيار صورة: ${uri.lastPathSegment ?: "صورة"}"
                Toast.makeText(this, "تم اختيار الصورة. اضغط حفظ لإرفاقها", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 401) {
            val index = pendingIndex
            pendingIndex = null
            // Never silently abandon an SOS message when location permission is denied.
            // send() explicitly marks the location unavailable in that case.
            if (index != null) send(index)
        }
    }
}
