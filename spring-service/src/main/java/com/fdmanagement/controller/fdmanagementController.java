package com.fdmanagement.controller;

import com.fdmanagement.dto.*;
import com.fdmanagement.service.FdService;
import com.fdmanagement.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class fdmanagementController {

    private final UserService userService;
    private final FdService fdService;

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.ok(userService.createUser(req));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PostMapping("/fds")
    public ResponseEntity<FdResponse> createFd(@Valid @RequestBody CreateFdRequest req) {
        return ResponseEntity.ok(fdService.createFd(req));
    }

    @GetMapping("/users/{id}/portfolio")
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable Long id) {
        return ResponseEntity.ok(fdService.getPortfolio(id));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawResponse> withdraw(@Valid @RequestBody WithdrawRequest req) {
        return ResponseEntity.ok(fdService.withdrawFd(req.fdId));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("FD Shield Spring Service is running");
    }
}
