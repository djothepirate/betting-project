package com.bettingproject.collection.adapter.web.j7;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.bettingproject.collection.adapter.web.j7")
@Profile("control-api")
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public class J7ImportProblemHandler {

    @ExceptionHandler(J7ImportProtocolException.class)
    ProblemDetail handleProtocolFailure(
            J7ImportProtocolException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, exception.error().name(), request);
    }

    @ExceptionHandler(J7ImportWebException.class)
    ProblemDetail handleWebFailure(
            J7ImportWebException exception,
            HttpServletRequest request) {
        return problem(exception.status(), exception.code(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleInvalidRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_J7_IMPORT", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpectedFailure(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "J7_IMPORT_INTERNAL_ERROR", request);
    }

    private ProblemDetail problem(
            HttpStatus status,
            String code,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                detail(status));
        problem.setType(URI.create("urn:betting-project:problem:" + code));
        problem.setTitle(title(status));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }

    private String title(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Invalid J7 import request";
            case FORBIDDEN -> "J7 sender identity refused";
            case CONFLICT -> "J7 import conflict";
            default -> "J7 import failure";
        };
    }

    private String detail(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request does not satisfy the strict J7 import contract.";
            case FORBIDDEN -> "The authenticated client is not authorized for this receiver.";
            case CONFLICT -> "The import identity already exists with different content.";
            default -> "The import did not produce a durable acknowledgement.";
        };
    }
}
