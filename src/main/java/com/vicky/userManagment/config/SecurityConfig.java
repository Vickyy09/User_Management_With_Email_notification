package com.vicky.userManagment.config;

import com.vicky.userManagment.repo.UserRepo;
import com.vicky.userManagment.security.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.time.Clock;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(UserRepo users) {
        return email -> {
            var user = users.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            if (user.getPasswordHash() == null || user.getRole() == null) {
                throw new UsernameNotFoundException("Invalid credentials");
            }
            return User.withUsername(user.getEmail()).password(user.getPasswordHash())
                    .roles(user.getRole().name()).disabled(!user.isActive()).build();
        };
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, TokenService tokens, SecurityErrors errors) throws Exception {
        return http
                // Credentials are accepted only as an explicit bearer header, never as browser cookies.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/openapi.json").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, cause) -> errors.write(req, res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, cause) -> errors.write(req, res, 403, "Access denied")))
                .addFilterBefore(new BearerTokenFilter(tokens, errors), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}