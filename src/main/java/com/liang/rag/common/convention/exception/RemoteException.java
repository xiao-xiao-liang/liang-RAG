package com.liang.rag.common.convention.exception;

import com.liang.rag.common.convention.errorcode.BaseErrorCode;
import com.liang.rag.common.convention.errorcode.IErrorCode;

/**
 * 远程服务调用异常
 * 比如调用 MinerU 解析服务失败，向上抛出的异常应该是远程服务调用异常
 *
 * @author liang
 */
public class RemoteException extends AbstractException {

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public RemoteException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "RemoteException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}
