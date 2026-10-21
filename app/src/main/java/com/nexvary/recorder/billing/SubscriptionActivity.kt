package com.nexvary.recorder.billing

import android.widget.LinearLayout
import android.widget.TextView
import com.nexvary.recorder.R
import com.nexvary.recorder.delivery.DeliveryUi
import com.nexvary.recorder.family.ProtectedFamilyPage

class SubscriptionActivity: ProtectedFamilyPage() {
    override val title=R.string.fs_subscriptions
    private lateinit var manager: BillingManager
    private lateinit var status: TextView
    private lateinit var offers: LinearLayout
    override fun build() {
        DeliveryUi.text(root,if(com.nexvary.recorder.BuildConfig.DEBUG) R.string.fs_billing_preview else R.string.fs_billing_note)
        status=DeliveryUi.text(root,R.string.fs_billing_loading)
        offers=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;root.addView(this) }
        manager=BillingManager(this) { runOnUiThread { render() } }
        DeliveryUi.button(root,R.string.fs_restore) { manager.restore() }
        manager.connect()
    }
    private fun render() {
        if(isFinishing || isDestroyed) return
        status.setText(if(manager.ready) R.string.fs_billing_verifier_missing else R.string.fs_billing_unavailable)
        offers.removeAllViews()
        manager.products.forEach { product ->
            product.subscriptionOfferDetails?.forEach { offer ->
                offers.addView(TextView(this).apply { text=product.name+"\n"+offer.pricingPhases.pricingPhaseList.joinToString("\n") { it.formattedPrice+" · "+it.billingPeriod } })
            }
            if(manager.canPurchase) DeliveryUi.button(offers,R.string.fs_subscribe) { manager.launch(this,product) }
        }
    }
    override fun onDestroy() { if(::manager.isInitialized) manager.close();super.onDestroy() }
}
