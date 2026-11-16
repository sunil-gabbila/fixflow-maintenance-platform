package com.fixflow.identity.service.impl;

import com.fixflow.identity.dto.request.RegisterRequest;
import com.fixflow.identity.dto.response.UserResponse;
import com.fixflow.identity.entity.Role;
import com.fixflow.identity.entity.User;
import com.fixflow.identity.enums.RoleName;
import com.fixflow.identity.exception.DuplicateResourceException;
import com.fixflow.identity.repository.RoleRepository;
import com.fixflow.identity.repository.UserRepository;
import com.fixflow.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        log.info("Attempting to register user with email: {}", normalizedEmail);

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration failed. Email already exists: {}", normalizedEmail);
            throw new DuplicateResourceException("Email '" + normalizedEmail + "' is already registered");
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseGet(() -> {
                    log.info("Role CUSTOMER not found, initializing role in database");
                    return roleRepository.save(Role.builder().name(RoleName.CUSTOMER).build());
                });

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank() ? request.getPhoneNumber().trim() : null)
                .roles(new HashSet<>())
                .build();

        user.addRole(customerRole);

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        return UserResponse.fromEntity(savedUser);
    }
}
