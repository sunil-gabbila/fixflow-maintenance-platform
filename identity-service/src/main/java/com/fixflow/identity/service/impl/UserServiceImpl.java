package com.fixflow.identity.service.impl;

import com.fixflow.identity.dto.request.RegisterRequest;
import com.fixflow.identity.dto.request.UpdateUserRequest;
import com.fixflow.identity.dto.response.UserResponse;
import com.fixflow.identity.entity.Role;
import com.fixflow.identity.entity.User;
import com.fixflow.identity.enums.RoleName;
import com.fixflow.identity.exception.DuplicateResourceException;
import com.fixflow.identity.exception.ResourceNotFoundException;
import com.fixflow.identity.repository.RoleRepository;
import com.fixflow.identity.repository.UserRepository;
import com.fixflow.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        log.info("Fetching user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });

        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all users");

        List<User> users = userRepository.findAll();
        log.info("Found {} users", users.size());

        return users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        log.info("Updating user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailAndIdNot(normalizedEmail, id)) {
            log.warn("Update failed. Email already in use by another user: {}", normalizedEmail);
            throw new DuplicateResourceException("Email '" + normalizedEmail + "' is already in use by another account");
        }

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(normalizedEmail);
        user.setPhoneNumber(request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                ? request.getPhoneNumber().trim() : null);

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully with ID: {}", updatedUser.getId());

        return UserResponse.fromEntity(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        log.info("Deleting user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });

        userRepository.delete(user);
        log.info("User deleted successfully with ID: {}", id);
    }
}
