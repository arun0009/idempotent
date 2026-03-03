package io.github.arun0009.idempotent.core;

import io.github.arun0009.idempotent.core.exception.IdempotentWaitExhaustedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class IdempotentRestControllerAdvice {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFoundException(NotFoundException ex) {
        var pb = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pb.setProperty("id", ex.id);
        return pb;
    }

    @ExceptionHandler(IdempotentWaitExhaustedException.class)
    ProblemDetail handleIdempotentWaitExhaustedException(IdempotentWaitExhaustedException ex) {
        var pb = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        pb.setProperty("code", "idempotent-wait-exhausted");
        return pb;
    }
}
