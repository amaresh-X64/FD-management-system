package com.fdmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fdmanagement.dto.*;
import com.fdmanagement.service.FdService;
import com.fdmanagement.service.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FdmanagementControllerTest {

    @Mock private UserService userService;
    @Mock private FdService fdService;

    @InjectMocks private fdmanagementController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── shared fixtures ───────────────────────────────────────────────────────

    private static UserResponse sampleUserResponse() {
        return new UserResponse(1L, "Arjun Kumar", "arjun@example.com",
                new BigDecimal("80000"), new BigDecimal("40000"),
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    private static FdResponse sampleFdResponse() {
        return new FdResponse(1L, 1L,
                new BigDecimal("100000"), new BigDecimal("7.5"),
                12, new BigDecimal("107500.00"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1),
                "SHORT_TERM", "ACTIVE");
    }

    private static PortfolioResponse samplePortfolioResponse() {
        return new PortfolioResponse(sampleUserResponse(), List.of(sampleFdResponse()),
                new BigDecimal("100000"), new BigDecimal("107500.00"), null);
    }

    private static WithdrawResponse sampleWithdrawResponse() {
        return new WithdrawResponse(1L, 1L,
                new BigDecimal("100000"), new BigDecimal("3750.00"),
                new BigDecimal("1000.00"), new BigDecimal("102750.00"),
                "Withdrawal successful");
    }

    // ── POST /users ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /users")
    class CreateUser {

        @Test
        void shouldReturn200WithUserResponse_whenInputIsValidUserRequest() throws Exception {
            when(userService.createUser(argThat(r -> "arjun@example.com".equals(r.email))))
                    .thenReturn(sampleUserResponse());

            mockMvc.perform(post("/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Arjun Kumar","email":"arjun@example.com",
                                 "monthlyIncome":80000,"monthlyExpenses":40000}
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("arjun@example.com"));

            verify(userService).createUser(argThat(r -> "arjun@example.com".equals(r.email)));
        }

        @Test
        void shouldReturn200WithUserResponse_whenInputIsUserWithMinimumValidIncome() throws Exception {
            when(userService.createUser(argThat(r -> "priya@example.com".equals(r.email))))
                    .thenReturn(sampleUserResponse());

            mockMvc.perform(post("/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Priya Sharma","email":"priya@example.com",
                                 "monthlyIncome":1,"monthlyExpenses":1}
                                """))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsBlankName() throws Exception {
            mockMvc.perform(post("/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"","email":"arjun@example.com",
                                 "monthlyIncome":80000,"monthlyExpenses":40000}
                                """))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).createUser(argThat(r -> true));
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsInvalidEmailFormat() throws Exception {
            mockMvc.perform(post("/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Arjun","email":"not-an-email",
                                 "monthlyIncome":80000,"monthlyExpenses":40000}
                                """))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).createUser(argThat(r -> true));
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsNullMonthlyIncome() throws Exception {
            mockMvc.perform(post("/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Arjun","email":"arjun@example.com",
                                 "monthlyExpenses":40000}
                                """))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).createUser(argThat(r -> true));
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsNegativeMonthlyExpenses() throws Exception {
            mockMvc.perform(post("/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Arjun","email":"arjun@example.com",
                                 "monthlyIncome":80000,"monthlyExpenses":-1}
                                """))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).createUser(argThat(r -> true));
        }
    }

    // ── GET /users/{id} ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /users/{id}")
    class GetUser {

        @Test
        void shouldReturn200WithUserResponse_whenInputIsExistingUserId1() throws Exception {
            when(userService.getUser(1L)).thenReturn(sampleUserResponse());

            mockMvc.perform(get("/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("arjun@example.com"));

            verify(userService).getUser(1L);
        }

        @Test
        void shouldReturn200WithUserResponse_whenInputIsExistingUserId42() throws Exception {
            when(userService.getUser(42L)).thenReturn(sampleUserResponse());

            mockMvc.perform(get("/users/42"))
                    .andExpect(status().isOk());

            verify(userService).getUser(42L);
        }
    }

    // ── POST /fds ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /fds")
    class CreateFd {

        @Test
        void shouldReturn200WithFdResponse_whenInputIsValidShortTermFdRequest() throws Exception {
            when(fdService.createFd(argThat(r -> r.userId == 1L && r.durationMonths == 12)))
                    .thenReturn(sampleFdResponse());

            mockMvc.perform(post("/fds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"userId":1,"principal":100000,"interestRate":7.5,
                                 "durationMonths":12,"startDate":"2025-01-01"}
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            verify(fdService).createFd(argThat(r -> r.userId == 1L && r.durationMonths == 12));
        }

        @Test
        void shouldReturn200WithFdResponse_whenInputIsValidLongTermFdRequest() throws Exception {
            when(fdService.createFd(argThat(r -> r.durationMonths == 24)))
                    .thenReturn(sampleFdResponse());

            mockMvc.perform(post("/fds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"userId":1,"principal":200000,"interestRate":8.0,
                                 "durationMonths":24,"startDate":"2025-06-01"}
                                """))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsNullUserId() throws Exception {
            mockMvc.perform(post("/fds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"principal":100000,"interestRate":7.5,
                                 "durationMonths":12,"startDate":"2025-01-01"}
                                """))
                    .andExpect(status().isBadRequest());

            verify(fdService, never()).createFd(argThat(r -> true));
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsNegativePrincipal() throws Exception {
            mockMvc.perform(post("/fds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"userId":1,"principal":-1,"interestRate":7.5,
                                 "durationMonths":12,"startDate":"2025-01-01"}
                                """))
                    .andExpect(status().isBadRequest());

            verify(fdService, never()).createFd(argThat(r -> true));
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsInterestRateAboveMaximumOf20() throws Exception {
            mockMvc.perform(post("/fds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"userId":1,"principal":100000,"interestRate":25.0,
                                 "durationMonths":12,"startDate":"2025-01-01"}
                                """))
                    .andExpect(status().isBadRequest());

            verify(fdService, never()).createFd(argThat(r -> true));
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsDurationOfZeroMonths() throws Exception {
            mockMvc.perform(post("/fds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"userId":1,"principal":100000,"interestRate":7.5,
                                 "durationMonths":0,"startDate":"2025-01-01"}
                                """))
                    .andExpect(status().isBadRequest());

            verify(fdService, never()).createFd(argThat(r -> true));
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsNullStartDate() throws Exception {
            mockMvc.perform(post("/fds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"userId":1,"principal":100000,"interestRate":7.5,
                                 "durationMonths":12}
                                """))
                    .andExpect(status().isBadRequest());

            verify(fdService, never()).createFd(argThat(r -> true));
        }
    }

    // ── GET /users/{id}/portfolio ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /users/{id}/portfolio")
    class GetPortfolio {

        @Test
        void shouldReturn200WithPortfolioResponse_whenInputIsExistingUserId1() throws Exception {
            when(fdService.getPortfolio(1L)).thenReturn(samplePortfolioResponse());

            mockMvc.perform(get("/users/1/portfolio"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalPrincipal").value(100000))
                    .andExpect(jsonPath("$.activeFds").isArray())
                    .andExpect(jsonPath("$.activeFds[0].status").value("ACTIVE"));

            verify(fdService).getPortfolio(1L);
        }

        @Test
        void shouldReturn200WithPortfolioResponse_whenInputIsExistingUserId99() throws Exception {
            when(fdService.getPortfolio(99L)).thenReturn(samplePortfolioResponse());

            mockMvc.perform(get("/users/99/portfolio"))
                    .andExpect(status().isOk());

            verify(fdService).getPortfolio(99L);
        }
    }

    // ── POST /withdraw ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /withdraw")
    class Withdraw {

        @Test
        void shouldReturn200WithWithdrawResponse_whenInputIsValidFdId1() throws Exception {
            when(fdService.withdrawFd(1L)).thenReturn(sampleWithdrawResponse());

            mockMvc.perform(post("/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fdId\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fdId").value(1))
                    .andExpect(jsonPath("$.netPayout").value(102750.00))
                    .andExpect(jsonPath("$.message").value("Withdrawal successful"));

            verify(fdService).withdrawFd(1L);
        }

        @Test
        void shouldReturn200WithWithdrawResponse_whenInputIsValidFdId42() throws Exception {
            when(fdService.withdrawFd(42L)).thenReturn(sampleWithdrawResponse());

            mockMvc.perform(post("/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fdId\":42}"))
                    .andExpect(status().isOk());

            verify(fdService).withdrawFd(42L);
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsNullFdId() throws Exception {
            mockMvc.perform(post("/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fdId\":null}"))
                    .andExpect(status().isBadRequest());

            verify(fdService, never()).withdrawFd(anyLong());
        }

        @Test
        void shouldReturn400AndNeverCallService_whenInputIsMissingFdIdField() throws Exception {
            mockMvc.perform(post("/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verify(fdService, never()).withdrawFd(anyLong());
        }
    }

    // ── GET /health ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /health")
    class Health {

        @Test
        void shouldReturn200WithStatusMessage_whenInputIsHealthCheckRequest() throws Exception {
            mockMvc.perform(get("/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("FD Shield Spring Service is running"));
        }
    }
}