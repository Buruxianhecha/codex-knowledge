package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class ProviderFailoverRulesTest {
    @Test fun quotaCanFailOverBeforeAnyStreaming() {
        assertTrue(ProviderFailoverRules.mayRetry(KeyFailureKind.QUOTA_EXHAUSTED,false))
        assertTrue(ProviderFailoverRules.mayRetry(KeyFailureKind.INVALID_CREDENTIAL,false))
    }
    @Test fun temporary429IsNotAnEmptyBalanceButCanUseDifferentProvider() {
        assertEquals(KeyFailureKind.RATE_LIMITED,KeyFailureClassifier.classify(429,null).kind)
        assertTrue(ProviderFailoverRules.mayRetry(KeyFailureKind.RATE_LIMITED,false))
    }
    @Test fun neverReplayPartialOutputOrToolCalls() {
        for(kind in KeyFailureKind.values())
            assertFalse(ProviderFailoverRules.mayRetry(kind,true))
    }
    @Test fun networkAndUnknownErrorsDoNotAutoChangeProviders() {
        assertFalse(ProviderFailoverRules.mayRetry(KeyFailureKind.NETWORK_FAILURE,false))
        assertFalse(ProviderFailoverRules.mayRetry(KeyFailureKind.UNKNOWN,false))
        assertFalse(ProviderFailoverRules.mayRetry(KeyFailureKind.MODEL_UNAVAILABLE,false))
    }
    @Test fun routingOriginIncludesActualModel() {
        assertNotEquals(
            ProviderFailoverRules.originId("https://api.openai.com/v1","gpt-4o"),
            ProviderFailoverRules.originId("https://api.openai.com/v1","gpt-4o-mini")
        )
        assertEquals(
            ProviderFailoverRules.normalized("https://api.openai.com/v1/"),
            ProviderFailoverRules.normalized("https://api.openai.com/v1")
        )
    }
}
