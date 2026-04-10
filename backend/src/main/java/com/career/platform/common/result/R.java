package com.career.platform.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一 API 返回结构
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private Long total;      // 分页时总数
    private Integer page;    // 当前页
    private Integer pageSize;
    private LocalDateTime timestamp;

    private R() {
        this.timestamp = LocalDateTime.now();
    }

    // ── 成功 ──

    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.code = 200;
        r.message = "success";
        return r;
    }

    public static <T> R<T> ok(T data) {
        R<T> r = ok();
        r.data = data;
        return r;
    }

    public static <T> R<T> ok(String message, T data) {
        R<T> r = ok();
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> R<java.util.List<T>> page(java.util.List<T> records, long total, int page, int pageSize) {
        R<java.util.List<T>> r = ok();
        r.data = records;
        r.total = total;
        r.page = page;
        r.pageSize = pageSize;
        return r;
    }

    // ── 失败 ──

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(500, message);
    }

    public static <T> R<T> unauthorized(String message) {
        return fail(401, message);
    }

    public static <T> R<T> forbidden(String message) {
        return fail(403, message);
    }

    public static <T> R<T> notFound(String message) {
        return fail(404, message);
    }

    public static <T> R<T> badRequest(String message) {
        return fail(400, message);
    }
}
