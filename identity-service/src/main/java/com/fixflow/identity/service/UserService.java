package com.fixflow.identity.service;

import com.fixflow.identity.dto.request.RegisterRequest;
import com.fixflow.identity.dto.request.UpdateUserRequest;
import com.fixflow.identity.dto.response.PageResponse;
import com.fixflow.identity.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse register(RegisterRequest request);

    UserResponse getUserById(Long id);

    List<UserResponse> getAllUsers();

    /**
     * Retrieves a paginated list of users.
     * @param page zero-based page index (must be >= 0)
     * @param size page size (must be between 1 and 100)
     * @return paginated response containing UserResponse items
     */
    PageResponse<UserResponse> getUsers(int page, int size);

    UserResponse updateUser(Long id, UpdateUserRequest request);

    void deleteUser(Long id);
}
