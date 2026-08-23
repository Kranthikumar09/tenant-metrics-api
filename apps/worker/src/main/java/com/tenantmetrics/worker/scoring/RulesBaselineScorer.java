package com.tenantmetrics.worker.scoring;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
class RulesBaselineScorer {

	static final String SCORE_VERSION = "RULES_BASELINE";
	static final String FEATURE_VERSION = "rules-features-v1";
	static final int OBSERVATION_WINDOW_DAYS = 60;

	private static final Set<String> DISTRESS_TYPES = Set.of(
			"billing.payment_failed",
			"billing.cancellation_requested",
			"support.ticket_opened");
	private static final Set<String> ENGAGEMENT_TYPES = Set.of(
			"auth.login",
			"product.feature_used",
			"session.started");

	// Tenant id must come from a verified worker tag, never a client header.
	// risk_probability stays null. RULES_BASELINE never claims a learned churn probability or label.
	AccountScore score(String tenantId, String accountExternalId, List<AccountEvent> events, Instant asOf) {
		List<AccountEvent> scoped = inWindow(tenantId, accountExternalId, events, asOf);
		if (scoped.isEmpty()) {
			return new AccountScore(
					tenantId,
					accountExternalId,
					"INSUFFICIENT_DATA",
					null,
					null,
					null,
					SCORE_VERSION,
					FEATURE_VERSION,
					"[]",
					asOf,
					null);
		}

		int eventCount = scoped.size();
		Set<String> types = new LinkedHashSet<>();
		int distressCount = 0;
		int engagementCount = 0;
		Instant latest = scoped.getFirst().occurredAt();
		for (AccountEvent event : scoped) {
			types.add(event.eventType());
			if (DISTRESS_TYPES.contains(event.eventType())) {
				distressCount++;
			}
			if (ENGAGEMENT_TYPES.contains(event.eventType())) {
				engagementCount++;
			}
			if (event.occurredAt().isAfter(latest)) {
				latest = event.occurredAt();
			}
		}
		int distinctTypes = types.size();
		int daysSinceLast = (int) Math.max(0, ChronoUnit.DAYS.between(latest, asOf));
		int freshnessSeconds = (int) Math.max(0, ChronoUnit.SECONDS.between(latest, asOf));

		int eventCountContribution = Math.min(30, eventCount * 5);
		int distinctContribution = Math.min(15, distinctTypes * 5);
		int engagementContribution = Math.min(20, engagementCount * 10);
		int distressContribution = -Math.min(45, distressCount * 15);
		int recencyContribution = -Math.min(30, daysSinceLast * 2);
		int health = clamp(50 + eventCountContribution + distinctContribution + engagementContribution
				+ distressContribution + recencyContribution);

		return new AccountScore(
				tenantId,
				accountExternalId,
				"SCORED",
				health,
				riskBand(health),
				null,
				SCORE_VERSION,
				FEATURE_VERSION,
				driversJson(List.of(
						driver("event_count", eventCount, eventCountContribution),
						driver("distinct_event_types", distinctTypes, distinctContribution),
						driver("engagement_event_count", engagementCount, engagementContribution),
						driver("distress_event_count", distressCount, distressContribution),
						driver("days_since_last_event", daysSinceLast, recencyContribution))),
				asOf,
				freshnessSeconds);
	}

	private static List<AccountEvent> inWindow(
			String tenantId,
			String accountExternalId,
			List<AccountEvent> events,
			Instant asOf) {
		Instant windowStart = asOf.minus(OBSERVATION_WINDOW_DAYS, ChronoUnit.DAYS);
		List<AccountEvent> scoped = new ArrayList<>();
		if (events == null) {
			return scoped;
		}
		for (AccountEvent event : events) {
			if (event == null || event.occurredAt() == null || event.eventType() == null) {
				continue;
			}
			if (!tenantId.equals(event.tenantId()) || !accountExternalId.equals(event.accountExternalId())) {
				continue;
			}
			if (event.occurredAt().isBefore(windowStart) || event.occurredAt().isAfter(asOf)) {
				continue;
			}
			scoped.add(event);
		}
		return scoped;
	}

	private static String riskBand(int health) {
		if (health >= 70) {
			return "LOW";
		}
		if (health >= 40) {
			return "MEDIUM";
		}
		return "HIGH";
	}

	private static int clamp(int health) {
		return Math.max(0, Math.min(100, health));
	}

	private static Driver driver(String name, int value, int contribution) {
		return new Driver(name, value, contribution);
	}

	private static String driversJson(List<Driver> drivers) {
		List<Driver> ranked = drivers.stream()
				.filter(driver -> driver.contribution() != 0)
				.sorted(Comparator.comparingInt((Driver driver) -> Math.abs(driver.contribution())).reversed()
						.thenComparing(Driver::name))
				.limit(3)
				.toList();
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < ranked.size(); i++) {
			if (i > 0) {
				json.append(',');
			}
			Driver driver = ranked.get(i);
			json.append("{\"name\":\"")
					.append(driver.name())
					.append("\",\"value\":")
					.append(driver.value())
					.append(",\"contribution\":")
					.append(driver.contribution())
					.append('}');
		}
		return json.append(']').toString();
	}

	private record Driver(String name, int value, int contribution) {
	}
}
