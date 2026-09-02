package com.bettingproject.catalog.adapter.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.bettingproject.catalog.adapter.web")
@Profile("control-api")
public class CatalogProblemHandler {

    @ExceptionHandler(CatalogWebException.class)
    ProblemDetail handleCatalogFailure(
            CatalogWebException exception,
            HttpServletRequest request) {
        return problem(exception.status(), exception.code(), request);
    }

    @ExceptionHandler({
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpMessageNotReadableException.class
    })
    ProblemDetail handleInvalidHttpRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpectedFailure(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", request);
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
            case BAD_REQUEST -> "Requête invalide";
            case NOT_FOUND -> "Ressource introuvable";
            case CONFLICT -> "Conflit";
            case UNPROCESSABLE_ENTITY -> "Règle métier non satisfaite";
            case SERVICE_UNAVAILABLE -> "Service temporairement indisponible";
            default -> "Erreur interne";
        };
    }

    private String detail(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "La requête ne respecte pas le contrat attendu.";
            case NOT_FOUND -> "La ressource demandée n'existe pas.";
            case CONFLICT -> "La requête entre en conflit avec l'état courant.";
            case UNPROCESSABLE_ENTITY -> "La commande est interdite par une règle métier.";
            case SERVICE_UNAVAILABLE -> "La configuration nécessaire à la mutation est indisponible.";
            default -> "Une erreur interne empêche le traitement de la requête.";
        };
    }
}
