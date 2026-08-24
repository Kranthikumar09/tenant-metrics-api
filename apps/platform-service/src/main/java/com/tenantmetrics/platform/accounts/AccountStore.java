package com.tenantmetrics.platform.accounts;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AccountStore {

	private final JdbcTemplate jdbcTemplate;

	AccountStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	boolean insertIfAbsent(String tenantId, String accountExternalId) {
		int inserted = jdbcTemplate.update(
				"""
						INSERT INTO accounts (tenant_id, account_external_id)
						VALUES (?, ?)
						ON CONFLICT (tenant_id, account_external_id) DO NOTHING
						""",
				tenantId,
				accountExternalId);
		return inserted == 1;
	}
}
