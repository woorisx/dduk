package com.dduk.dto.common;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiResponse<T> {
    private String status;
    private T data;
    private String message;
    private String code;
    private Object errors;

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status("success")
                .data(data)
                .message(message)
                .build();
    }

    public static ApiResponse<Void> error(String message, String code) {
        return error(message, code, null);
    }

    public static ApiResponse<Void> error(String message, String code, Object errors) {
        return ApiResponse.<Void>builder()
                .status("error")
                .message(message)
                .code(code)
                .errors(errors)
                .build();
    }
}
