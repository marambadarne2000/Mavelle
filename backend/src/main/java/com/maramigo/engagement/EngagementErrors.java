package com.maramigo.engagement;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
/** Do not echo private support text in validation responses/logging. @author Maram Abbas */
@RestControllerAdvice(assignableTypes={SupportController.class,NotificationController.class})
public class EngagementErrors {
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 public ResponseEntity<ProblemDetail> status(org.springframework.web.server.ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(ProblemDetail.forStatusAndDetail(e.getStatusCode(),e.getReason()));}
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
 public ResponseEntity<ProblemDetail> invalid(){return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Check the required fields and try again."));}
}
