package com.tenantmetrics.platform.scoring;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

	static final int DEFAULT_PAGE_SIZE = 50;
	static final int MAX_PAGE_SIZE = 500;

	@GetMapping("/v1/predictions")
	ResponseEntity<?> listPredictions(
			@RequestParam(name = "limit", required = false) Integer limit,
			@RequestParam(name = "cursor", required = false) String cursor,
			HttpServletRequest request) {
		TenantContext tenant = requireTenant(request);
		int pageSize = limit == null ? DEFAULT_PAGE_SIZE : limit;
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			return problem(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
		}
		String afterAccountId;
		try {
			afterAccountId = decodeCursor(cursor);
		}
		catch (IllegalArgumentException ex) {
			return problem(HttpStatus.BAD_REQUEST, "cursor is invalid");
		}
		List<AccountScore> rows = accountScoreStore.listByTenant(tenant.tenantId(), afterAccountId, pageSize + 1);
		boolean hasMore = rows.size() > pageSize;
		if (hasMore) {
			rows = List.copyOf(rows.subList(0, pageSize));
		}
		String nextCursor = hasMore ? encodeCursor(rows.getLast().accountExternalId()) : null;
		return ResponseEntity.ok(new PredictionListResponse(
				rows.stream().map(PredictionController::toResponse).toList(),
				nextCursor));
	}

	private static String encodeCursor(String accountExternalId) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(accountExternalId.getBytes(StandardCharsets.UTF_8));
	}

	private static String decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			byte[] bytes = Base64.getUrlDecoder().decode(cursor);
			String decoded = new String(bytes, StandardCharsets.UTF_8);
			if (!Arrays.equals(bytes, decoded.getBytes(StandardCharsets.UTF_8))
					|| decoded.isBlank()
					|| decoded.length() > 128
					|| decoded.codePoints().anyMatch(Character::isISOControl)) {
				throw new IllegalArgumentException("cursor");
			}
			return decoded;
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("cursor");
		}
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
