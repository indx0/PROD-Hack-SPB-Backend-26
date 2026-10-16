package ru.example.prodbackend.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.exception.dto.FieldErrorResponse;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({UserNotFoundException.class, ItemNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(
            RuntimeException exception
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("NOT_FOUND")
                .message(exception.getMessage())
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExistsException(
            UserAlreadyExistsException exception
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("USER_ALREADY_EXISTS")
                .message(exception.getMessage())
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        List<FieldErrorResponse> fieldErrors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldErrorResponse(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(errorResponse);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException exception
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("UNAUTHORIZED")
                .message("Invalid email or password")
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("INVALID_REQUEST_BODY")
                .message("Request body is missing or contains invalid JSON")
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ErrorResponse body = ErrorResponse.builder()
                .code("INVALID_REQUEST_PARAMETER")
                .message("Invalid format for parameter: " + exception.getName())
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .fieldErrors(List.of(new FieldErrorResponse(exception.getName(), "Invalid parameter format")))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        boolean duplicate = false;

        for (Throwable cause = exception;
             cause != null;
             cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                duplicate = true;
                break;
            }
        }
        UUID errorId = UUID.randomUUID();

        ErrorResponse body = ErrorResponse.builder()
                .code(duplicate ? "DATA_CONFLICT" : "INTERNAL_ERROR")
                .message(duplicate ? "Resource already exists" : "Internal server error")
                .traceId(errorId)
                .time(Instant.now())
                .build();

        return ResponseEntity
                .status(duplicate ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }

    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenException(
            RefreshTokenException exception
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("INVALID_REFRESH_TOKEN")
                .message("Refresh token is invalid or expired")
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse);
    }

    @ExceptionHandler(MoveProjectNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMoveProjectNotFound(
            MoveProjectNotFoundException exception
    ) {
        ErrorResponse body = ErrorResponse.builder()
                .code("MOVE_PROJECT_NOT_FOUND")
                .message(exception.getMessage())
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(BoxNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBoxNotFound(BoxNotFoundException exception) {
        ErrorResponse body = ErrorResponse.builder()
                .code("BOX_NOT_FOUND")
                .message(exception.getMessage())
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleResourceConflict(ResourceConflictException exception) {
        ErrorResponse body = ErrorResponse.builder()
                .code("DATA_CONFLICT")
                .message(exception.getMessage())
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ErrorResponse> handleRequestValidation(RequestValidationException exception) {
        ErrorResponse body = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .fieldErrors(List.of(new FieldErrorResponse(exception.getField(), exception.getMessage())))
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(RoomNotFoundException exception) {
        ErrorResponse body = ErrorResponse.builder()
                .code("ROOM_NOT_FOUND")
                .message(exception.getMessage())
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }


    @ExceptionHandler(MoveTaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMoveTaskNotFound(MoveTaskNotFoundException exception) {
        ErrorResponse body = ErrorResponse.builder()
                .code("TASK_NOT_FOUND")
                .message(exception.getMessage())
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(TaskTemplateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaskTemplateNotFound(TaskTemplateNotFoundException exception) {
        ErrorResponse body = ErrorResponse.builder()
                .code("TASK_TEMPLATE_NOT_FOUND")
                .message(exception.getMessage())
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
