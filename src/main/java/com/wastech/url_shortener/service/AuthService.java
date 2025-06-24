package com.wastech.url_shortener.service;

import com.wastech.url_shortener.model.Role;
import com.wastech.url_shortener.model.User;
import com.wastech.url_shortener.payload.LoginRequest;
import com.wastech.url_shortener.payload.RegistrationRequest;
import com.wastech.url_shortener.repository.UserRepository;
import com.wastech.url_shortener.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public String authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        return tokenProvider.generateToken(authentication);
    }

    @Transactional
    public void registerUser(RegistrationRequest registrationRequest) {
        if (userRepository.findByUsername(registrationRequest.getUsername()).isPresent()) {
            throw new RuntimeException("Username is already taken!");
        }

        // Default to USER role if no roles are specified or invalid roles
        Set<Role> roles = new HashSet<>();
        if (registrationRequest.getRoles() != null && !registrationRequest.getRoles().isEmpty()) {
            roles = registrationRequest.getRoles().stream()
                .map(r -> {
                    try {
                        return Role.valueOf(r.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid role provided during registration: {}. Defaulting to ROLE_USER.", r);
                        return Role.ROLE_USER;
                    }
                })
                .collect(Collectors.toSet());
        }
        if (roles.isEmpty()) {
            roles.add(Role.ROLE_USER);
        }

        User user = new User();
        user.setUsername(registrationRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registrationRequest.getPassword()));
        user.setRoles(roles);
        user.setPaid(false);

        userRepository.save(user);
    }
}