package com.nexvary.recorder

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.nexvary.recorder.guardian.ChildMode
import com.nexvary.recorder.ui.FeatureCards
import com.nexvary.recorder.ui.StudioPage
import com.nexvary.recorder.ui.ThemeManager

/** Fully bundled help: no network, account, subscription or external browser required. */
class GuideActivity : AppCompatActivity() {
    private data class Topic(val id: Int, val question: Int, val answer: Int)
    private val topics = listOf(
        Topic(R.id.guideFaqRecord, R.string.guide_q_record, R.string.guide_a_record),
        Topic(R.id.guideFaqBackground, R.string.guide_q_background, R.string.guide_a_background),
        Topic(R.id.guideFaqSos, R.string.guide_q_sos, R.string.guide_a_sos),
        Topic(R.id.guideFaqFree, R.string.guide_q_free, R.string.guide_a_free),
        Topic(R.id.guideFaqFamily, R.string.guide_q_family, R.string.guide_a_family),
        Topic(R.id.guideFaqCloud, R.string.guide_q_cloud, R.string.guide_a_cloud),
        Topic(R.id.guideFaqEvidence, R.string.guide_q_evidence, R.string.guide_a_evidence),
        Topic(R.id.guideFaqLibrary, R.string.guide_q_library, R.string.guide_a_library),
        Topic(R.id.guideFaqMedia, R.string.guide_q_media, R.string.guide_a_media),
        Topic(R.id.guideFaqTrouble, R.string.guide_q_trouble, R.string.guide_a_trouble),
        Topic(R.id.guideFaqCalls, R.string.guide_q_calls, R.string.guide_a_calls),
        Topic(R.id.guideFaqTest, R.string.guide_q_test, R.string.guide_a_test)
    )
    private lateinit var search: EditText
    private lateinit var results: LinearLayout
    private lateinit var count: TextView
    private lateinit var expand: Button
    private val expanded = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this); super.onCreate(savedInstanceState)
        expanded.addAll(savedInstanceState?.getIntArray("expanded")?.toList() ?: listOf(R.id.guideFaqRecord))
        val root = StudioPage.install(this, R.string.guide_title)
        val pad = (12 * resources.displayMetrics.density).toInt()
        root.addView(TextView(this).apply { setText(R.string.guide_intro); textSize = 16f; setPadding(0, 0, 0, pad) })
        search = EditText(this).apply {
            id = R.id.guideSearch; setHint(R.string.guide_search)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            isSingleLine = true; isSaveEnabled = false
            setTextColor(0xFFF5F7FA.toInt()); setHintTextColor(0xFFB9C4D0.toInt())
            textDirection = View.TEXT_DIRECTION_LOCALE
        }
        root.addView(search)
        count = TextView(this).apply { id = R.id.guideResults; textSize = 14f; setPadding(0, pad, 0, pad) }
        root.addView(count)
        expand = Button(this).apply {
            id = R.id.guideExpand
            setOnClickListener {
                if (expanded.size == topics.size) expanded.clear() else expanded.addAll(topics.map { it.id })
                render()
            }
        }
        root.addView(expand)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)
        root.addView(TextView(this).apply {
            setText(R.string.guide_quick_start); textSize = 16f
            setBackgroundResource(R.drawable.bg_metal_card); setPadding(pad, pad, pad, pad)
        })
        val shortcuts = mutableListOf(FeatureCards.Item(R.id.guideQuickSos, R.drawable.asset_sos, R.string.rescue_title, R.string.more_rescue_note, true) {
            startActivity(Intent(this, com.nexvary.recorder.guardian.GuardianEmergencyActivity::class.java))
        })
        if (!ChildMode.enabled(this)) {
            shortcuts.add(FeatureCards.Item(R.id.guideQuickCamera, R.drawable.asset_camera, R.string.camera_title, R.string.more_camera_note) {
                startActivity(Intent(this, com.nexvary.recorder.camera.BackgroundCameraActivity::class.java))
            })
            shortcuts.add(FeatureCards.Item(R.id.guideQuickLibrary, R.drawable.asset_library, R.string.library_title, R.string.more_library_note) {
                startActivity(Intent(this, com.nexvary.recorder.media.RecordingsLibraryActivity::class.java))
            })
        }
        FeatureCards.grid(root, shortcuts)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        search.setText(savedInstanceState?.getString("query") ?: intent.getStringExtra("query").orEmpty())
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", search.text.toString())
        outState.putIntArray("expanded", expanded.toIntArray())
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        val query = search.text.toString().trim()
        val visible = topics.filter { query.isBlank() || (getString(it.question) + " " + getString(it.answer)).contains(query, ignoreCase = true) }
        count.text = getString(R.string.guide_results, visible.size)
        expand.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
        expand.setText(if (expanded.size == topics.size) R.string.guide_collapse_all else R.string.guide_expand_all)
        results.removeAllViews()
        val pad = (12 * resources.displayMetrics.density).toInt()
        if (visible.isEmpty()) results.addView(TextView(this).apply { setText(R.string.guide_empty); textSize = 17f; setPadding(0, pad, 0, pad) })
        for (topic in visible) {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_metal_card)
                setPadding(pad, pad / 2, pad, pad / 2)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pad }
            }
            val answer = TextView(this).apply {
                setText(topic.answer); textSize = 16f; setPadding(pad / 2, pad / 2, pad / 2, pad)
                visibility = if (query.isNotBlank() || topic.id in expanded) View.VISIBLE else View.GONE
            }
            val heading = androidx.appcompat.widget.AppCompatButton(this).apply {
                id = topic.id; setText(topic.question); isAllCaps = false; textSize = 17f
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setTextColor(0xFF9BF6B8.toInt()); backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                minHeight = (56 * resources.displayMetrics.density).toInt()
                setTag(R.id.studioStyled, true)
                fun updateState() { ViewCompat.setStateDescription(this, getString(if (answer.visibility == View.VISIBLE) R.string.guide_expanded else R.string.guide_collapsed)) }
                updateState()
                setOnClickListener {
                    if (answer.visibility == View.VISIBLE) { answer.visibility = View.GONE; expanded.remove(topic.id) }
                    else { answer.visibility = View.VISIBLE; expanded.add(topic.id) }
                    updateState()
                    expand.setText(if (expanded.size == topics.size) R.string.guide_collapse_all else R.string.guide_expand_all)
                }
            }
            panel.addView(heading, LinearLayout.LayoutParams(-1, -2))
            panel.addView(answer, LinearLayout.LayoutParams(-1, -2))
            results.addView(panel)
        }
    }
}
