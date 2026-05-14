package com.fdshield.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fdshield.dto.*;
import com.fdshield.service.FdService;
import com.fdshield.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FdShieldControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private FdService fdService;

    @InjectMocks
    private FdShieldController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static UserResponse sampleUserResponse() {
        return new UserResponse(
                1L, "Arjun Kumar", "arjun@example.com",
                new BigDecimal("80000"), new BigDecimal("40000"),
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    private static FdResponse sampleFdResponse() {
        return new FdResponse(
                1L, 1L,
                new BigDecimal("100000"), new BigDecimal("7.5"),
                12, new BigDecimal("107500.00"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1),
                "SHORT_TERM", "ACTIVE");
    }

    private static PortfolioResponse samplePortfolioResponse() {
        return new PortfolioResponse(
                sampleUserResponse(),
                List.of(sampleFdResponse()),
                new BigDecimal("100000"),
                new BigDecimal("107500.00"),
                null);
    }

    private static WithdrawResponse sampleWithdrawResponse() {
        return new WithdrawResponse(
                1L, 1L,
                new BigDecimal("100000"),
                new BigDecimal("3750.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("102750.00"),
                "Withdrawal successful");
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    static Stream<Arguments> createUserSuccessCases() {
        return Stream.of(
                Arguments.of(
                        "Valid user returns 200 with UserResponse body",
                        "Arjun Kumar", "arjun@example.com",
                        new BigDecimal("80000"), new BigDecimal("40000")),
                Arguments.of(
                        "User with minimum valid income returns 200",
                        "Priya Sharma", "priya@example.com",
                        new BigDecimal("1"), new BigDecimal("1")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("createUserSuccessCases")
    @DisplayName("POST /users – valid request returns 200 and UserResponse")
    void createUser_success(
            String description,
            String name, String email,
            BigDecimal monthlyIncome, BigDecimal monthlyExpenses) throws Exception {

        CreateUserRequest req = new CreateUserRequest(name, email, monthlyIncome, monthlyExpenses);
        UserResponse stubResponse = sampleUserResponse();

        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(stubResponse);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stubResponse.id))
                .andExpect(jsonPath("$.email").value(stubResponse.email));

        verify(userService).createUser(any(CreateUserRequest.class));
    }

    static Stream<Arguments> createUserValidationFailureCases() {
        return Stream.of(
                Arguments.of(
                        "Blank name returns 400",
                        """
                                {"name":"","email":"arjun@example.com","monthlyIncome":80000,"monthlyExpenses":40000}
                                """),
                Arguments.of(
                        "Invalid email format returns 400",
                        """
                                {"name":"Arjun","email":"not-an-email","monthlyIncome":80000,"monthlyExpenses":40000}
                                """),
                Arguments.of(
                        "Null monthlyIncome returns 400",
                        """
                                {"name":"Arjun","email":"arjun@example.com","monthlyExpenses":40000}
                                """),
                Arguments.of(
                        "Negative monthlyExpenses returns 400",
                        """
                                {"name":"Arjun","email":"arjun@example.com","monthlyIncome":80000,"monthlyExpenses":-1}
                                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("createUserValidationFailureCases")
    @DisplayName("POST /users – invalid request body returns 400")
    void createUser_validationFailure(String description, String invalidJson) throws Exception {
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(userService, never()).createUser(any());
    }

    static Stream<Arguments> getUserSuccessCases() {
        return Stream.of(
                Arguments.of("userId=1 returns 200 with UserResponse", 1L, "arjun@example.com"),
                Arguments.of("userId=42 returns 200 with UserResponse", 42L, "arjun@example.com"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getUserSuccessCases")
    @DisplayName("GET /users/{id} – existing user returns 200")
    void getUser_success(String description, long userId, String expectedEmail) throws Exception {
        when(userService.getUser(userId)).thenReturn(sampleUserResponse());

        mockMvc.perform(get("/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(expectedEmail));

        verify(userService).getUser(userId);
    }

    static Stream<Arguments> createFdSuccessCases() {
        return Stream.of(
                Arguments.of(
                        "Valid SHORT_TERM FD request returns 200",
                        1L, new BigDecimal("100000"), new BigDecimal("7.5"),
                        12, LocalDate.of(2025, 1, 1)),
                Arguments.of(
                        "Valid LONG_TERM FD request returns 200",
                        1L, new BigDecimal("200000"), new BigDecimal("8.0"),
                        24, LocalDate.of(2025, 6, 1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("createFdSuccessCases")
    @DisplayName("POST /fds – valid request returns 200 and FdResponse")
    void createFd_success(
            String description,
            Long userId, BigDecimal principal, BigDecimal interestRate,
            Integer durationMonths, LocalDate startDate) throws Exception {

        CreateFdRequest req = new CreateFdRequest(userId, principal, interestRate,
                durationMonths, startDate);
        FdResponse stubResponse = sampleFdResponse();

        when(fdService.createFd(any(CreateFdRequest.class))).thenReturn(stubResponse);

        mockMvc.perform(post("/fds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stubResponse.id))
                .andExpect(jsonPath("$.status").value(stubResponse.status));

        verify(fdService).createFd(any(CreateFdRequest.class));
    }

    static Stream<Arguments> createFdValidationFailureCases() {
        return Stream.of(
                Arguments.of(
                        "Null userId returns 400",
                        """
                                {"principal":100000,"interestRate":7.5,"durationMonths":12,"startDate":"2025-01-01"}
                                """),
                Arguments.of(
                        "Negative principal returns 400",
                        """
                                {"userId":1,"principal":-1,"interestRate":7.5,"durationMonths":12,"startDate":"2025-01-01"}
                                """),
                Arguments.of(
                        "Interest rate above max (20.0) returns 400",
                        """
                                {"userId":1,"principal":100000,"interestRate":25.0,"durationMonths":12,"startDate":"2025-01-01"}
                                """),
                Arguments.of(
                        "Duration of 0 months returns 400",
                        """
                                {"userId":1,"principal":100000,"interestRate":7.5,"durationMonths":0,"startDate":"2025-01-01"}
                                """),
                Arguments.of(
                        "Null startDate returns 400",
                        """
                                {"userId":1,"principal":100000,"interestRate":7.5,"durationMonths":12}
                                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("createFdValidationFailureCases")
    @DisplayName("POST /fds – invalid request body returns 400")
    void createFd_validationFailure(String description, String invalidJson) throws Exception {
        mockMvc.perform(post("/fds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(fdService, never()).createFd(any());
    }

    static Stream<Arguments> getPortfolioSuccessCases() {
        return Stream.of(
                Arguments.of("userId=1 returns 200 with PortfolioResponse", 1L),
                Arguments.of("userId=99 returns 200 with PortfolioResponse", 99L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getPortfolioSuccessCases")
    @DisplayName("GET /users/{id}/portfolio – returns 200 and PortfolioResponse")
    void getPortfolio_success(String description, long userId) throws Exception {
        when(fdService.getPortfolio(userId)).thenReturn(samplePortfolioResponse());

        mockMvc.perform(get("/users/{id}/portfolio", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrincipal").value(100000))
                .andExpect(jsonPath("$.activeFds").isArray())
                .andExpect(jsonPath("$.activeFds[0].status").value("ACTIVE"));

        verify(fdService).getPortfolio(userId);
    }

    static Stream<Arguments> withdrawSuccessCases() {
        return Stream.of(
                Arguments.of("fdId=1 returns 200 with WithdrawResponse", 1L),
                Arguments.of("fdId=42 returns 200 with WithdrawResponse", 42L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withdrawSuccessCases")
    @DisplayName("POST /withdraw – valid fdId returns 200 and WithdrawResponse")
    void withdraw_success(String description, long fdId) throws Exception {
        WithdrawRequest req = new WithdrawRequest(fdId);
        WithdrawResponse stubResponse = sampleWithdrawResponse();

        when(fdService.withdrawFd(fdId)).thenReturn(stubResponse);

        mockMvc.perform(post("/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fdId").value(stubResponse.fdId))
                .andExpect(jsonPath("$.netPayout").value(102750.00))
                .andExpect(jsonPath("$.message").value("Withdrawal successful"));

        verify(fdService).withdrawFd(fdId);
    }

    static Stream<Arguments> withdrawValidationFailureCases() {
        return Stream.of(
                Arguments.of(
                        "Null fdId returns 400",
                        """
                                {"fdId":null}
                                """),
                Arguments.of(
                        "Missing fdId field returns 400",
                        """
                                {}
                                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withdrawValidationFailureCases")
    @DisplayName("POST /withdraw – invalid request body returns 400")
    void withdraw_validationFailure(String description, String invalidJson) throws Exception {
        mockMvc.perform(post("/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(fdService, never()).withdrawFd(anyLong());
    }

    static Stream<Arguments> healthCheckCases() {
        return Stream.of(
                Arguments.of("Health endpoint returns 200 and expected message"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("healthCheckCases")
    @DisplayName("GET /health – returns 200 and status message")
    void health_returnsOk(String description) throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("FD Shield Spring Service is running"));
    }
}