package com.datamate.storefront.infrastructure.adapter.inbound.rest;

import com.datamate.storefront.application.exception.IdempotencyKeyReusedException;
import com.datamate.storefront.domain.exception.DomainException;
import com.datamate.storefront.domain.exception.OutOfStockException;
import com.datamate.storefront.domain.exception.UnknownProductException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail outOfStock(OutOfStockException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "out-of-stock", e.getMessage());
        problem.setProperty("sku", e.sku());
        return problem;
    }

    @ExceptionHandler
    ProblemDetail unknownProduct(UnknownProductException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "unknown-product", e.getMessage());
        problem.setProperty("sku", e.sku());
        return problem;
    }

    @ExceptionHandler
    ProblemDetail keyReused(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail domainRule(DomainException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "business-rule-violated", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail invalidInput(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", e.getMessage());
    }

    @ExceptionHandler({CannotAcquireLockException.class, DataAccessResourceFailureException.class})
    ResponseEntity<ProblemDetail> busy(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "busy", "Store node is busy, retry with the same Idempotency-Key"));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
