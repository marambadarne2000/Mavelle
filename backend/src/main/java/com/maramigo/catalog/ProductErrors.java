package com.maramigo.catalog;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** @author Maram Abbas */
@RestControllerAdvice(assignableTypes=AdminCatalogController.class)
public class ProductErrors {
    @ExceptionHandler({DataIntegrityViolationException.class,ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> conflict() { return problem(HttpStatus.CONFLICT,"SKU already exists or the product changed. Reload and try again."); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> status(ResponseStatusException exception) { return problem(exception.getStatusCode(), exception.getReason()); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid() { return problem(HttpStatus.BAD_REQUEST,"Check all product fields, price, quantities and version. Invalid fields were not saved."); }
    private ResponseEntity<ProblemDetail> problem(HttpStatusCode status,String detail) { return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status,detail)); }
}
