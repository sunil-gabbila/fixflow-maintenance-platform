package com.fixflow.identity;

import com.fixflow.identity.dto.request.RegisterRequest;
import com.fixflow.identity.dto.response.UserResponse;
import com.fixflow.identity.entity.User;
import com.fixflow.identity.enums.RoleName;
import com.fixflow.identity.exception.DuplicateResourceException;
import com.fixflow.identity.repository.RoleRepository;
import com.fixflow.identity.repository.UserRepository;
import com.fixflow.identity.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestExecutionListeners({
        DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class,
        TransactionalTestExecutionListener.class
})
class UserRegistrationIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully register a new user with CUSTOMER role and hashed password")
    void testRegisterUser_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .password("Test@123")
                .phoneNumber("9876543210")
                .build();

        UserResponse response = userService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("Test");
        assertThat(response.getLastName()).isEqualTo("User");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getPhoneNumber()).isEqualTo("9876543210");
        assertThat(response.getRoles()).contains("CUSTOMER");
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();

        // Verify entity in DB
        Optional<User> userOptional = userRepository.findByEmail("test@example.com");
        assertThat(userOptional).isPresent();
        User savedUser = userOptional.get();
        assertThat(savedUser.getPassword()).isNotEqualTo("Test@123");
        assertThat(passwordEncoder.matches("Test@123", savedUser.getPassword())).isTrue();
        assertThat(savedUser.getRoles()).anyMatch(role -> role.getName() == RoleName.CUSTOMER);
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when registering with duplicate email")
    void testRegisterUser_DuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Test")
                .lastName("User")
                .email("duplicate@example.com")
                .password("Test@123")
                .phoneNumber("9876543210")
                .build();

        userService.register(request);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered");
    }
}
