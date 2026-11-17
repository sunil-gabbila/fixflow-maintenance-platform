package com.fixflow.identity;

import com.fixflow.identity.dto.request.RegisterRequest;
import com.fixflow.identity.repository.UserRepository;
import com.fixflow.identity.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.ServletTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestExecutionListeners({
        ServletTestExecutionListener.class,
        DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class,
        TransactionalTestExecutionListener.class
})
class UserPaginationIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private void registerUsers(int count) {
        for (int i = 1; i <= count; i++) {
            RegisterRequest request = RegisterRequest.builder()
                    .firstName("User" + i)
                    .lastName("Test")
                    .email("user" + i + "@example.com")
                    .password("Test@123")
                    .phoneNumber("900000000" + i)
                    .build();
            userService.register(request);
        }
    }

    // ========== DEFAULT PAGINATION ==========

    @Nested
    @DisplayName("Default Pagination")
    class DefaultPaginationTests {

        @Test
        @DisplayName("GET /api/v1/users - defaults to page=0, size=10")
        void shouldReturnDefaultPagination() throws Exception {
            registerUsers(3);

            mockMvc.perform(get("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Users fetched successfully"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(3))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andExpect(jsonPath("$.data.last").value(true))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // ========== CUSTOM PAGE AND SIZE ==========

    @Nested
    @DisplayName("Custom Page and Size")
    class CustomPageAndSizeTests {

        @Test
        @DisplayName("GET /api/v1/users?page=0&size=2 - first page of 2")
        void shouldReturnFirstPageWithCustomSize() throws Exception {
            registerUsers(5);

            mockMvc.perform(get("/api/v1/users")
                            .param("page", "0")
                            .param("size", "2")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(2))
                    .andExpect(jsonPath("$.data.totalElements").value(5))
                    .andExpect(jsonPath("$.data.totalPages").value(3))
                    .andExpect(jsonPath("$.data.last").value(false));
        }

        @Test
        @DisplayName("GET /api/v1/users?page=2&size=2 - last page with 1 remaining user")
        void shouldReturnLastPageWithRemainingUsers() throws Exception {
            registerUsers(5);

            mockMvc.perform(get("/api/v1/users")
                            .param("page", "2")
                            .param("size", "2")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.page").value(2))
                    .andExpect(jsonPath("$.data.size").value(2))
                    .andExpect(jsonPath("$.data.totalElements").value(5))
                    .andExpect(jsonPath("$.data.totalPages").value(3))
                    .andExpect(jsonPath("$.data.last").value(true));
        }

        @Test
        @DisplayName("GET /api/v1/users?page=1&size=3 - middle page")
        void shouldReturnMiddlePage() throws Exception {
            registerUsers(7);

            mockMvc.perform(get("/api/v1/users")
                            .param("page", "1")
                            .param("size", "3")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(3))
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(3))
                    .andExpect(jsonPath("$.data.totalElements").value(7))
                    .andExpect(jsonPath("$.data.totalPages").value(3))
                    .andExpect(jsonPath("$.data.last").value(false));
        }
    }

    // ========== EMPTY RESULTS ==========

    @Nested
    @DisplayName("Empty Results")
    class EmptyResultsTests {

        @Test
        @DisplayName("GET /api/v1/users - empty database returns empty content")
        void shouldReturnEmptyContentWhenNoUsers() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(0))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.totalPages").value(0))
                    .andExpect(jsonPath("$.data.last").value(true));
        }

        @Test
        @DisplayName("GET /api/v1/users?page=5&size=10 - page beyond data returns empty content")
        void shouldReturnEmptyContentForPageBeyondData() throws Exception {
            registerUsers(3);

            mockMvc.perform(get("/api/v1/users")
                            .param("page", "5")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(0))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.last").value(true));
        }
    }

    // ========== INVALID PAGE AND SIZE ==========

    @Nested
    @DisplayName("Invalid Page and Size")
    class InvalidPageAndSizeTests {

        @Test
        @DisplayName("GET /api/v1/users?page=-1 - negative page returns 400")
        void shouldReturn400ForNegativePage() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .param("page", "-1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Page index must be non-negative"));
        }

        @Test
        @DisplayName("GET /api/v1/users?size=0 - zero size returns 400")
        void shouldReturn400ForZeroSize() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .param("size", "0")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Size must be between 1 and 100"));
        }

        @Test
        @DisplayName("GET /api/v1/users?size=-5 - negative size returns 400")
        void shouldReturn400ForNegativeSize() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .param("size", "-5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Size must be between 1 and 100"));
        }

        @Test
        @DisplayName("GET /api/v1/users?size=101 - size exceeding 100 returns 400")
        void shouldReturn400ForSizeExceeding100() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .param("size", "101")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Size must be between 1 and 100"));
        }
    }

    // ========== CORRECT totalElements AND totalPages ==========

    @Nested
    @DisplayName("Correct totalElements and totalPages")
    class TotalElementsAndPagesTests {

        @Test
        @DisplayName("totalElements matches actual user count")
        void totalElementsShouldMatchActualUserCount() throws Exception {
            registerUsers(12);

            mockMvc.perform(get("/api/v1/users")
                            .param("page", "0")
                            .param("size", "5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(12))
                    .andExpect(jsonPath("$.data.totalPages").value(3))
                    .andExpect(jsonPath("$.data.content.length()").value(5))
                    .andExpect(jsonPath("$.data.last").value(false));
        }

        @Test
        @DisplayName("totalPages correctly rounds up for non-exact division")
        void totalPagesShouldRoundUpForNonExactDivision() throws Exception {
            registerUsers(7);

            mockMvc.perform(get("/api/v1/users")
                            .param("page", "0")
                            .param("size", "3")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(7))
                    .andExpect(jsonPath("$.data.totalPages").value(3));
        }

        @Test
        @DisplayName("totalPages is 1 when all users fit in a single page")
        void totalPagesShouldBeOneWhenAllUsersFitOnOnePage() throws Exception {
            registerUsers(5);

            mockMvc.perform(get("/api/v1/users")
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(5))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andExpect(jsonPath("$.data.last").value(true));
        }
    }

    // ========== SENSITIVE FIELD PROTECTION ==========

    @Nested
    @DisplayName("Sensitive Field Protection")
    class SensitiveFieldProtectionTests {

        @Test
        @DisplayName("Password and other sensitive fields are never exposed in paginated response")
        void shouldNeverExposePasswordInResponse() throws Exception {
            registerUsers(1);

            mockMvc.perform(get("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").isNumber())
                    .andExpect(jsonPath("$.data.content[0].firstName").value("User1"))
                    .andExpect(jsonPath("$.data.content[0].lastName").value("Test"))
                    .andExpect(jsonPath("$.data.content[0].email").value("user1@example.com"))
                    .andExpect(jsonPath("$.data.content[0].roles").isArray())
                    .andExpect(jsonPath("$.data.content[0].createdAt").exists())
                    .andExpect(jsonPath("$.data.content[0].updatedAt").exists())
                    .andExpect(jsonPath("$.data.content[0].password").doesNotExist());
        }
    }

    // ========== BOUNDARY VALUES ==========

    @Nested
    @DisplayName("Boundary Values")
    class BoundaryValueTests {

        @Test
        @DisplayName("GET /api/v1/users?size=1 - minimum valid size")
        void shouldAcceptMinimumSize() throws Exception {
            registerUsers(3);

            mockMvc.perform(get("/api/v1/users")
                            .param("size", "1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.size").value(1))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.totalPages").value(3))
                    .andExpect(jsonPath("$.data.last").value(false));
        }

        @Test
        @DisplayName("GET /api/v1/users?size=100 - maximum valid size")
        void shouldAcceptMaximumSize() throws Exception {
            registerUsers(3);

            mockMvc.perform(get("/api/v1/users")
                            .param("size", "100")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(3))
                    .andExpect(jsonPath("$.data.size").value(100))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andExpect(jsonPath("$.data.last").value(true));
        }
    }
}
