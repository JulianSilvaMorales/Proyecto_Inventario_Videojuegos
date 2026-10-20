package com.inventario.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import com.inventario.service.ParametroNoEncontradoException;
import com.inventario.service.ErrorAplicacionException;
import com.inventario.service.UsuarioNoEncontradoException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MessageSource messages;

    public ApiExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "error.validation.title",
                "error.validation.detail",
                request);
        String fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldMessage)
                .distinct()
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
        problem.setProperty("errors", fields);
        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNotFound(HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "error.not-found.title",
                "error.not-found.detail",
                request);
    }

    @ExceptionHandler({UsuarioNoEncontradoException.class, ParametroNoEncontradoException.class})
    ProblemDetail handleDomainNotFound(Exception exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.NOT_FOUND,
                "error.not-found.title",
                "error.not-found.detail",
                request);
        problem.setProperty("causa", exception.getMessage());
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataConflict(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "error.conflict.title",
                "error.conflict.detail",
                request);
    }

    @ExceptionHandler(ErrorAplicacionException.class)
    ProblemDetail handleApplicationError(
            ErrorAplicacionException exception,
            HttpServletRequest request) {
        String titleKey = switch (exception.status()) {
            case BAD_REQUEST -> "error.validation.title";
            case NOT_FOUND -> "error.not-found.title";
            case CONFLICT -> "error.conflict.title";
            default -> "error.internal.title";
        };
        return problem(
                exception.status(),
                titleKey,
                exception.messageKey(),
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(HttpServletRequest request) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "error.authentication.title",
                "error.authentication.detail",
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAuthorization(HttpServletRequest request) {
        return problem(
                HttpStatus.FORBIDDEN,
                "error.authorization.title",
                "error.authorization.detail",
                request);
    }

    @ExceptionHandler(ErrorResponseException.class)
    ProblemDetail handleHttpError(
            ErrorResponseException exception,
            HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getBody().getDetail());
        problem.setTitle(exception.getBody().getTitle());
        problem.setType(exception.getBody().getType());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error(
                "Error interno al procesar {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "error.internal.title",
                "error.internal.detail",
                request);
    }

    private String fieldMessage(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ProblemDetail problem(
            HttpStatus status,
            String titleKey,
            String detailKey,
            HttpServletRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                messages.getMessage(detailKey, null, locale));
        problem.setTitle(messages.getMessage(titleKey, null, locale));
        problem.setType(URI.create("https://inventario.local/problems/" + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
