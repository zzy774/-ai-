package com.labreport.server.controller;

import com.labreport.server.common.Constants;
import com.labreport.server.common.Result;
import com.labreport.server.model.dto.LoginRequest;
import com.labreport.server.model.dto.LoginResponse;
import com.labreport.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(Constants.TOKEN_PREFIX.length());
        authService.logout(token);
        return Result.ok(null);
    }
}
