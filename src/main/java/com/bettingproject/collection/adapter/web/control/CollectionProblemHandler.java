package com.bettingproject.collection.adapter.web.control;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages="com.bettingproject.collection.adapter.web.control")
@Profile("control-api")
public class CollectionProblemHandler {
    @ExceptionHandler(CollectionWebException.class)
    ProblemDetail known(CollectionWebException error,HttpServletRequest request){return problem(error.status(),error.code(),request);}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.HttpMediaTypeNotSupportedException.class})
    ProblemDetail invalid(Exception error,HttpServletRequest request){return problem(400,"INVALID_REQUEST",request);}
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception error,HttpServletRequest request){return problem(500,"INTERNAL_ERROR",request);}
    private ProblemDetail problem(int status,String code,HttpServletRequest request){
        ProblemDetail value=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),"La requête ne peut pas être traitée dans son état actuel.");
        value.setTitle(status==404 ? "Ressource introuvable" : status==400 ? "Requête invalide" : "Traitement indisponible");
        value.setType(URI.create("urn:betting-project:problem:"+code));
        // A fixed route family never reflects user-supplied path segments or query values.
        value.setInstance(URI.create("/internal/collection")); value.setProperty("code",code); return value;
    }
}
