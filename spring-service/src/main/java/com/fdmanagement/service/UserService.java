package com.fdmanagement.service;

import com.fdmanagement.dto.CreateUserRequest;
import com.fdmanagement.dto.UserResponse;
import com.fdmanagement.entity.User;
import com.fdmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FdService fdService;

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (userRepository.findByEmail(req.email).isPresent()) {
            throw new RuntimeException("Email already registered: " + req.email);
        }
        User user = User.builder()
                .name(req.name)
                .email(req.email)
                .monthlyIncome(req.monthlyIncome)
                .monthlyExpenses(req.monthlyExpenses)
                .build();
        user = userRepository.save(user);
        return fdService.toUserResponse(user);
    }

    public UserResponse getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        return fdService.toUserResponse(user);
    }
}
