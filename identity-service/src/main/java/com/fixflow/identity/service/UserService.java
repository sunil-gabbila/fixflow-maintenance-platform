package com.fixflow.identity.service;

import com.fixflow.identity.dto.request.RegisterRequest;
import com.fixflow.identity.dto.request.UpdateUserRequest;
import com.fixflow.identity.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse register(RegisterRequest request);

    UserResponse getUserById(Long id);

    List<UserResponse> getAllUsers();

    UserResponse updateUser(Long id, UpdateUserRequest request);

    void deleteUser(Long id);
}
