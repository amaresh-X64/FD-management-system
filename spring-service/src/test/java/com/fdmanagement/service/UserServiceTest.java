package com.fdmanagement.service;

import com.fdmanagement.dto.CreateUserRequest;
import com.fdmanagement.dto.UserResponse;
import com.fdmanagement.entity.User;
import com.fdmanagement.repository.UserRepository;
import com.fdmanagement.service.FdService;
import com.fdmanagement.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

        @Mock
        private UserRepository userRepository;
        @Mock
        private FdService fdService;

        @InjectMocks
        private UserService userService;

        private User savedUser;
        private UserResponse userResponse;

        @BeforeEach
        void setUp() {
                savedUser = User.builder()
                                .id(1L)
                                .name("Arjun Kumar")
                                .email("arjun@example.com")
                                .monthlyIncome(new BigDecimal("80000"))
                                .monthlyExpenses(new BigDecimal("40000"))
                                .build();

                userResponse = new UserResponse(
                                1L, "Arjun Kumar", "arjun@example.com",
                                new BigDecimal("80000"), new BigDecimal("40000"), null);
        }

        static Stream<Arguments> createUserSuccessCases() {
                return Stream.of(
                                Arguments.of(
                                                "New user with unique email is saved and response is returned",
                                                "Arjun Kumar", "arjun@example.com",
                                                new BigDecimal("80000"), new BigDecimal("40000")),
                                Arguments.of(
                                                "User with zero expenses is saved correctly",
                                                "Priya Sharma", "priya@example.com",
                                                new BigDecimal("50000"), BigDecimal.ZERO));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("createUserSuccessCases")
        @DisplayName("createUser – saves user and returns mapped response")
        void createUser_success(
                        String description,
                        String name, String email,
                        BigDecimal monthlyIncome, BigDecimal monthlyExpenses) {

                CreateUserRequest req = new CreateUserRequest(name, email, monthlyIncome, monthlyExpenses);

                User builtUser = User.builder()
                                .id(1L).name(name).email(email)
                                .monthlyIncome(monthlyIncome).monthlyExpenses(monthlyExpenses)
                                .build();

                UserResponse expectedResponse = new UserResponse(
                                1L, name, email, monthlyIncome, monthlyExpenses, null);

                when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
                when(userRepository.save(any(User.class))).thenReturn(builtUser);
                when(fdService.toUserResponse(builtUser)).thenReturn(expectedResponse);

                UserResponse response = userService.createUser(req);

                assertThat(response).isEqualTo(expectedResponse);
                verify(userRepository).save(any(User.class));
                verify(fdService).toUserResponse(builtUser);
        }

        static Stream<Arguments> createUserDuplicateEmailCases() {
                return Stream.of(
                                Arguments.of(
                                                "Duplicate email throws RuntimeException with message 'Email already registered: arjun@example.com'",
                                                "arjun@example.com",
                                                "Email already registered: arjun@example.com"),
                                Arguments.of(
                                                "Duplicate email throws RuntimeException with message 'Email already registered: priya@example.com'",
                                                "priya@example.com",
                                                "Email already registered: priya@example.com"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("createUserDuplicateEmailCases")
        @DisplayName("createUser – duplicate email throws RuntimeException")
        void createUser_duplicateEmail_throwsException(
                        String description, String email, String expectedMessage) {

                CreateUserRequest req = new CreateUserRequest(
                                "Any Name", email, new BigDecimal("80000"), new BigDecimal("40000"));

                when(userRepository.findByEmail(email)).thenReturn(Optional.of(savedUser));

                assertThatThrownBy(() -> userService.createUser(req))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining(expectedMessage);

                verify(userRepository, never()).save(any());
                verify(fdService, never()).toUserResponse(any());
        }

        static Stream<Arguments> createUserFieldMappingCases() {
                return Stream.of(
                                Arguments.of(
                                                "Request fields are mapped correctly onto the saved User entity",
                                                "Arjun Kumar", "arjun@example.com",
                                                new BigDecimal("80000"), new BigDecimal("40000")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("createUserFieldMappingCases")
        @DisplayName("createUser – request fields are mapped onto User entity before save")
        void createUser_fieldMapping(
                        String description,
                        String name, String email,
                        BigDecimal monthlyIncome, BigDecimal monthlyExpenses) {

                CreateUserRequest req = new CreateUserRequest(name, email, monthlyIncome, monthlyExpenses);

                when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
                when(userRepository.save(any(User.class))).thenReturn(savedUser);
                when(fdService.toUserResponse(any())).thenReturn(userResponse);

                userService.createUser(req);

                verify(userRepository).save(argThat(user -> name.equals(user.getName())
                                && email.equals(user.getEmail())
                                && monthlyIncome.compareTo(user.getMonthlyIncome()) == 0
                                && monthlyExpenses.compareTo(user.getMonthlyExpenses()) == 0));
        }

        static Stream<Arguments> getUserSuccessCases() {
                return Stream.of(
                                Arguments.of("Existing userId=1 returns mapped UserResponse", 1L),
                                Arguments.of("Existing userId=42 returns mapped UserResponse", 42L));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("getUserSuccessCases")
        @DisplayName("getUser – existing user returns mapped response")
        void getUser_success(String description, long userId) {
                User user = User.builder()
                                .id(userId).name("Arjun Kumar").email("arjun@example.com")
                                .monthlyIncome(new BigDecimal("80000")).monthlyExpenses(new BigDecimal("40000"))
                                .build();

                UserResponse expectedResponse = new UserResponse(
                                userId, "Arjun Kumar", "arjun@example.com",
                                new BigDecimal("80000"), new BigDecimal("40000"), null);

                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(fdService.toUserResponse(user)).thenReturn(expectedResponse);

                UserResponse response = userService.getUser(userId);

                assertThat(response).isEqualTo(expectedResponse);
                verify(fdService).toUserResponse(user);
        }

        static Stream<Arguments> getUserNotFoundCases() {
                return Stream.of(
                                Arguments.of("userId=99 not found throws RuntimeException",
                                                99L, "User not found: 99"),
                                Arguments.of("userId=0 not found throws RuntimeException",
                                                0L, "User not found: 0"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("getUserNotFoundCases")
        @DisplayName("getUser – non-existent user throws RuntimeException")
        void getUser_notFound_throwsException(
                        String description, long userId, String expectedMessage) {

                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> userService.getUser(userId))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining(expectedMessage);

                verify(fdService, never()).toUserResponse(any());
        }
}