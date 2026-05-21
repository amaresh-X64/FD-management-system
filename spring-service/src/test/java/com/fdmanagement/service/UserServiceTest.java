package com.fdmanagement.service;

import com.fdmanagement.dto.CreateUserRequest;
import com.fdmanagement.dto.UserResponse;
import com.fdmanagement.entity.User;
import com.fdmanagement.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FdService fdService;

    @InjectMocks private UserService userService;

    private User savedUser;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        savedUser = User.builder()
                .id(1L).name("Arjun Kumar").email("arjun@example.com")
                .monthlyIncome(new BigDecimal("80000"))
                .monthlyExpenses(new BigDecimal("40000")).build();

        userResponse = new UserResponse(
                1L, "Arjun Kumar", "arjun@example.com",
                new BigDecimal("80000"), new BigDecimal("40000"), null);
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        void shouldReturnMappedResponse_whenInputIsValidUserWithUniqueEmail() {
            CreateUserRequest req = new CreateUserRequest(
                    "Arjun Kumar", "arjun@example.com",
                    new BigDecimal("80000"), new BigDecimal("40000"));

            when(userRepository.findByEmail("arjun@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(argThat(u -> "arjun@example.com".equals(u.getEmail()))))
                    .thenReturn(savedUser);
            when(fdService.toUserResponse(savedUser)).thenReturn(userResponse);

            assertThat(userService.createUser(req)).isEqualTo(userResponse);
            verify(userRepository).save(argThat(u -> "arjun@example.com".equals(u.getEmail())));
        }

        @Test
        void shouldReturnMappedResponse_whenInputIsUserWithZeroExpenses() {
            User zeroExpUser = User.builder().id(2L).name("Priya Sharma")
                    .email("priya@example.com")
                    .monthlyIncome(new BigDecimal("50000"))
                    .monthlyExpenses(BigDecimal.ZERO).build();
            UserResponse zeroExpResponse = new UserResponse(
                    2L, "Priya Sharma", "priya@example.com",
                    new BigDecimal("50000"), BigDecimal.ZERO, null);
            CreateUserRequest req = new CreateUserRequest(
                    "Priya Sharma", "priya@example.com",
                    new BigDecimal("50000"), BigDecimal.ZERO);

            when(userRepository.findByEmail("priya@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(argThat(u -> "priya@example.com".equals(u.getEmail()))))
                    .thenReturn(zeroExpUser);
            when(fdService.toUserResponse(zeroExpUser)).thenReturn(zeroExpResponse);

            assertThat(userService.createUser(req)).isEqualTo(zeroExpResponse);
        }

        @Test
        void shouldMapAllRequestFieldsOntoEntity_whenInputIsValidRequest() {
            CreateUserRequest req = new CreateUserRequest(
                    "Arjun Kumar", "arjun@example.com",
                    new BigDecimal("80000"), new BigDecimal("40000"));

            when(userRepository.findByEmail("arjun@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(argThat(u -> "arjun@example.com".equals(u.getEmail()))))
                    .thenReturn(savedUser);
            when(fdService.toUserResponse(savedUser)).thenReturn(userResponse);

            userService.createUser(req);

            verify(userRepository).save(argThat(u ->
                    "Arjun Kumar".equals(u.getName()) &&
                    "arjun@example.com".equals(u.getEmail()) &&
                    new BigDecimal("80000").compareTo(u.getMonthlyIncome()) == 0 &&
                    new BigDecimal("40000").compareTo(u.getMonthlyExpenses()) == 0));
        }

        @Test
        void shouldThrowRuntimeExceptionAndNeverSave_whenInputIsDuplicateEmail() {
            CreateUserRequest req = new CreateUserRequest(
                    "Any Name", "arjun@example.com",
                    new BigDecimal("80000"), new BigDecimal("40000"));

            when(userRepository.findByEmail("arjun@example.com")).thenReturn(Optional.of(savedUser));

            assertThatThrownBy(() -> userService.createUser(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Email already registered: arjun@example.com");

            verify(userRepository, never()).save(argThat(u -> true));
            verify(fdService, never()).toUserResponse(argThat(u -> true));
        }

        @Test
        void shouldThrowRuntimeExceptionWithCorrectEmail_whenInputIsAnotherDuplicateEmail() {
            User priya = User.builder().id(2L).name("Priya").email("priya@example.com")
                    .monthlyIncome(BigDecimal.ONE).monthlyExpenses(BigDecimal.ONE).build();
            CreateUserRequest req = new CreateUserRequest(
                    "Any Name", "priya@example.com",
                    new BigDecimal("80000"), new BigDecimal("40000"));

            when(userRepository.findByEmail("priya@example.com")).thenReturn(Optional.of(priya));

            assertThatThrownBy(() -> userService.createUser(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Email already registered: priya@example.com");
        }
    }

    // ── getUser ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUser")
    class GetUser {

        @Test
        void shouldReturnMappedResponse_whenInputIsExistingUserId1() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
            when(fdService.toUserResponse(savedUser)).thenReturn(userResponse);

            assertThat(userService.getUser(1L)).isEqualTo(userResponse);
            verify(fdService).toUserResponse(savedUser);
        }

        @Test
        void shouldReturnMappedResponse_whenInputIsExistingUserId42() {
            User user42 = User.builder().id(42L).name("Arjun Kumar")
                    .email("arjun@example.com")
                    .monthlyIncome(new BigDecimal("80000"))
                    .monthlyExpenses(new BigDecimal("40000")).build();
            UserResponse response42 = new UserResponse(
                    42L, "Arjun Kumar", "arjun@example.com",
                    new BigDecimal("80000"), new BigDecimal("40000"), null);

            when(userRepository.findById(42L)).thenReturn(Optional.of(user42));
            when(fdService.toUserResponse(user42)).thenReturn(response42);

            assertThat(userService.getUser(42L)).isEqualTo(response42);
        }

        @Test
        void shouldThrowRuntimeException_whenInputIsNonExistentUserId99() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found: 99");

            verify(fdService, never()).toUserResponse(argThat(u -> true));
        }

        @Test
        void shouldThrowRuntimeException_whenInputIsNonExistentUserId0() {
            when(userRepository.findById(0L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser(0L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found: 0");
        }
    }
}