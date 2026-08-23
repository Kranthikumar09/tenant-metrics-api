package com.tenantmetrics.platform.scoring;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tenantmetrics.scoring.AccountEvent;
import com.tenantmetrics.scoring.RulesBaselineScorer;

@Service
public class AccountScoreService {

	private final AccountScoreStore accountScoreStore;
	private final RulesBaselineScorer rulesBaselineScorer;

	AccountScoreService(AccountScoreStore accountScoreStore, RulesBaselineScorer rulesBaselineScorer) {
		this.accountScoreStore = accountScoreStore;
		this.rulesBaselineScorer = rulesBaselineScorer;
	}

	public void refresh(String tenantId, String accountExternalId) {
		List<AccountEvent> events = accountScoreStore.loadEvents(tenantId, accountExternalId);
		accountScoreStore.upsert(rulesBaselineScorer.score(tenantId, accountExternalId, events, Instant.now()));
	}
}
