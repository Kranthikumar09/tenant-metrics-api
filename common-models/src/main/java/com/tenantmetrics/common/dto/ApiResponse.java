package com.tenantmetrics.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Generic envelope for API responses shared between services.
 *
 * @param <T> payload type carried on success
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
		boolean success,
		T data,
		String error,
		Instant timestamp
) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null, Instant.now());
	}

	public static <T> ApiResponse<T> failure(String error) {
		return new ApiResponse<>(false, null, error, Instant.now());
	}
}
