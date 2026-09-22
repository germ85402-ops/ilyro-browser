package com.ilyro.browser.passwords

import org.junit.Assert.assertEquals
import org.junit.Test

class AutofillAuthorizationPolicyTest {
    @Test
    fun devicesWithoutAScreenLockKeepDirectAutofill() {
        assertEquals(
            AutofillAuthorizationDecision.DIRECT_FILL,
            autofillAuthorizationDecision(deviceIsSecure = false, confirmationIntentAvailable = false)
        )
    }

    @Test
    fun securedDevicesRequireAConfirmationIntent() {
        assertEquals(
            AutofillAuthorizationDecision.REQUIRE_DEVICE_CONFIRMATION,
            autofillAuthorizationDecision(deviceIsSecure = true, confirmationIntentAvailable = true)
        )
        assertEquals(
            AutofillAuthorizationDecision.SUPPRESS_CREDENTIAL,
            autofillAuthorizationDecision(deviceIsSecure = true, confirmationIntentAvailable = false)
        )
    }
}
