package com.nexvary.recorder.delivery

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nexvary.recorder.R

/** One mailbox per action. The transport's canonical list stays private to this component. */
class RecipientEditor(context: Context) : LinearLayout(context) {
    private val addresses = mutableListOf<String>()
    private val input: android.widget.EditText
    private val add: android.widget.Button
    private val chips: ChipGroup
    private val count: TextView
    private var restoring = false
    private var ready = false
    var userEdited = false
        private set
    val draft: String get() = input.text.toString()
    val canonical: String get() = addresses.joinToString(", ")
    init {
        orientation = VERTICAL; id = R.id.recipientEditor; isSaveEnabled = false
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        input = DeliveryUi.field(this, R.string.recipient_entry_label, R.id.cameraRecipient,
            type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).apply {
            hint = "name@example.com"; isSaveEnabled = false; layoutDirection = LAYOUT_DIRECTION_LTR
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
            filters = arrayOf(android.text.InputFilter.LengthFilter(MailRecipients.MAX_INPUT))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { addDraft(); true } else false
            }
            doAfterTextChanged { if (!restoring) userEdited = true; error = null }
        }
        removeView(input); row.addView(input, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add = DeliveryUi.button(row, R.string.recipient_add, R.id.recipientAdd) { addDraft() }.apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) }
            minWidth = dp(80); minHeight = dp(48)
        }
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        count = DeliveryUi.text(this, R.string.camera_recipients_label).apply {
            id = R.id.recipientCount; textSize = 14f; accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        }
        chips = ChipGroup(context).apply {
            id = R.id.recipientChips; chipSpacingHorizontal = dp(6); chipSpacingVertical = dp(4)
            isSingleSelection = false; isSaveEnabled = false
        }
        addView(chips, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        DeliveryUi.text(this, R.string.camera_recipients_hint)
        ready = true; render()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    fun restore(recipients: String, draft: String = "", edited: Boolean = false) {
        restoring = true
        try { addresses.clear(); addresses.addAll(MailRecipients.parse(recipients).orEmpty()); input.setText(draft) }
        finally { restoring = false }
        userEdited = edited; render()
    }
    fun loadDefaults(recipients: String) {
        if (!userEdited && addresses.isEmpty() && input.text.isBlank()) restore(recipients)
    }
    private fun addDraft(): Boolean {
        if (!isEnabled) return false
        val address = draft.trim()
        val error = when {
            !DeliveryPolicy.email(address) -> R.string.recipient_single_invalid
            addresses.any { it.equals(address, ignoreCase = true) } -> R.string.recipient_duplicate
            addresses.size >= MailRecipients.MAX_RECIPIENTS -> R.string.camera_recipients_invalid
            else -> null
        }
        if (error != null) { input.error = context.getString(error); return false }
        addresses.add(address); input.text.clear(); userEdited = true; render(); return true
    }
    /** A valid unfinished draft is included before consent; an invalid one blocks sending. */
    fun confirmedRecipients(): String? {
        if (draft.isNotBlank() && !addDraft()) return null
        if (addresses.isEmpty()) { input.error = context.getString(R.string.camera_recipients_invalid); return null }
        return canonical
    }
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        // Superclass initialization can call this before child views exist.
        if (ready) updateEnabled()
    }
    private fun updateEnabled() {
        input.isEnabled = isEnabled && addresses.size < MailRecipients.MAX_RECIPIENTS
        add.isEnabled = input.isEnabled
        for (index in 0 until chips.childCount) chips.getChildAt(index).isEnabled = isEnabled
    }
    private fun render() {
        count.text = context.getString(R.string.recipient_count, addresses.size, MailRecipients.MAX_RECIPIENTS)
        chips.removeAllViews()
        addresses.forEach { address ->
            chips.addView(Chip(context).apply {
                id = View.generateViewId(); text = address; textDirection = TEXT_DIRECTION_LTR
                layoutDirection = LAYOUT_DIRECTION_LTR; isAllCaps = false
                isCheckable = false; isClickable = false; isSaveEnabled = false
                setEnsureMinTouchTargetSize(true); minHeight = dp(48)
                setTextColor(0xFF43FF88.toInt()); chipBackgroundColor = ColorStateList.valueOf(0xFF171D20.toInt())
                chipStrokeColor = ColorStateList.valueOf(0xFFAEB9C4.toInt()); chipStrokeWidth = dp(1).toFloat()
                setCloseIconResource(android.R.drawable.ic_menu_close_clear_cancel); isCloseIconVisible = true
                closeIconTint = ColorStateList.valueOf(0xFFFF6377.toInt())
                closeIconContentDescription = context.getString(R.string.recipient_remove, address)
                ellipsize = TextUtils.TruncateAt.MIDDLE; contentDescription = address
                setTag(R.id.studioStyled, true); TooltipCompat.setTooltipText(this, address)
                setOnCloseIconClickListener { addresses.remove(address); userEdited = true; render() }
            })
        }
        boundChips(width); updateEnabled()
    }
    private fun boundChips(available: Int) {
        if (available > 0) for (index in 0 until chips.childCount)
            (chips.getChildAt(index) as Chip).maxWidth = (available - paddingLeft - paddingRight).coerceAtLeast(dp(48))
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); boundChips(w) }
}
