// src/main/kotlin/data/models/billing/BillingModels.kt
package data.models.billing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyPurchaseReq(
    @SerialName("product_id")
    val productId: String,

    @SerialName("purchase_token")
    val purchaseToken: String,

    @SerialName("purchase_time")
    val purchaseTime: Long
)

@Serializable
data class BillingStatusRes(
    @SerialName("is_premium")
    val isPremium: Boolean,

    @SerialName("expires_at")
    val expiresAt: Long?
)
