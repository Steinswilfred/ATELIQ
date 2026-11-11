package com.clothingplatform.security;

import com.clothingplatform.user.User;
import com.clothingplatform.user.UserRepository;
import com.clothingplatform.user.UserType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public AppUserDetailsService(UserRepository users, JdbcTemplate jdbc) { this.users = users; this.jdbc = jdbc; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).map(this::build)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }

    public Optional<AppUserDetails> loadById(long id) {
        return users.findById(id).filter(u -> u.getDeletedAt() == null).map(this::build);
    }

    private AppUserDetails build(User u) {
        Set<String> perms = new HashSet<>(effectivePermissions(u));
        Set<GrantedAuthority> auth = new HashSet<>();
        perms.forEach(p -> auth.add(new SimpleGrantedAuthority(p)));
        auth.add(new SimpleGrantedAuthority("ROLE_" + u.getUserType().name()));
        return new AppUserDetails(u.getId(), u.getEmail(), u.getPasswordHash(), u.getFullName(),
                u.getUserType(), u.getAccountStatus(), auth, perms);
    }

    /** Role permissions (enabled) + per-user grants - per-user revocations. Super Admin always holds everything. */
    private List<String> effectivePermissions(User u) {
        if (u.getUserType() == UserType.SUPER_ADMIN) return jdbc.queryForList("SELECT code FROM permissions", String.class);
        return jdbc.queryForList("""
            SELECT p.code FROM permissions p
            WHERE (EXISTS (SELECT 1 FROM user_roles ur JOIN role_permissions rp ON rp.role_id = ur.role_id AND rp.enabled
                           WHERE ur.user_id = ? AND rp.permission_id = p.id)
                   AND NOT EXISTS (SELECT 1 FROM user_permission_overrides o WHERE o.user_id = ? AND o.permission_id = p.id AND NOT o.granted))
               OR EXISTS (SELECT 1 FROM user_permission_overrides o WHERE o.user_id = ? AND o.permission_id = p.id AND o.granted)
            """, String.class, u.getId(), u.getId(), u.getId());
    }
}
