package com.tenantmetrics.worker.scoring;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountScoreRefresher {

	private final ObjectProvider<JdbcTemplate> jdbcTemplates;
	private final RulesBaselineScorer rulesBaselineScorer;

	AccountScoreRefresher(ObjectProvider<JdbcTemplate> jdbcTemplates, RulesBaselineScorer rulesBaselineScorer) {
		this.jdbcTemplates = jdbcTemplates;
		this.rulesBaselineScorer = rulesBaselineScorer;
	}

	public void refresh(String tenantId, String eventId) {
		JdbcTemplate jdbcTemplate = jdbcTemplates.getIfAvailable();
		if (jdbcTemplate == null) {
			return;
		}
		AccountScoreStore store = new AccountScoreStore(jdbcTemplate);
		Optional<AccountEvent> event = store.findEvent(tenantId, eventId);
		if (event.isEmpty()) {
			return;
		}
		String accountExternalId = event.get().accountExternalId();
		List<AccountEvent> events = store.loadEvents(tenantId, accountExternalId);
		store.upsert(rulesBaselineScorer.score(tenantId, accountExternalId, events, Instant.now()));
	}
}
