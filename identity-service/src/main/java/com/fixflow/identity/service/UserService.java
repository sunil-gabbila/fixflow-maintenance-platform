package com.fixflow.identity.service;

import com.fixflow.identity.dto.request.RegisterRequest;
import com.fixflow.identity.dto.response.UserResponse;

public interface UserService {

    UserResponse register(RegisterRequest request);
}
