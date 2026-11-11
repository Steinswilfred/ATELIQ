package com.clothingplatform.security;

import com.clothingplatform.user.AccountStatus;
import com.clothingplatform.user.UserType;
import java.util.Collection;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AppUserDetails implements UserDetails {
    private final long id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final UserType userType;
    private final AccountStatus status;
    private final Set<GrantedAuthority> authorities;
    private final Set<String> permissions;

    public AppUserDetails(long id, String email, String passwordHash, String fullName, UserType userType,
                          AccountStatus status, Set<GrantedAuthority> authorities, Set<String> permissions) {
        this.id = id; this.email = email; this.passwordHash = passwordHash; this.fullName = fullName;
        this.userType = userType; this.status = status; this.authorities = authorities; this.permissions = permissions;
    }

    public long getId() { return id; }
    public String getFullName() { return fullName; }
    public UserType getUserType() { return userType; }
    public AccountStatus getStatus() { return status; }
    public Set<String> getPermissions() { return permissions; }
    public boolean isSuperAdmin() { return userType == UserType.SUPER_ADMIN; }
    public boolean hasPermission(String code) { return permissions.contains(code); }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    // Account state is enforced by AccountStatusChecker so that users get specific messages after a correct password.
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
