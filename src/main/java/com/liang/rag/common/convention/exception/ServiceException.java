package com.liang.rag.common.convention.exception;

import com.liang.rag.common.convention.errorcode.BaseErrorCode;
import com.liang.rag.common.convention.errorcode.IErrorCode;

import java.util.Optional;

/**
 * 服务端运行异常
 * 请求运行过程中出现的不符合业务预期的异常
 *
 * @author liang
 */
public class ServiceException extends AbstractException {

    public ServiceException(String message) {
        this(message, null, BaseErrorCode.SERVICE_ERROR);
    }

    public ServiceException(IErrorCode errorCode) {
        this(null, errorCode);
    }

    public ServiceException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ServiceException(String message, Throwable throwable, IErrorCode errorCode) {
        super(Optional.ofNullable(message).orElse(errorCode.message()), throwable, errorCode);
    }

    @Override
    public String toString() {
        return "ServiceException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}
