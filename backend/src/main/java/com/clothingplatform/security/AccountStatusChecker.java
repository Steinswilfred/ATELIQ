package com.clothingplatform.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

/**
 * Runs inside Spring Security's authentication, AFTER the password is verified (so account states are not
 * disclosed to someone who does not know the password). Also used by the JWT filter on every request.
 */
public class AccountStatusChecker implements UserDetailsChecker {
    @Override
    public void check(UserDetails details) {
        AppUserDetails u = (AppUserDetails) details;
        switch (u.getStatus()) {
            case ACTIVE, APPROVED -> { }
            case PENDING_APPROVAL -> throw new AccountRestrictedException("TAILOR_PENDING_APPROVAL",
                    "Your tailor registration has been successfully submitted. Your account is currently waiting for administrator approval. You will receive an email and notification once your application has been reviewed.");
            case REJECTED -> throw new AccountRestrictedException("TAILOR_REJECTED",
                    "Your tailor application has not been approved. Please check the reason provided or contact support.");
            case BLOCKED -> throw new AccountRestrictedException("ACCOUNT_BLOCKED",
                    "Your account has been blocked. Please contact support.");
            case SUSPENDED -> throw new AccountRestrictedException("ACCOUNT_SUSPENDED",
                    "Your account is temporarily suspended. Please contact support.");
            case PENDING_EMAIL_VERIFICATION -> throw new AccountRestrictedException("EMAIL_NOT_VERIFIED",
                    "Please verify your email address to continue.");
            case PENDING_PHONE_VERIFICATION -> throw new AccountRestrictedException("PHONE_NOT_VERIFIED",
                    "Please verify your phone number to continue.");
            case DEACTIVATED -> throw new AccountRestrictedException("ACCOUNT_DEACTIVATED",
                    "This account has been deactivated. Please contact support.");
        }
    }
}
