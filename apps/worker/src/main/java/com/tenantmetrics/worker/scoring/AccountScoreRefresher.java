package com.tenantmetrics.worker.scoring;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.tenantmetrics.scoring.AccountEvent;
import com.tenantmetrics.scoring.RulesBaselineScorer;

@Component
public class AccountScoreRefresher {

	private final ObjectProvider<JdbcTemplate> jdbcTemplates;
	private final RulesBaselineScorer rulesBaselineScorer;

	AccountScoreRefresher(ObjectProvider<JdbcTemplate> jdbcTemplates, RulesBaselineScorer rulesBaselineScorer) {
		this.jdbcTemplates = jdbcTemplates;
		this.rulesBaselineScorer = rulesBaselineScorer;
	}

	public boolean refresh(String tenantId, String eventId) {
		JdbcTemplate jdbcTemplate = jdbcTemplates.getIfAvailable();
		if (jdbcTemplate == null) {
			return false;
		}
		AccountScoreStore store = new AccountScoreStore(jdbcTemplate);
		Optional<AccountEvent> event = store.findEvent(tenantId, eventId);
		if (event.isEmpty()) {
			return false;
		}
		String accountExternalId = event.get().accountExternalId();
		List<AccountEvent> events = store.loadEvents(tenantId, accountExternalId);
		store.upsert(rulesBaselineScorer.score(tenantId, accountExternalId, events, Instant.now()));
		return true;
	}
}
