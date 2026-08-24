package com.tenantmetrics.platform.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.security.session")
public record BrowserSessionProperties(Duration absoluteLifetime) {

	public BrowserSessionProperties {
		absoluteLifetime = absoluteLifetime == null ? Duration.ofHours(8) : absoluteLifetime;
		if (absoluteLifetime.isZero() || absoluteLifetime.isNegative()) {
			throw new IllegalArgumentException("Absolute session lifetime must be positive");
		}
	}
}
