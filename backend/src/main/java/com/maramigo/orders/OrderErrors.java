package com.maramigo.orders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.ConcurrencyFailureException;
/** @author Maram Abbas */
@RestControllerAdvice(assignableTypes={OrderController.class,AdminOrderController.class,DashboardController.class})
public class OrderErrors {
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<ProblemDetail> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(ProblemDetail.forStatusAndDetail(e.getStatusCode(),e.getReason()));}
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class}) public ResponseEntity<ProblemDetail> invalid(){return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Check the required fields and try again."));}
    @ExceptionHandler(ConcurrencyFailureException.class) public ResponseEntity<ProblemDetail> busy(){return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Inventory is busy. Please try again."));}
}
