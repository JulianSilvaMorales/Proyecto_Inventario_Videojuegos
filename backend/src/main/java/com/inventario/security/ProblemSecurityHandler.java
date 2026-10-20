package com.inventario.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final MessageSource messages;

    public ProblemSecurityHandler(ObjectMapper objectMapper, MessageSource messages) {
        this.objectMapper = objectMapper;
        this.messages = messages;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        writeProblem(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "error.authentication.title",
                "error.authentication.detail");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        writeProblem(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "error.authorization.title",
                "error.authorization.detail");
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String titleKey,
            String detailKey) throws IOException {
        Locale locale = request.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                messages.getMessage(detailKey, null, locale));
        problem.setType(URI.create("https://inventario.local/problems/" + status.value()));
        problem.setTitle(messages.getMessage(titleKey, null, locale));
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
