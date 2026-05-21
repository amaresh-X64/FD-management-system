package com.fdmanagement.controller;

import com.fdmanagement.service.FdService;
import com.fdmanagement.service.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock private UserService userService;
    @Mock private FdService fdService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new fdmanagementController(userService, fdService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── RuntimeException handler ──────────────────────────────────────────────

    @Nested
    @DisplayName("handleRuntimeException")
    class HandleRuntimeException {

        @Test
        void shouldReturn500WithErrorField_whenInputIsRuntimeExceptionWithMessage() throws Exception {
            when(userService.getUser(99L)).thenThrow(new RuntimeException("User not found: 99"));

            mockMvc.perform(get("/users/99"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("User not found: 99"));
        }

        @Test
        void shouldReturn500WithUnknownError_whenInputIsRuntimeExceptionWithNullMessage() throws Exception {
            when(userService.getUser(99L)).thenThrow(new RuntimeException((String) null));

            mockMvc.perform(get("/users/99"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Unknown error"));
        }
    }

    // ── generic Exception handler ─────────────────────────────────────────────

    @Nested
    @DisplayName("handleGenericException")
    class HandleGenericException {

        @Test
        void shouldReturn500WithErrorField_whenInputIsExceptionWithMessage() {
            GlobalExceptionHandler handler = new GlobalExceptionHandler();

            var response = handler.handleGeneric(new Exception("Unexpected DB failure"));

            assertThat(response.getStatusCodeValue()).isEqualTo(500);
            assertThat(response.getBody()).containsEntry("error", "Unexpected DB failure");
        }

        @Test
        void shouldReturn500WithUnknownError_whenInputIsExceptionWithNullMessage() {
            GlobalExceptionHandler handler = new GlobalExceptionHandler();

            var response = handler.handleGeneric(new Exception((String) null));

            assertThat(response.getStatusCodeValue()).isEqualTo(500);
            assertThat(response.getBody()).containsEntry("error", "Unknown error");
        }
    }
}