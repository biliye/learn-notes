package com.learnnotes.common;

/**
 * 业务错误码。HTTP 状态码语义见规格 §5：
 * 200 成功 / 400 参数错误 / 401 未登录或 token 失效 / 404 不存在 / 409 冲突 / 500 服务端错误。
 */
public final class ErrorCode {

    /** 参数错误 */
    public static final int BAD_REQUEST = 40000;
    /** 未登录 / token 失效 */
    public static final int UNAUTHORIZED = 40100;
    /** 资源不存在 */
    public static final int NOT_FOUND = 40400;
    /** 冲突（非空删除、slug 冲突且 onConflict=FAIL 等） */
    public static final int CONFLICT = 40900;
    /** 账号锁定 */
    public static final int LOCKED = 42300;
    /** 服务端内部错误 */
    public static final int INTERNAL_ERROR = 50000;

    private ErrorCode() {
    }
}
