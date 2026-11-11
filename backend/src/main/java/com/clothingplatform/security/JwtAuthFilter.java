package com.clothingplatform.security;

import com.clothingplatform.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Validates the bearer token, then re-checks the account in the DB so a block takes effect immediately. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AppUserDetailsService users;
    private final AccountStatusChecker checker = new AccountStatusChecker();

    public JwtAuthFilter(JwtService jwt, AppUserDetailsService users) { this.jwt = jwt; this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            Optional<Long> id = jwt.parseUserId(header.substring(7));
            if (id.isPresent()) {
                Optional<AppUserDetails> user = users.loadById(id.get());
                if (user.isPresent()) {
                    try {
                        checker.check(user.get());
                        var token = new UsernamePasswordAuthenticationToken(user.get(), null, user.get().getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(token);
                    } catch (AuthenticationException ignored) {
                        // blocked/suspended/etc.: leave unauthenticated, entry point returns 401
                    }
                }
            }
        }
        chain.doFilter(req, res);
    }
}
