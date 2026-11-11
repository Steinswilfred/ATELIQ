package com.clothingplatform.security;

import org.springframework.security.authentication.AccountStatusException;

public class AccountRestrictedException extends AccountStatusException {
    private final String code;
    public AccountRestrictedException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
