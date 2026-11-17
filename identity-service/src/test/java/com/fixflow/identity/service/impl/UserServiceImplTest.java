package com.fixflow.identity.service.impl;

import com.fixflow.identity.dto.request.RegisterRequest;
import com.fixflow.identity.dto.request.UpdateUserRequest;
import com.fixflow.identity.dto.response.PageResponse;
import com.fixflow.identity.dto.response.UserResponse;
import com.fixflow.identity.entity.Role;
import com.fixflow.identity.entity.User;
import com.fixflow.identity.enums.RoleName;
import com.fixflow.identity.exception.DuplicateResourceException;
import com.fixflow.identity.exception.ResourceNotFoundException;
import com.fixflow.identity.repository.RoleRepository;
import com.fixflow.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        customerRole = Role.builder()
                .id(1L)
                .name(RoleName.CUSTOMER)
                .build();

        testUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("hashedPassword123")
                .phoneNumber("9876543210")
                .roles(new HashSet<>(Set.of(customerRole)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ========== REGISTER ==========

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("Should register user successfully with hashed password and CUSTOMER role")
        void register_Success() {
            RegisterRequest request = RegisterRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("john@example.com")
                    .password("Test@123")
                    .phoneNumber("9876543210")
                    .build();

            when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
            when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(customerRole));
            when(passwordEncoder.encode("Test@123")).thenReturn("hashedPassword123");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UserResponse response = userService.register(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getFirstName()).isEqualTo("John");
            assertThat(response.getLastName()).isEqualTo("Doe");
            assertThat(response.getEmail()).isEqualTo("john@example.com");
            assertThat(response.getPhoneNumber()).isEqualTo("9876543210");
            assertThat(response.getRoles()).contains("CUSTOMER");

            verify(userRepository).existsByEmail("john@example.com");
            verify(passwordEncoder).encode("Test@123");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when email already exists")
        void register_DuplicateEmail() {
            RegisterRequest request = RegisterRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("john@example.com")
                    .password("Test@123")
                    .build();

            when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already registered");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should create CUSTOMER role if it does not exist")
        void register_CreatesRoleIfMissing() {
            RegisterRequest request = RegisterRequest.builder()
                    .firstName("Jane")
                    .lastName("Doe")
                    .email("jane@example.com")
                    .password("Test@123")
                    .build();

            when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
            when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.empty());
            when(roleRepository.save(any(Role.class))).thenReturn(customerRole);
            when(passwordEncoder.encode("Test@123")).thenReturn("hashedPassword123");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            userService.register(request);

            verify(roleRepository).save(any(Role.class));
        }

        @Test
        @DisplayName("Should normalize email to lowercase and trim whitespace")
        void register_NormalizesEmail() {
            RegisterRequest request = RegisterRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("  JOHN@Example.COM  ")
                    .password("Test@123")
                    .build();

            when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
            when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(customerRole));
            when(passwordEncoder.encode("Test@123")).thenReturn("hashedPassword123");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            userService.register(request);

            verify(userRepository).existsByEmail("john@example.com");
        }
    }

    // ========== GET USER BY ID ==========

    @Nested
    @DisplayName("getUserById()")
    class GetUserByIdTests {

        @Test
        @DisplayName("Should return user when found by ID")
        void getUserById_Success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            UserResponse response = userService.getUserById(1L);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getFirstName()).isEqualTo("John");
            assertThat(response.getEmail()).isEqualTo("john@example.com");
            assertThat(response.getRoles()).contains("CUSTOMER");

            verify(userRepository).findById(1L);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void getUserById_NotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining("99");

            verify(userRepository).findById(99L);
        }
    }

    // ========== GET ALL USERS ==========

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsersTests {

        @Test
        @DisplayName("Should return list of all users")
        void getAllUsers_Success() {
            User secondUser = User.builder()
                    .id(2L)
                    .firstName("Jane")
                    .lastName("Smith")
                    .email("jane@example.com")
                    .password("hashedPassword456")
                    .roles(new HashSet<>(Set.of(customerRole)))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(userRepository.findAll()).thenReturn(List.of(testUser, secondUser));

            List<UserResponse> result = userService.getAllUsers();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getEmail()).isEqualTo("john@example.com");
            assertThat(result.get(1).getEmail()).isEqualTo("jane@example.com");

            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("Should return empty list when no users exist")
        void getAllUsers_Empty() {
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            List<UserResponse> result = userService.getAllUsers();

            assertThat(result).isEmpty();

            verify(userRepository).findAll();
        }
    }

    // ========== UPDATE USER ==========

    @Nested
    @DisplayName("updateUser()")
    class UpdateUserTests {

        @Test
        @DisplayName("Should update user successfully")
        void updateUser_Success() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("Jonathan")
                    .lastName("Doe-Updated")
                    .email("jonathan@example.com")
                    .phoneNumber("1234567890")
                    .build();

            User updatedUser = User.builder()
                    .id(1L)
                    .firstName("Jonathan")
                    .lastName("Doe-Updated")
                    .email("jonathan@example.com")
                    .password("hashedPassword123")
                    .phoneNumber("1234567890")
                    .roles(new HashSet<>(Set.of(customerRole)))
                    .createdAt(testUser.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmailAndIdNot("jonathan@example.com", 1L)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            UserResponse response = userService.updateUser(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getFirstName()).isEqualTo("Jonathan");
            assertThat(response.getLastName()).isEqualTo("Doe-Updated");
            assertThat(response.getEmail()).isEqualTo("jonathan@example.com");
            assertThat(response.getPhoneNumber()).isEqualTo("1234567890");

            verify(userRepository).findById(1L);
            verify(userRepository).existsByEmailAndIdNot("jonathan@example.com", 1L);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void updateUser_NotFound() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("Jonathan")
                    .lastName("Doe")
                    .email("jonathan@example.com")
                    .build();

            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUser(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining("99");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when email belongs to another user")
        void updateUser_DuplicateEmail() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("taken@example.com")
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmailAndIdNot("taken@example.com", 1L)).thenReturn(true);

            assertThatThrownBy(() -> userService.updateUser(1L, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already in use");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should allow updating user with their own existing email")
        void updateUser_SameEmail() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("Jonathan")
                    .lastName("Doe")
                    .email("john@example.com")
                    .build();

            User updatedUser = User.builder()
                    .id(1L)
                    .firstName("Jonathan")
                    .lastName("Doe")
                    .email("john@example.com")
                    .password("hashedPassword123")
                    .roles(new HashSet<>(Set.of(customerRole)))
                    .createdAt(testUser.getCreatedAt())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmailAndIdNot("john@example.com", 1L)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            UserResponse response = userService.updateUser(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getFirstName()).isEqualTo("Jonathan");

            verify(userRepository).save(any(User.class));
        }
    }

    // ========== DELETE USER ==========

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUserTests {

        @Test
        @DisplayName("Should delete user successfully")
        void deleteUser_Success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            userService.deleteUser(1L);

            verify(userRepository).findById(1L);
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user not found")
        void deleteUser_NotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining("99");

            verify(userRepository, never()).delete(any(User.class));
        }
    }

    // ========== GET USERS (PAGINATED) ==========

    @Nested
    @DisplayName("getUsers() - Pagination")
    class GetUsersPaginatedTests {

        private User secondUser;

        @BeforeEach
        void setUpPagination() {
            secondUser = User.builder()
                    .id(2L)
                    .firstName("Jane")
                    .lastName("Smith")
                    .email("jane@example.com")
                    .password("hashedPassword456")
                    .roles(new HashSet<>(Set.of(customerRole)))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("Should return paginated users with default page=0, size=10")
        void getUsers_DefaultPagination() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(List.of(testUser, secondUser), pageable, 2);

            when(userRepository.findAll(pageable)).thenReturn(page);

            PageResponse<UserResponse> result = userService.getUsers(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getPage()).isEqualTo(0);
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.isLast()).isTrue();

            verify(userRepository).findAll(pageable);
        }

        @Test
        @DisplayName("Should return paginated users with custom page and size")
        void getUsers_CustomPageAndSize() {
            Pageable pageable = PageRequest.of(1, 1);
            Page<User> page = new PageImpl<>(List.of(secondUser), pageable, 2);

            when(userRepository.findAll(pageable)).thenReturn(page);

            PageResponse<UserResponse> result = userService.getUsers(1, 1);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("jane@example.com");
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getSize()).isEqualTo(1);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
            assertThat(result.isLast()).isTrue();

            verify(userRepository).findAll(pageable);
        }

        @Test
        @DisplayName("Should return empty content when no users exist")
        void getUsers_EmptyResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(userRepository.findAll(pageable)).thenReturn(page);

            PageResponse<UserResponse> result = userService.getUsers(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getPage()).isEqualTo(0);
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getTotalPages()).isEqualTo(0);
            assertThat(result.isLast()).isTrue();

            verify(userRepository).findAll(pageable);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when page is negative")
        void getUsers_NegativePage() {
            assertThatThrownBy(() -> userService.getUsers(-1, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Page index must be non-negative");

            verify(userRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when size is zero")
        void getUsers_SizeZero() {
            assertThatThrownBy(() -> userService.getUsers(0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Size must be between 1 and 100");

            verify(userRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when size is negative")
        void getUsers_NegativeSize() {
            assertThatThrownBy(() -> userService.getUsers(0, -5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Size must be between 1 and 100");

            verify(userRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when size exceeds 100")
        void getUsers_SizeExceeds100() {
            assertThatThrownBy(() -> userService.getUsers(0, 101))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Size must be between 1 and 100");

            verify(userRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("Should accept size=1 (minimum boundary)")
        void getUsers_SizeMinBoundary() {
            Pageable pageable = PageRequest.of(0, 1);
            Page<User> page = new PageImpl<>(List.of(testUser), pageable, 2);

            when(userRepository.findAll(pageable)).thenReturn(page);

            PageResponse<UserResponse> result = userService.getUsers(0, 1);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getSize()).isEqualTo(1);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
            assertThat(result.isLast()).isFalse();
        }

        @Test
        @DisplayName("Should accept size=100 (maximum boundary)")
        void getUsers_SizeMaxBoundary() {
            Pageable pageable = PageRequest.of(0, 100);
            Page<User> page = new PageImpl<>(List.of(testUser, secondUser), pageable, 2);

            when(userRepository.findAll(pageable)).thenReturn(page);

            PageResponse<UserResponse> result = userService.getUsers(0, 100);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getSize()).isEqualTo(100);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("Should correctly calculate totalPages and report last flag")
        void getUsers_CorrectTotalPagesAndLastFlag() {
            // 15 total users, page size 5 → 3 pages; requesting page 0 → not last
            Pageable pageable = PageRequest.of(0, 5);
            Page<User> page = new PageImpl<>(
                    List.of(testUser, secondUser, testUser, secondUser, testUser),
                    pageable,
                    15
            );

            when(userRepository.findAll(pageable)).thenReturn(page);

            PageResponse<UserResponse> result = userService.getUsers(0, 5);

            assertThat(result.getTotalElements()).isEqualTo(15);
            assertThat(result.getTotalPages()).isEqualTo(3);
            assertThat(result.isLast()).isFalse();
        }

        @Test
        @DisplayName("Should never expose password in paginated results")
        void getUsers_NeverExposesPassword() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(List.of(testUser), pageable, 1);

            when(userRepository.findAll(pageable)).thenReturn(page);

            PageResponse<UserResponse> result = userService.getUsers(0, 10);

            assertThat(result.getContent()).hasSize(1);
            UserResponse userResponse = result.getContent().get(0);
            // UserResponse does not have a password field — verify by checking mapped fields only
            assertThat(userResponse.getId()).isEqualTo(testUser.getId());
            assertThat(userResponse.getFirstName()).isEqualTo(testUser.getFirstName());
            assertThat(userResponse.getEmail()).isEqualTo(testUser.getEmail());
        }
    }
}
