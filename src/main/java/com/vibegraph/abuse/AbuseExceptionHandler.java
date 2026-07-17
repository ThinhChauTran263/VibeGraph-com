package com.vibegraph.abuse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;
import com.vibegraph.common.exception.ConcurrentImportLimitException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AbuseExceptionHandler {

    @ExceptionHandler(ConcurrentImportLimitException.class)
    public ResponseEntity<ApiResponse<Void>> concurrentImport(ConcurrentImportLimitException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                ErrorResponse.builder().code(ex.getCode()).message(ex.getMessage()).build()));
    }
}
