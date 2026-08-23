package com.tenantmetrics.platform.scoring;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tenantmetrics.platform.tenancy.TenantContext;
import com.tenantmetrics.platform.tenancy.TenantResolutionFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
class PredictionController {

	private final AccountScoreStore accountScoreStore;

	PredictionController(AccountScoreStore accountScoreStore) {
		this.accountScoreStore = accountScoreStore;
	}

	@GetMapping("/v1/accounts/{accountExternalId}/prediction")
	ResponseEntity<?> getPrediction(@PathVariable String accountExternalId, HttpServletRequest request) {
		TenantContext tenant = requireTenant(request);
		return accountScoreStore.find(tenant.tenantId(), accountExternalId)
				.<ResponseEntity<?>>map(score -> ResponseEntity.ok(toResponse(score)))
				.orElseGet(() -> problem(HttpStatus.NOT_FOUND, "prediction not found"));
	}

	@GetMapping("/v1/predictions")
	PredictionListResponse listPredictions(HttpServletRequest request) {
		TenantContext tenant = requireTenant(request);
		return new PredictionListResponse(accountScoreStore.listByTenant(tenant.tenantId()).stream()
				.map(PredictionController::toResponse)
				.toList());
	}

	private static PredictionResponse toResponse(AccountScore score) {
		String drivers = score.driversJson() == null || score.driversJson().isBlank() ? "[]" : score.driversJson();
		return new PredictionResponse(
				score.accountExternalId(),
				score.eligibility(),
				score.healthScore(),
				score.riskBand(),
				score.riskProbability(),
				score.scoreVersion(),
				score.featureVersion(),
				drivers,
				score.scoredAt(),
				score.freshnessSeconds(),
				"none");
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
