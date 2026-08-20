package com.learnnotes.common;

import lombok.Getter;

/**
 * 业务异常：携带业务错误码与 HTTP 状态码，由 {@link GlobalExceptionHandler} 统一转为响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BizException(int code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BizException badRequest(String msg) {
        return new BizException(ErrorCode.BAD_REQUEST, 400, msg);
    }

    public static BizException unauthorized(String msg) {
        return new BizException(ErrorCode.UNAUTHORIZED, 401, msg);
    }

    public static BizException notFound(String msg) {
        return new BizException(ErrorCode.NOT_FOUND, 404, msg);
    }

    public static BizException conflict(String msg) {
        return new BizException(ErrorCode.CONFLICT, 409, msg);
    }

    public static BizException locked(String msg) {
        return new BizException(ErrorCode.LOCKED, 423, msg);
    }

    public static BizException internal(String msg) {
        return new BizException(ErrorCode.INTERNAL_ERROR, 500, msg);
    }
}
