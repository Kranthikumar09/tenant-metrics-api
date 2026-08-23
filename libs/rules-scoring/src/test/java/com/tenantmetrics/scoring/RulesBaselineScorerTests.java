package com.tenantmetrics.scoring;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RulesBaselineScorerTests {

	private static final Instant AS_OF = Instant.parse("2026-08-23T12:00:00Z");

	private final RulesBaselineScorer scorer = new RulesBaselineScorer();

	@Test
	void noEventsAreInsufficientAndNeverClaimProbability() {
		AccountScore score = scorer.score("tenant-a", "acct-1", List.of(), AS_OF);

		assertThat(score.eligibility()).isEqualTo("INSUFFICIENT_DATA");
		assertThat(score.healthScore()).isNull();
		assertThat(score.riskBand()).isNull();
		assertThat(score.riskProbability()).isNull();
		assertThat(score.scoreVersion()).isEqualTo("RULES_BASELINE");
		assertThat(score.featureVersion()).isEqualTo("rules-features-v1");
	}

	@Test
	void engagementTodayIsLowRiskWithoutAChurnLabel() {
		AccountScore score = scorer.score("tenant-a", "acct-1", List.of(
				event("tenant-a", "acct-1", "auth.login", "2026-08-23T11:00:00Z")), AS_OF);

		assertThat(score.eligibility()).isEqualTo("SCORED");
		assertThat(score.healthScore()).isEqualTo(70);
		assertThat(score.riskBand()).isEqualTo("LOW");
		assertThat(score.riskProbability()).isNull();
		assertThat(score.scoreVersion()).isEqualTo("RULES_BASELINE");
		assertThat(score.driversJson()).contains("engagement_event_count");
		assertThat(score.driversJson()).doesNotContain("churn");
	}

	@Test
	void twoDistressEventsAreHighRiskWithoutInventingALabel() {
		AccountScore score = scorer.score("tenant-a", "acct-1", List.of(
				event("tenant-a", "acct-1", "billing.payment_failed", "2026-08-23T10:00:00Z"),
				event("tenant-a", "acct-1", "billing.payment_failed", "2026-08-23T11:00:00Z")),
				AS_OF);

		assertThat(score.healthScore()).isEqualTo(35);
		assertThat(score.riskBand()).isEqualTo("HIGH");
		assertThat(score.riskProbability()).isNull();
		assertThat(score.driversJson()).contains("distress_event_count");
	}

	@Test
	void boundTenantIgnoresOtherTenantEvents() {
		AccountScore score = scorer.score("tenant-a", "acct-shared", List.of(
				event("tenant-a", "acct-shared", "billing.payment_failed", "2026-08-23T11:00:00Z"),
				event("tenant-b", "acct-shared", "auth.login", "2026-08-23T11:00:00Z"),
				event("tenant-a", "acct-other", "auth.login", "2026-08-23T11:00:00Z")),
				AS_OF);

		assertThat(score.tenantId()).isEqualTo("tenant-a");
		assertThat(score.accountExternalId()).isEqualTo("acct-shared");
		assertThat(score.healthScore()).isEqualTo(45);
		assertThat(score.riskBand()).isEqualTo("MEDIUM");
	}

	@Test
	void sameInputsAreDeterministic() {
		List<AccountEvent> events = List.of(
				event("tenant-a", "acct-1", "billing.invoice_paid", "2026-08-23T11:00:00Z"));

		AccountScore first = scorer.score("tenant-a", "acct-1", events, AS_OF);
		AccountScore second = scorer.score("tenant-a", "acct-1", events, AS_OF);

		assertThat(first.healthScore()).isEqualTo(60);
		assertThat(first.riskBand()).isEqualTo("MEDIUM");
		assertThat(second.healthScore()).isEqualTo(first.healthScore());
		assertThat(second.driversJson()).isEqualTo(first.driversJson());
		assertThat(second.scoredAt()).isEqualTo(first.scoredAt());
	}

	private static AccountEvent event(String tenantId, String accountId, String eventType, String occurredAt) {
		return new AccountEvent(tenantId, accountId, eventType, Instant.parse(occurredAt));
	}
}
