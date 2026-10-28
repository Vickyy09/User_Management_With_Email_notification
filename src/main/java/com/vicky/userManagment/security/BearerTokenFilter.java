package com.vicky.userManagment.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class BearerTokenFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final SecurityErrors errors;

    public BearerTokenFilter(TokenService tokens, SecurityErrors errors) {
        this.tokens = tokens;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                errors.write(request, response, 401, "A Bearer token is required");
                return;
            }
            var principal = tokens.authenticate(header.substring(7));
            if (principal.isEmpty()) {
                errors.write(request, response, 401, "Invalid or expired token");
                return;
            }
            var user = principal.get();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}