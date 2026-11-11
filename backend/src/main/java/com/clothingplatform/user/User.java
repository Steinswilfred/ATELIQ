package com.clothingplatform.user;

import com.clothingplatform.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false) private String email;
    private String phone;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Enumerated(EnumType.STRING) @Column(name = "user_type", nullable = false) private UserType userType;
    @Enumerated(EnumType.STRING) @Column(name = "account_status", nullable = false) private AccountStatus accountStatus;
    @Column(name = "status_reason") private String statusReason;
    @Column(name = "email_verified", nullable = false) private boolean emailVerified;
    @Column(name = "phone_verified", nullable = false) private boolean phoneVerified;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(name = "failed_login_count", nullable = false) private int failedLoginCount;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "deleted_at") private Instant deletedAt;

    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { passwordHash = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { fullName = v; }
    public UserType getUserType() { return userType; }
    public void setUserType(UserType v) { userType = v; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatus v) { accountStatus = v; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String v) { statusReason = v; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean v) { emailVerified = v; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(boolean v) { phoneVerified = v; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { createdBy = v; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant v) { lastLoginAt = v; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public void setFailedLoginCount(int v) { failedLoginCount = v; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant v) { lockedUntil = v; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { deletedAt = v; }
}
