package com.tenantmetrics.platform.tenancy;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

final class TenantHeaderStrippingRequest extends HttpServletRequestWrapper {

	TenantHeaderStrippingRequest(HttpServletRequest request) {
		super(request);
	}

	static boolean isClientTenantHeader(String name) {
		if (name == null) {
			return false;
		}
		String normalized = name.toLowerCase(Locale.ROOT).replace("_", "-");
		return "x-tenant-id".equals(normalized) || "tenant-id".equals(normalized);
	}

	@Override
	public String getHeader(String name) {
		return isClientTenantHeader(name) ? null : super.getHeader(name);
	}

	@Override
	public Enumeration<String> getHeaders(String name) {
		return isClientTenantHeader(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
	}

	@Override
	public Enumeration<String> getHeaderNames() {
		List<String> names = Collections.list(super.getHeaderNames()).stream()
				.filter(name -> !isClientTenantHeader(name))
				.toList();
		return Collections.enumeration(names);
	}
}
