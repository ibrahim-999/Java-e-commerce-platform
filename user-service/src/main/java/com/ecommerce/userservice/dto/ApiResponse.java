package com.ecommerce.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// ApiResponse<T> — a generic wrapper for ALL API responses.
//
// Generics recap:
//   <T> means "this class works with any type".
//   ApiResponse<UserResponse>    → data field is a UserResponse
//   ApiResponse<List<UserResponse>> → data field is a List
//   ApiResponse<Void>            → no data (error responses)
//
// Every response from our API looks like:
// {
//   "success": true,
//   "message": "User created successfully",
//   "data": { ... },
//   "timestamp": "2026-03-08T..."
// }
//
// @JsonInclude(NON_NULL) — excludes null fields from JSON output.
// So error responses won't have a "data" field, and success responses won't have "errors".

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    // Factory methods — cleaner than calling the builder every time.

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
