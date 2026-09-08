package com.gongguhan.global.error;

import com.gongguhan.global.common.ApiError;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        ApiError error = new ApiError(errorCode.name(), e.getMessage(), List.of());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(error, TraceIdUtils.from(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError error = new ApiError(
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                fieldErrors);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(error, TraceIdUtils.from(request)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
            NoResourceFoundException e, HttpServletRequest request) {
        ApiError error = new ApiError(
                ErrorCode.RESOURCE_NOT_FOUND.name(),
                ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage(),
                List.of());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ApiResponse.error(error, TraceIdUtils.from(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception e, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), e);
        ApiError error = new ApiError(
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage(),
                List.of());
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(error, TraceIdUtils.from(request)));
    }
}
