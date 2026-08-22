package com.tenantmetrics.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformServicePersistenceTests extends AbstractPlatformPostgresTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoadsAgainstPostgres() {
		assertThat(POSTGRES.isRunning()).isTrue();
	}

	@Test
	void flywayBootstrapRowIsQueryAble() {
		Integer id = jdbcTemplate.queryForObject(
				"SELECT id FROM platform_bootstrap WHERE id = 1",
				Integer.class);
		assertThat(id).isEqualTo(1);
	}
}
