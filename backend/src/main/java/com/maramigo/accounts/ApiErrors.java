package com.maramigo.accounts;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;

/** @author Maram Abbas */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class ApiErrors {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> duplicateAccount() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "An account with this email already exists."));
    }
}
