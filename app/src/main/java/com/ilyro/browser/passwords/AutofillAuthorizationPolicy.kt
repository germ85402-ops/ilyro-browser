package com.ilyro.browser.passwords

internal enum class AutofillAuthorizationDecision {
    DIRECT_FILL,
    REQUIRE_DEVICE_CONFIRMATION,
    SUPPRESS_CREDENTIAL
}

internal fun autofillAuthorizationDecision(
    deviceIsSecure: Boolean,
    confirmationIntentAvailable: Boolean
): AutofillAuthorizationDecision = when {
    !deviceIsSecure -> AutofillAuthorizationDecision.DIRECT_FILL
    confirmationIntentAvailable -> AutofillAuthorizationDecision.REQUIRE_DEVICE_CONFIRMATION
    else -> AutofillAuthorizationDecision.SUPPRESS_CREDENTIAL
}
