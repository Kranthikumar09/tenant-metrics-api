package com.tenantmetrics.platform.tenancy;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.security")
public class ApiKeyProperties {

	private Map<String, String> apiKeyHashes = new HashMap<>();

	public Map<String, String> getApiKeyHashes() {
		return apiKeyHashes;
	}

	public void setApiKeyHashes(Map<String, String> apiKeyHashes) {
		this.apiKeyHashes = apiKeyHashes == null ? new HashMap<>() : apiKeyHashes;
	}
}
