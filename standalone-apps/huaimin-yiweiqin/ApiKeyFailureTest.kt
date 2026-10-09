package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class ApiKeyFailureTest {
    @Test fun generic429CannotBeMistakenForZeroBalance() {
        assertEquals(KeyFailureKind.RATE_LIMITED,
            KeyFailureClassifier.classify(429,"{\"error\":{\"message\":\"quota exceeded\"}}").kind)
    }
    @Test fun structuredBillingErrorCanSwitch() {
        assertEquals(KeyFailureKind.QUOTA_EXHAUSTED,
            KeyFailureClassifier.classify(429,"{\"error\":{\"code\":\"insufficient_quota\"}}").kind)
        assertEquals(KeyFailureKind.QUOTA_EXHAUSTED,
            KeyFailureClassifier.classify(402,"{\"error\":{\"code\":\"insufficient_balance\"}}").kind)
    }
    @Test fun barePaymentErrorIsStillUnknown() {
        assertEquals(KeyFailureKind.UNKNOWN,
            KeyFailureClassifier.classify(402,"{\"message\":\"payment required\"}").kind)
    }
    @Test fun credentialsModelAndServerAreNotZeroBalance() {
        assertEquals(KeyFailureKind.INVALID_CREDENTIAL,KeyFailureClassifier.classify(401,null).kind)
        assertEquals(KeyFailureKind.MODEL_UNAVAILABLE,KeyFailureClassifier.classify(403,null).kind)
        assertEquals(KeyFailureKind.TRANSIENT_PROVIDER_FAILURE,KeyFailureClassifier.classify(503,null).kind)
    }
    @Test fun neverReusesModelScopedKeyForDifferentModel() {
        val all=listOf(KeyCandidate("primary","A","red",0,"EXHAUSTED"),
            KeyCandidate("b","B","green",1,modelScope="alpha"),
            KeyCandidate("c","C","blue",2,enabled=false),
            KeyCandidate("d","D","white",3,modelScope="beta"))
        assertEquals(listOf("green"),KeySelection.compatible(all,"alpha",1000).map {it.value})
    }
    @Test fun rateCooldownIsNotPermanent() {
        val row=KeyCandidate("a","A","secret",0,"RATE_LIMITED",cooldownUntil=12000L)
        assertTrue(KeySelection.compatible(listOf(row),"m",10000L).isEmpty())
        assertEquals(1,KeySelection.compatible(listOf(row),"m",12001L).size)
    }
}
