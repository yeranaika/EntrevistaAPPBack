package security.billing

import data.models.billing.VerifyPurchaseReq
import data.repository.billing.SuscripcionRepository
import data.repository.usuarios.UserRepository
import io.ktor.client.*
import java.util.UUID

data class GooglePlayVerificationResult(
    val isValid: Boolean,
    val isActive: Boolean,
    val expiryTimeMillis: Long?
)

data class BillingResult(
    val success: Boolean,
    val errorMessage: String? = null
)

class GooglePlayBillingService(
    private val httpClient: HttpClient,
    private val userRepo: UserRepository,
    private val suscripcionRepo: SuscripcionRepository,
) {

    // 👇 CONSTRUCTOR SECUNDARIO SIN httpClient (para usarlo como lo llamas en Routing.kt)
    constructor(
        userRepo: UserRepository,
        suscripcionRepo: SuscripcionRepository
    ) : this(
        HttpClient(),  // cliente simple por ahora
        userRepo,
        suscripcionRepo
    )

    suspend fun verifyAndStore(req: VerifyPurchaseReq, usuarioId: UUID): BillingResult {
        val googleRes = verifyWithGoogle(req.productId, req.purchaseToken)

        if (!googleRes.isValid || !googleRes.isActive) {
            return BillingResult(success = false, errorMessage = "Compra no válida o no activa")
        }

        suscripcionRepo.upsertSubscription(
            usuarioId = usuarioId,
            plan = req.productId,
            proveedor = "google_play",
            estado = "activa",
            fechaExpiracionMillis = googleRes.expiryTimeMillis
        )

        // userRepo.setPremium(usuarioId, true, googleRes.expiryTimeMillis) // opcional

        return BillingResult(success = true)
    }

    private suspend fun verifyWithGoogle(
        productId: String,
        purchaseToken: String
    ): GooglePlayVerificationResult {
        // TODO: llamada real a Google Play
        return GooglePlayVerificationResult(
            isValid = true,
            isActive = true,
            expiryTimeMillis = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )
    }
}
