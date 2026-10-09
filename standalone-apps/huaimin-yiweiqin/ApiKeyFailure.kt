package com.cleo.cleos.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class KeyFailureKind {
    QUOTA_EXHAUSTED, RATE_LIMITED, INVALID_CREDENTIAL, MODEL_UNAVAILABLE,
    TRANSIENT_PROVIDER_FAILURE, NETWORK_FAILURE, CANCELLED, UNKNOWN
}
data class KeyFailure(val kind: KeyFailureKind, val code: String? = null)

/** Structured provider code always wins. 429 alone is NEVER an empty balance. */
object KeyFailureClassifier {
    private val json = Json { ignoreUnknownKeys = true }
    private val quota = setOf("insufficient_quota", "billing_hard_limit_reached",
        "insufficient_balance", "credit_balance_exhausted", "balance_insufficient")
    private val limited = setOf("rate_limit_exceeded", "too_many_requests",
        "requests_per_minute", "tokens_per_minute", "rate_limit")
    fun classify(status: Int?, body: String?, code: String? = null, type: String? = null): KeyFailure {
        val root = runCatching { json.parseToJsonElement(body.orEmpty()) as? JsonObject }.getOrNull()
        val obj = (root?.get("error") as? JsonObject) ?: root
        fun field(k: String): String? = runCatching {
            obj?.get(k)?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
        }.getOrNull()
        val c = (code ?: field("code") ?: field("error_code")).orEmpty().lowercase()
        val t = (type ?: field("type")).orEmpty().lowercase()
        val kind = when {
            c in quota || t == "insufficient_quota" -> KeyFailureKind.QUOTA_EXHAUSTED
            c in limited || t == "rate_limit_error" -> KeyFailureKind.RATE_LIMITED
            c in setOf("invalid_api_key", "invalid_authentication", "invalid_token", "token_expired") ->
                KeyFailureKind.INVALID_CREDENTIAL
            c in setOf("model_not_found", "model_not_available", "model_access_denied") ->
                KeyFailureKind.MODEL_UNAVAILABLE
            status == 429 -> KeyFailureKind.RATE_LIMITED
            status == 401 -> KeyFailureKind.INVALID_CREDENTIAL
            status == 403 || status == 404 -> KeyFailureKind.MODEL_UNAVAILABLE
            status != null && status in 500..599 -> KeyFailureKind.TRANSIENT_PROVIDER_FAILURE
            else -> KeyFailureKind.UNKNOWN
        }
        return KeyFailure(kind, c.takeIf { it.isNotBlank() })
    }
}
