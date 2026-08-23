package com.tenantmetrics.platform.scoring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tenantmetrics.scoring.RulesBaselineScorer;

@Configuration
class RulesScoringConfig {

	@Bean
	RulesBaselineScorer rulesBaselineScorer() {
		return new RulesBaselineScorer();
	}
}
