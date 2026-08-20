package com.learnnotes.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体 {@code {code, msg, data}}（规格 §5）。
 * code=0 表示成功；非 0 用于前端区分业务分支。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {

    public static final int OK = 0;

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok(T data) {
        return new R<>(OK, "ok", data);
    }

    public static R<Void> ok() {
        return new R<>(OK, "ok", null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }
}
