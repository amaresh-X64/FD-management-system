package com.fdmanagement.controller;

import com.fdmanagement.service.FdService;
import com.fdmanagement.service.UserService;
import com.fdmanagement.controller.fdmanagementController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
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

    // RuntimeException branch: with message and with null message
    static Stream<Arguments> runtimeExceptionCases() {
        return Stream.of(
            Arguments.of("RuntimeException with message → error field populated",
                         "User not found: 99", "User not found: 99"),
            Arguments.of("RuntimeException null message → fallback 'Unknown error'",
                         null, "Unknown error")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runtimeExceptionCases")
    @DisplayName("GlobalExceptionHandler – RuntimeException → 500 JSON with error field")
    void handleRuntimeException(String label, String msg, String expectedError) throws Exception {
        when(userService.getUser(99L))
                .thenThrow(msg != null ? new RuntimeException(msg) : new RuntimeException());

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(expectedError));
    }

    // Generic Exception branch — tested by calling the handler directly
    // because MockMvc + Mockito cannot throw checked Exception from non-declaring methods
    // and any RuntimeException subclass gets caught by handleRuntime first
    static Stream<Arguments> genericExceptionCases() {
        return Stream.of(
            Arguments.of("Exception with message → error field populated",
                         "Unexpected DB failure", "Unexpected DB failure"),
            Arguments.of("Exception null message → fallback 'Unknown error'",
                         null, "Unknown error")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("genericExceptionCases")
    @DisplayName("GlobalExceptionHandler – generic Exception → 500 JSON with error field")
    void handleGenericException(String label, String msg, String expectedError) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception ex = msg != null ? new Exception(msg) : new Exception();

        var response = handler.handleGeneric(ex);

        assertThat(response.getStatusCodeValue()).isEqualTo(500);
        assertThat(response.getBody()).containsEntry("error", expectedError);
    }
}