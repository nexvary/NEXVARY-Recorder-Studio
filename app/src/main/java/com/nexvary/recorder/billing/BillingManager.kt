package com.nexvary.recorder.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.nexvary.recorder.family.FamilyStore
import org.json.JSONObject
import java.util.concurrent.Executors

/** Genuine Play client. Purchasing stays unavailable until a real verification backend is supplied. */
class BillingManager(private val context: Context,private val verifier: PurchaseVerifier?=null,private val changed: () -> Unit): PurchasesUpdatedListener {
    private val executor=Executors.newSingleThreadExecutor()
    val products=mutableListOf<ProductDetails>()
    var ready=false;private set
    var resultCode=BillingClient.BillingResponseCode.SERVICE_DISCONNECTED;private set
    val canPurchase get()=ready && verifier!=null
    private val client=BillingClient.newBuilder(context).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection().build()
    fun connect() {
        client.startConnection(object: BillingClientStateListener {
            override fun onBillingServiceDisconnected() { ready=false;changed() }
            override fun onBillingSetupFinished(result: BillingResult) {
                resultCode=result.responseCode;ready=result.responseCode==BillingClient.BillingResponseCode.OK
                if(ready) { queryProducts();restore() };changed()
            }
        })
    }
    private fun queryProducts() {
        val list=listOf(EntitlementManager.MONTHLY,EntitlementManager.YEARLY).map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build() }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build()) { result,details ->
            resultCode=result.responseCode;products.clear();products.addAll(details.productDetailsList);changed()
        }
    }
    fun restore() {
        if(!ready) return
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result,purchases ->
            resultCode=result.responseCode
            if(result.responseCode==BillingClient.BillingResponseCode.OK) process(purchases)
            changed()
        }
    }
    fun launch(activity: Activity,product: ProductDetails): Boolean {
        if(!canPurchase) return false
        val offer=product.subscriptionOfferDetails?.firstOrNull() ?: return false
        val accountId=runCatching { com.nexvary.recorder.guardian.CloudApi(context).accountId() }.getOrNull() ?: return false
        return client.launchBillingFlow(activity,BillingFlowParams.newBuilder().setObfuscatedAccountId(accountId).setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.offerToken).build())).build()).responseCode==BillingClient.BillingResponseCode.OK
    }
    override fun onPurchasesUpdated(result: BillingResult,purchases: MutableList<Purchase>?) {
        resultCode=result.responseCode
        if(result.responseCode==BillingClient.BillingResponseCode.OK && purchases!=null) process(purchases)
        changed()
    }
    private fun process(purchases: List<Purchase>) {
        runCatching { executor.execute { runCatching {
            purchases.forEach { purchase ->
                // Tokens are encrypted and are never included in callbacks or logs.
                val account=runCatching { com.nexvary.recorder.guardian.CloudApi(context).accountId() }.getOrNull() ?: return@forEach
                val ownsSession=FamilyStore(context).update { root ->
                    if(runCatching { com.nexvary.recorder.guardian.CloudSessionState.binding(com.nexvary.recorder.guardian.CloudApi.config(root).optString("idToken")) }.getOrNull()!=account) return@update false
                    root.put("playPurchase",JSONObject().put("token",purchase.purchaseToken).put("state",purchase.purchaseState).put("accountBinding",account));true
                }
                if(!ownsSession) return@forEach
                if(purchase.purchaseState==Purchase.PurchaseState.PENDING) return@forEach
                val validator=verifier ?: return@forEach
                if(purchase.purchaseState!=Purchase.PurchaseState.PURCHASED) return@forEach
                val verified=runCatching { validator.verify(purchase.products,purchase.purchaseToken) }.getOrNull() ?: return@forEach
                val committed=FamilyStore(context).update { root ->
                    val current=runCatching { com.nexvary.recorder.guardian.CloudSessionState.binding(com.nexvary.recorder.guardian.CloudApi.config(root).optString("idToken")) }.getOrNull()
                    if(current!=account) return@update false
                    root.put("verifiedEntitlement",JSONObject().put("state",verified.state.name).put("expiresAt",verified.expiresAt).put("verifiedAt",verified.verifiedAt).put("accountBinding",account));true
                }
                if(committed && EntitlementPolicy.premium(verified,System.currentTimeMillis()) && !purchase.isAcknowledged)
                    client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { changed() }
            }
            changed()
        } } }
    }
    fun close() { client.endConnection();executor.shutdown() }
}
