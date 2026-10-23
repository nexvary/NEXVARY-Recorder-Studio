package com.nexvary.recorder.family

import org.json.JSONObject

/** Progress is local encrypted state; it is independent from cloud authentication. */
object GuidedSetupProgress {
    const val EXTRA_GUIDED = "guidedSetup"
    fun data(root: JSONObject): JSONObject = root.optJSONObject("guidedSetup") ?: JSONObject()
    fun healthSaved(root: JSONObject) { root.put("guidedSetup", data(root).put("healthReviewed", true)) }
    fun move(root: JSONObject, step: Int) { root.put("guidedSetup", data(root).put("step", step.coerceIn(0, 4))) }
    fun complete(root: JSONObject) { root.put("guidedSetup", data(root).put("step", 4).put("complete", true)) }
}
