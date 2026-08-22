package com.tenantmetrics.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class PlatformServicePersistenceTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoadsAgainstPostgres() {
		assertThat(postgres.isRunning()).isTrue();
	}

	@Test
	void flywayBootstrapRowIsQueryAble() {
		Integer id = jdbcTemplate.queryForObject(
				"SELECT id FROM platform_bootstrap WHERE id = 1",
				Integer.class);
		assertThat(id).isEqualTo(1);
	}
}
