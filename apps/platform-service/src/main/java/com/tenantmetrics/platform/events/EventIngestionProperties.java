package com.tenantmetrics.platform.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("platform.events")
public record EventIngestionProperties(DataSize maxRequestSize) {

	private static final DataSize DEFAULT_MAX_REQUEST_SIZE = DataSize.ofMegabytes(1);

	public EventIngestionProperties {
		maxRequestSize = maxRequestSize == null ? DEFAULT_MAX_REQUEST_SIZE : maxRequestSize;
		long bytes = maxRequestSize.toBytes();
		if (bytes <= 0 || bytes >= Integer.MAX_VALUE) {
			throw new IllegalArgumentException(
					"Event request size must be between 1 byte and 2 GiB");
		}
	}

	int maxRequestBytes() {
		return Math.toIntExact(maxRequestSize.toBytes());
	}
}
