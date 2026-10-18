package ru.example.prodbackend.observability;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import ru.example.prodbackend.exception.dto.ErrorResponse;

@RestControllerAdvice
public class ErrorObservabilityAdvice implements ResponseBodyAdvice<Object> {
    public static final String ERROR_CODE = ErrorObservabilityAdvice.class.getName() + ".code";
    public static final String ERROR_TRACE_ID = ErrorObservabilityAdvice.class.getName() + ".traceId";

    public static void capture(HttpServletRequest request, ErrorResponse body) {
        request.setAttribute(ERROR_CODE, body.getCode());
        request.setAttribute(ERROR_TRACE_ID, body.getTraceId());
    }

    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ErrorResponse error && request instanceof ServletServerHttpRequest servletRequest) {
            capture(servletRequest.getServletRequest(), error);
        }
        return body;
    }
}
