package com.tenantmetrics.platform.tenancy;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class TenantContextController {

	@GetMapping("/v1/tenant-context")
	public Map<String, String> current(HttpServletRequest request) {
		Object attribute = request.getAttribute(TenantResolutionFilter.TENANT_CONTEXT_ATTRIBUTE);
		if (!(attribute instanceof TenantContext tenantContext)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return Map.of("tenantId", tenantContext.tenantId());
	}
}
