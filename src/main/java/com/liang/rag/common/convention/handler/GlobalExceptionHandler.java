package com.liang.rag.common.convention.handler;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.liang.rag.common.convention.errorcode.BaseErrorCode;
import com.liang.rag.common.convention.exception.AbstractException;
import com.liang.rag.common.convention.result.Result;
import com.liang.rag.common.convention.result.Results;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.Optional;

/**
 * 全局异常处理器
 * 拦截指定异常并通过优雅构建方式返回前端信息
 *
 * @author liang
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截参数验证异常
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    /**
     * 拦截应用内抛出的异常
     */
    @ExceptionHandler(value = {AbstractException.class})
    public Result<Void> abstractException(HttpServletRequest request, AbstractException ex) {
        if (ex.getCause() != null) {
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex, ex.getCause());
            return Results.failure(ex);
        }
        StringBuilder stackTraceBuilder = new StringBuilder();
        stackTraceBuilder.append(ex.getClass().getName()).append(": ").append(ex.getErrorMessage()).append("\n");
        StackTraceElement[] stackTrace = ex.getStackTrace();
        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            stackTraceBuilder.append("\tat ").append(stackTrace[i]).append("\n");
        }
        log.error("[{}] {} [ex] {} \n\n{}", request.getMethod(), request.getRequestURL().toString(), ex, stackTraceBuilder);
        return Results.failure(ex);
    }

    /**
     * 拦截客户端断开连接导致的 IO 异常
     * <p>
     * 在 SSE 流式响应过程中，客户端（浏览器）主动断开连接时，Tomcat 会抛出 IOException。
     * 此时响应已提交（Content-Type: text/event-stream），无法再返回 JSON 格式的 Result 对象。
     * 因此仅记录 WARN 日志，不做额外处理。
     * </p>
     */
    @ExceptionHandler(value = IOException.class)
    public void ioExceptionHandler(HttpServletRequest request, HttpServletResponse response, IOException ex) {
        String message = Optional.ofNullable(ex.getMessage()).orElse("");
        // 常见的客户端断开连接错误信息（中文/英文）
        boolean isClientDisconnect = message.contains("中止") || message.contains("Broken pipe")
                || message.contains("Connection reset") || message.contains("connection was aborted");
        if (isClientDisconnect) {
            log.warn("[{}] {} 客户端断开连接: {}", request.getMethod(), getUrl(request), message);
        } else if (response.isCommitted()) {
            // 响应已提交（如 SSE 流已开始），无法返回 JSON，仅记录日志
            log.error("[{}] {} IO异常（响应已提交，无法返回错误信息）", request.getMethod(), getUrl(request), ex);
        } else {
            // 响应未提交的 IO 异常，按正常错误处理
            log.error("[{}] {} IO异常", request.getMethod(), getUrl(request), ex);
        }
        // 由于 SSE 场景下 response 大概率已 committed，这里不返回任何内容
        // Spring 会自动关闭连接
    }

    /**
     * 拦截未捕获异常
     */
    @ExceptionHandler(value = Throwable.class)
    public Result<Void> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        return Results.failure();
    }

    private String getUrl(HttpServletRequest request) {
        if (StrUtil.isBlank(request.getQueryString())) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }
}
