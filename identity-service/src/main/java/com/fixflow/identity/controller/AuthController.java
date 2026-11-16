package com.fixflow.identity.controller;

import com.fixflow.identity.dto.request.RegisterRequest;
import com.fixflow.identity.dto.response.ApiResponse;
import com.fixflow.identity.dto.response.UserResponse;
import com.fixflow.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse userResponse = userService.register(request);
        ApiResponse<UserResponse> response = ApiResponse.success("User registered successfully", userResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
