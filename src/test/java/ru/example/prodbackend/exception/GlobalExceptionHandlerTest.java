package ru.example.prodbackend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import ru.example.prodbackend.exception.dto.ErrorResponse;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void checklistNotFoundPreservesSpecific404Code() {
        assertError(handler.handleChecklistItemNotFound(new ChecklistItemNotFoundException()), 404, "CHECKLIST_ITEM_NOT_FOUND");
    }

    @Test
    void userAndItemNotFoundPreserveShared404Code() {
        assertError(handler.handleUserNotFoundException(new UserNotFoundException("Пользователь не найден")), 404, "NOT_FOUND");
        assertError(handler.handleUserNotFoundException(new ItemNotFoundException("Вещь не найдена")), 404, "NOT_FOUND");
    }

    @Test
    void boxNotFoundReturns404() {
        assertError(handler.handleBoxNotFound(new BoxNotFoundException()), 404, "BOX_NOT_FOUND");
    }

    @Test
    void resourceConflictReturns409() {
        assertError(handler.handleResourceConflict(new ResourceConflictException("Номер занят")), 409, "DATA_CONFLICT");
    }

    @Test
    void requestValidationReturns422WithFieldError() {
        var response = handler.handleRequestValidation(new RequestValidationException("number", "Номер должен быть положительным"));

        assertError(response, 422, "VALIDATION_ERROR");
        var fieldError = response.getBody().getFieldErrors().get(0);
        assertEquals("number", fieldError.getField());
        assertEquals("Номер должен быть положительным", fieldError.getIssue());
    }

    private void assertError(ResponseEntity<ErrorResponse> response, int status, String code) {
        assertEquals(status, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(code, response.getBody().getCode());
        assertFalse(response.getBody().getMessage().isBlank());
        assertNotNull(response.getBody().getTraceId());
        assertNotNull(response.getBody().getTime());
    }
}
