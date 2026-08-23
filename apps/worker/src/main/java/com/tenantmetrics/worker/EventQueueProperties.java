package com.tenantmetrics.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.events.queue")
class EventQueueProperties {

	private String endpoint = "http://127.0.0.1:4566";
	private String region = "us-east-1";
	private String name = "accepted-events";
	private String accessKey = "test";
	private String secretKey = "test";
	private boolean pollEnabled = true;

	String getEndpoint() {
		return endpoint;
	}

	void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	String getRegion() {
		return region;
	}

	void setRegion(String region) {
		this.region = region;
	}

	String getName() {
		return name;
	}

	void setName(String name) {
		this.name = name;
	}

	String getAccessKey() {
		return accessKey;
	}

	void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	String getSecretKey() {
		return secretKey;
	}

	void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	boolean isPollEnabled() {
		return pollEnabled;
	}

	void setPollEnabled(boolean pollEnabled) {
		this.pollEnabled = pollEnabled;
	}
}
