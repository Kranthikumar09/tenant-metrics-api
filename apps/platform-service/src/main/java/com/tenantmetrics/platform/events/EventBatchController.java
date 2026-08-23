package com.tenantmetrics.platform.events;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tenantmetrics.platform.tenancy.TenantContext;
import com.tenantmetrics.platform.tenancy.TenantResolutionFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
class EventBatchController {

	private final EventBatchService eventBatchService;

	EventBatchController(EventBatchService eventBatchService) {
		this.eventBatchService = eventBatchService;
	}

	@PostMapping(path = "/v1/events:batch", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<?> ingest(
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody(required = false) EventBatchRequest request,
			HttpServletRequest httpRequest) {
		TenantContext tenant = requireTenant(httpRequest);
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
			return problem(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
		}
		if (request == null || request.events() == null || request.events().isEmpty()) {
			return problem(HttpStatus.BAD_REQUEST, "events must contain at least one item");
		}
		if (request.events().size() > EventBatchService.MAX_BATCH_SIZE) {
			return problem(HttpStatus.BAD_REQUEST, "batch exceeds 500 events");
		}
		return ResponseEntity.accepted().body(eventBatchService.ingest(tenant, idempotencyKey, request));
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
