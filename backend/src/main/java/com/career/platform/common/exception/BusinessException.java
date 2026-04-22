package com.career.platform.common.exception;



/**
 * 自定义业务异常
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final String errorCode;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
        this.errorCode = null;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null;
    }

    public BusinessException(int code, String message, String errorCode) {
        super(message);
        this.code = code;
        this.errorCode = errorCode;
    }

    public int getCode() { return code; }
    public String getErrorCode() { return errorCode; }

    public static BusinessException of(String message) {
        return new BusinessException(message);
    }

    public static BusinessException of(int code, String message) {
        return new BusinessException(code, message);
    }

    public static BusinessException of(int code, String message, String errorCode) {
        return new BusinessException(code, message, errorCode);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }
}
