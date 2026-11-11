package com.clothingplatform.user;

public enum AccountStatus {
    PENDING_EMAIL_VERIFICATION, PENDING_PHONE_VERIFICATION, PENDING_APPROVAL,
    ACTIVE, APPROVED, REJECTED, BLOCKED, SUSPENDED, DEACTIVATED;

    /** Only these states may hold a session. Everything else is refused by the security layer. */
    public boolean canAuthenticate() { return this == ACTIVE || this == APPROVED; }
}
