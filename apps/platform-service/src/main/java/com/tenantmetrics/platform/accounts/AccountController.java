package com.tenantmetrics.platform.accounts;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tenantmetrics.platform.tenancy.TenantContext;
import com.tenantmetrics.platform.tenancy.TenantResolutionFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
class AccountController {

	private static final Set<String> REQUEST_FIELDS = Set.of("account_external_id");

	private final AccountStore accountStore;

	AccountController(AccountStore accountStore) {
		this.accountStore = accountStore;
	}

	@PostMapping(path = "/v1/accounts:upsert", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<?> upsert(
			@RequestBody(required = false) Map<String, Object> request,
			HttpServletRequest httpRequest) {
		TenantContext tenant = requireTenant(httpRequest);
		if (request == null || !request.keySet().equals(REQUEST_FIELDS)) {
			return problem(HttpStatus.BAD_REQUEST, "request must contain only account_external_id");
		}
		Object rawAccountExternalId = request.get("account_external_id");
		if (!(rawAccountExternalId instanceof String accountExternalId)
				|| accountExternalId.isBlank()
				|| accountExternalId.length() > 128) {
			return problem(HttpStatus.BAD_REQUEST, "account_external_id must be a non-blank string of at most 128 characters");
		}

		boolean created = accountStore.insertIfAbsent(tenant.tenantId(), accountExternalId);
		return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK)
				.body(new AccountUpsertResponse(accountExternalId));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> malformedJson(HttpMessageNotReadableException ignored) {
		return problem(HttpStatus.BAD_REQUEST, "request body must be valid JSON");
	}

	private static TenantContext requireTenant(HttpServletRequest request) {
		Object attribute = request.getAttribute(TenantResolutionFilter.TENANT_CONTEXT_ATTRIBUTE);
		if (!(attribute instanceof TenantContext tenantContext)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return tenantContext;
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
		return ResponseEntity.status(status)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(problemDetail);
	}
}

record AccountUpsertResponse(@JsonProperty("account_external_id") String accountExternalId) {
}
