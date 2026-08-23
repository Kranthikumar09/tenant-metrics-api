package com.tenantmetrics.platform;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
abstract class AbstractPlatformPostgresTest {

	// One JVM-lifetime container. @Container stops Postgres after each class and
	// leaves a cached Spring context pointed at a dead JDBC URL (health 503).
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:4.4.0"))
			.withServices(LocalStackContainer.Service.SQS);

	static {
		POSTGRES.start();
		LOCALSTACK.start();
	}

	@DynamicPropertySource
	static void registerInfrastructure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("platform.events.queue.endpoint", () -> LOCALSTACK.getEndpoint().toString());
		registry.add("platform.events.queue.region", LOCALSTACK::getRegion);
		registry.add("platform.events.queue.name", () -> "accepted-events");
		registry.add("platform.events.queue.access-key", LOCALSTACK::getAccessKey);
		registry.add("platform.events.queue.secret-key", LOCALSTACK::getSecretKey);
	}
}
