package com.nexvary.recorder.safety

import android.Manifest
import android.content.res.ColorStateList
import android.view.ViewGroup
import android.view.WindowManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexvary.recorder.R
import com.nexvary.recorder.databinding.ActivityQuickRescueBinding
import com.nexvary.recorder.ui.ThemeManager
import com.nexvary.recorder.ui.UiInsets
import org.json.JSONArray
import org.json.JSONObject

/** No SEND_SMS permission: every message is confirmed and composed in an external app. */
class QuickRescueActivity : AppCompatActivity() {
    private val store by lazy { getSharedPreferences("sos_cards", MODE_PRIVATE) }
    private val cards = mutableListOf<JSONObject>()
    private lateinit var binding: ActivityQuickRescueBinding
    private var pendingIndex: Int? = null
    private var editingImage: Uri? = null
    private var imageStatus: TextView? = null
    private var editorIndex: Int? = null
    private var editorFields: List<EditText>? = null
    private var editorLocation: CheckBox? = null
    private var editorOpen = false
    private var editorDialog: androidx.appcompat.app.AlertDialog? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            editingImage = uri
            imageStatus?.setText(R.string.rescue_image_attached)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityQuickRescueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsets.apply(binding.root)
        val saved = runCatching { JSONArray(store.getString("cards", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until saved.length()) saved.optJSONObject(i)?.let(cards::add)
        pendingIndex = savedInstanceState?.getInt("pending", -1)?.takeIf { it >= 0 }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddContact.setOnClickListener { editCard(null) }
        binding.btnPrepareRescue.setOnClickListener {
            if (cards.isEmpty()) editCard(null)
            else MaterialAlertDialogBuilder(this).setTitle(R.string.rescue_choose)
                .setItems(cards.map { it.optString("name") }.toTypedArray()) { dialog, i ->
                    dialog.dismiss()
                    binding.root.post { requestSend(i) }
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
        render()
        savedInstanceState?.getString("draft")?.let { draft ->
            val index = savedInstanceState.getInt("editor_index", -1).takeIf { it >= 0 }
            editCard(index, JSONObject(draft))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pending", pendingIndex ?: -1)
        if (editorOpen) {
            outState.putString("draft", draft().toString())
            outState.putInt("editor_index", editorIndex ?: -1)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        editorDialog?.dismiss()
        super.onDestroy()
    }

    private fun persist() {
        store.edit().putString("cards", JSONArray().apply { cards.forEach { put(it) } }.toString()).apply()
    }

    private fun draft(): JSONObject {
        val fields = editorFields.orEmpty()
        return JSONObject().put("name", fields.getOrNull(0)?.text.toString())
            .put("phone", fields.getOrNull(1)?.text.toString())
            .put("message", fields.getOrNull(2)?.text.toString())
            .put("location", editorLocation?.isChecked ?: false)
            .put("image", editingImage?.toString().orEmpty())
    }

    private fun editCard(index: Int?, restored: JSONObject? = null) {
        val original = restored ?: index?.let { cards.getOrNull(it) }
        editorIndex = index
        editingImage = original?.optString("image")?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val fields = listOf(
            EditText(this).apply { id = R.id.rescueName; setHint(R.string.rescue_name); setSingleLine(); setText(original?.optString("name").orEmpty()) },
            EditText(this).apply { id = R.id.rescuePhone; setHint(R.string.rescue_phone); inputType = InputType.TYPE_CLASS_PHONE; setText(original?.optString("phone").orEmpty()) },
            EditText(this).apply { id = R.id.rescueMessage; setHint(R.string.rescue_message); minLines = 3; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; setText(original?.optString("message").orEmpty()) }
        )
        editorFields = fields
        fields.forEach(form::addView)
        editorLocation = CheckBox(this).apply {
            setText(R.string.rescue_location)
            isChecked = original?.optBoolean("location", false) ?: false
            form.addView(this)
        }
        imageStatus = TextView(this).apply {
            setText(if (editingImage == null) R.string.rescue_image_none else R.string.rescue_image_attached)
            form.addView(this)
        }
        form.addView(Button(this).apply {
            setText(R.string.rescue_image)
            setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        })
        form.addView(Button(this).apply {
            setText(R.string.rescue_remove_image)
            setOnClickListener { editingImage = null; imageStatus?.setText(R.string.rescue_image_none) }
        })
        editorOpen = true
        editorDialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (index == null) R.string.rescue_add else R.string.rescue_edit)
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton(R.string.rescue_save, null)
            .setNegativeButton(R.string.cancel, null).create().apply {
                setOnDismissListener { editorOpen = false; editorFields = null; editorLocation = null; imageStatus = null }
                setOnShowListener {
                    styleEditor(form)
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(NEON)
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(NEON)
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val item = draft()
                        val phone = RescuePolicy.normalizePhone(item.optString("phone").trim())
                        if (item.optString("name").isBlank() || !RescuePolicy.validPhone(phone) || item.optString("message").isBlank()) {
                            fields[1].error = getString(R.string.rescue_invalid)
                        } else {
                            item.put("phone", phone).put("name", item.optString("name").trim()).put("message", item.optString("message").trim())
                            if (index == null) cards.add(item) else cards[index] = item
                            persist(); render(); dismiss()
                        }
                    }
                }
                show()
            }
    }


    // Native widgets created with the activity context can inherit OEM light defaults.
    // Explicit state lists keep every editor field legible, including on OPPO builds.
    private fun styleEditor(view: View) {
        when (view) {
            is EditText -> {
                view.setTextColor(NEON); view.setHintTextColor(0xFF9BF6B8.toInt())
                view.backgroundTintList = ColorStateList.valueOf(NEON)
                view.highlightColor = 0x6643FF88
                view.textCursorDrawable?.setTint(NEON)
            }
            is CheckBox -> { view.setTextColor(NEON); view.buttonTintList = ColorStateList.valueOf(NEON) }
            is Button -> { view.setTextColor(0xFF06150C.toInt()); view.backgroundTintList = ColorStateList.valueOf(NEON); view.isAllCaps = false }
            is TextView -> view.setTextColor(NEON)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) styleEditor(view.getChildAt(i))
    }

    companion object { private const val NEON = 0xFF43FF88.toInt() }

    private fun render() {
        binding.contactList.removeAllViews()
        if (cards.isEmpty()) binding.contactList.addView(TextView(this).apply {
            setText(R.string.rescue_empty); setTextColor(0xFFD3DCE5.toInt()); textSize = 16f
        })
        cards.forEachIndexed { index, card ->
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                setBackgroundResource(R.drawable.bg_metal_card)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad }
            }
            panel.addView(TextView(this).apply {
                text = card.optString("name"); textSize = 19f; setTextColor(0xFFFFFFFF.toInt())
            })
            panel.addView(TextView(this).apply {
                text = card.optString("phone"); textSize = 16f; textDirection = View.TEXT_DIRECTION_LTR; setTextColor(0xFFD3DCE5.toInt())
            })
            panel.addView(TextView(this).apply {
                text = card.optString("message"); setTextColor(0xFFD3DCE5.toInt())
            })
            if (card.optString("image").isNotBlank()) panel.addView(TextView(this).apply {
                setText(R.string.rescue_image_attached); setTextColor(0xFFD3DCE5.toInt())
            })
            panel.addView(Button(this).apply { setText(R.string.rescue_prepare); setOnClickListener { requestSend(index) } })
            panel.addView(Button(this).apply { setText(R.string.rescue_edit); setOnClickListener { editCard(index) } })
            panel.addView(Button(this).apply {
                setText(R.string.rescue_delete)
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@QuickRescueActivity).setMessage(R.string.rescue_delete_confirm)
                        .setPositiveButton(R.string.rescue_delete) { _, _ -> cards.removeAt(index); persist(); render() }
                        .setNegativeButton(R.string.cancel, null).show()
                }
            })
            binding.contactList.addView(panel)
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestSend(index: Int) {
        val card = cards.getOrNull(index) ?: return
        if (card.optBoolean("location", false) && !hasLocationPermission()) {
            pendingIndex = index
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 401)
        } else review(index)
    }

    private fun review(index: Int) {
        val card = cards.getOrNull(index) ?: return
        var body = card.optString("message")
        if (card.optBoolean("location", false)) {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            val location = runCatching {
                if (!hasLocationPermission()) null else manager.getProviders(true).mapNotNull {
                    try { manager.getLastKnownLocation(it) } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
                }.filter { RescuePolicy.recentLocation(SystemClock.elapsedRealtimeNanos(), it.elapsedRealtimeNanos) }
                    .maxByOrNull { it.elapsedRealtimeNanos }
            }.getOrNull()
            body += "\n" + if (location == null) getString(R.string.rescue_location_unavailable)
                else getString(R.string.rescue_location_format, "https://maps.google.com/?q=${location.latitude},${location.longitude}")
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.rescue_review)
            .setMessage(getString(R.string.rescue_review_note, card.optString("phone"), body))
            .setPositiveButton(R.string.rescue_continue) { _, _ -> compose(card, body) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun compose(card: JSONObject, body: String) {
        val image = card.optString("image").takeIf { it.isNotBlank() }?.let(Uri::parse)
        runCatching {
            if (image != null) contentResolver.openAssetFileDescriptor(image, "r")?.use { } ?: error("Attachment unavailable")
            val intent = if (image == null) {
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", card.optString("phone"), null)).putExtra("sms_body", body)
            } else Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, image)
                putExtra(Intent.EXTRA_TEXT, body)
                clipData = ClipData.newUri(contentResolver, "SOS attachment", image)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(if (image == null) intent else Intent.createChooser(intent, getString(R.string.rescue_review)))
        }.onFailure { Toast.makeText(this, R.string.rescue_share_error, Toast.LENGTH_LONG).show() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 401) {
            val index = pendingIndex; pendingIndex = null
            if (index != null) review(index)
        }
    }
}
