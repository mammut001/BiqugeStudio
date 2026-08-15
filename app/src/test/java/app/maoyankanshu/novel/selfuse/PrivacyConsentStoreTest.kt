package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyConsentStoreTest {
    @Test
    fun olderOrMissingConsentDoesNotAcceptCurrentPolicy() {
        assertFalse(PrivacyConsentStore.isAcceptedVersion(0, 1))
        assertFalse(PrivacyConsentStore.isAcceptedVersion(1, 2))
    }

    @Test
    fun currentOrNewerStoredConsentAcceptsCurrentPolicy() {
        assertTrue(PrivacyConsentStore.isAcceptedVersion(1, 1))
        assertTrue(PrivacyConsentStore.isAcceptedVersion(2, 1))
    }

    @Test
    fun invalidCurrentPolicyVersionNeverCountsAsAccepted() {
        assertFalse(PrivacyConsentStore.isAcceptedVersion(1, 0))
    }
}
