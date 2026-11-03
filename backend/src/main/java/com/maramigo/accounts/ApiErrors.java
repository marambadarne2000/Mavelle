package com.maramigo.accounts;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;

/** @author Maram Abbas */
@RestControllerAdvice(assignableTypes = {AuthController.class, StaffRegistration.class, OwnerController.class, AccountLifecycleController.class, MfaController.class, AccountSettings.class, com.maramigo.catalog.MediaController.class})
public class ApiErrors {
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid() {
        // Do not let framework validation logs print rejected passwords, email addresses or tokens.
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Check the required fields and try again."));
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> status(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(ProblemDetail.forStatusAndDetail(exception.getStatusCode(),exception.getReason()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> duplicateAccount() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "An account with this email already exists."));
    }
}
