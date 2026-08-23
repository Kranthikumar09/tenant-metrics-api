package com.tenantmetrics.worker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WorkerRescoreTests {

	private static final String QUEUE_NAME = "accepted-events";
	private static final String DEAD_LETTER_QUEUE_NAME = "accepted-events-dlq";
	private static final int MAX_RECEIVE_COUNT = 2;

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:4.4.0"))
			.withServices(LocalStackContainer.Service.SQS);

	static {
		POSTGRES.start();
		LOCALSTACK.start();
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("filesystem:" + migrations().toAbsolutePath())
				.load()
				.migrate();
	}

	@DynamicPropertySource
	static void registerInfrastructure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.enabled", () -> "false");
		registry.add("platform.events.queue.endpoint", () -> LOCALSTACK.getEndpoint().toString());
		registry.add("platform.events.queue.region", LOCALSTACK::getRegion);
		registry.add("platform.events.queue.name", () -> QUEUE_NAME);
		registry.add("platform.events.queue.dead-letter-name", () -> DEAD_LETTER_QUEUE_NAME);
		registry.add("platform.events.queue.max-receive-count", () -> Integer.toString(MAX_RECEIVE_COUNT));
		registry.add("platform.events.queue.access-key", LOCALSTACK::getAccessKey);
		registry.add("platform.events.queue.secret-key", LOCALSTACK::getSecretKey);
		registry.add("platform.events.queue.poll-enabled", () -> "false");
	}

	@Autowired
	private AcceptedEventPoller poller;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private SqsClient sqsClient;
	private String queueUrl;

	@BeforeEach
	void openQueue() {
		sqsClient = SqsClient.builder()
				.endpointOverride(LOCALSTACK.getEndpoint())
				.region(Region.of(LOCALSTACK.getRegion()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
				.build();
		sqsClient.createQueue(CreateQueueRequest.builder().queueName(QUEUE_NAME).build());
		queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(QUEUE_NAME).build()).queueUrl();
	}

	@Test
	void acceptedTenantTaggedMessageWritesThatTenantScore() {
		List<Message> unexpectedMessages = List.of();
		setQueueVisibilityTimeout(0);
		try {
			insertEvent("tenant-a", "evt-rescore-a", "acct-rescore-a", "auth.login");
			insertEvent("tenant-b", "evt-rescore-b-noise", "acct-rescore-a", "auth.login");

			send(body("tenant-a", "evt-rescore-a"), "tenant-a");
			assertThat(poller.pollOnce()).isEqualTo(1);
			unexpectedMessages = receiveMessages();
			assertThat(poller.acceptedEventIds()).contains("evt-rescore-a");
			assertThat(unexpectedMessages).isEmpty();

			Map<String, Object> score = findScore("tenant-a", "acct-rescore-a");
			assertThat(score).isNotNull();
			assertThat(score.get("score_version")).isEqualTo("RULES_BASELINE");
			assertThat(((Number) score.get("health_score")).intValue()).isEqualTo(70);
			assertThat(score.get("risk_probability")).isNull();
			assertThat(findScore("tenant-b", "acct-rescore-a")).isNull();
		}
		finally {
			unexpectedMessages.forEach(this::delete);
			setQueueVisibilityTimeout(30);
		}
	}

	@Test
	void mismatchedTenantTagDoesNotWriteAScore() {
		insertEvent("tenant-a", "evt-rescore-mismatch", "acct-rescore-mismatch", "auth.login");

		send(body("tenant-a", "evt-rescore-mismatch"), "tenant-b");
		assertThat(poller.pollOnce()).isEqualTo(1);
		assertThat(poller.rejectedReasons()).contains("mismatched_tenant");
		assertThat(findScore("tenant-a", "acct-rescore-mismatch")).isNull();
		assertThat(findScore("tenant-b", "acct-rescore-mismatch")).isNull();
	}

	@Test
	void sameAccountExternalIdIsIsolatedWhenEachTenantMessageIsAccepted() {
		insertEvent("tenant-a", "evt-rescore-shared-a", "acct-rescore-shared", "billing.payment_failed");
		insertEvent("tenant-b", "evt-rescore-shared-b", "acct-rescore-shared", "auth.login");

		send(body("tenant-a", "evt-rescore-shared-a"), "tenant-a");
		send(body("tenant-b", "evt-rescore-shared-b"), "tenant-b");
		assertThat(poller.pollOnce()).isEqualTo(2);

		assertThat(((Number) findScore("tenant-a", "acct-rescore-shared").get("health_score")).intValue()).isEqualTo(45);
		assertThat(((Number) findScore("tenant-b", "acct-rescore-shared").get("health_score")).intValue()).isEqualTo(70);
	}

	@Test
	void missingEventRemainsRetryableAndIsNotRecordedAsAccepted() {
		String eventId = "evt-rescore-missing";
		List<Message> retryableMessages = List.of();
		setQueueVisibilityTimeout(0);
		try {
			send(body("tenant-a", eventId), "tenant-a");
			assertThat(poller.pollOnce()).isEqualTo(1);
			retryableMessages = receiveMessages();

			assertThat(poller.acceptedEventIds()).doesNotContain(eventId);
			assertThat(retryableMessages).anyMatch(message -> message.body().contains(eventId));
			assertThat(findScore("tenant-a", "acct-from-missing-event")).isNull();
		}
		finally {
			retryableMessages.forEach(this::delete);
			setQueueVisibilityTimeout(30);
		}
	}

	@Test
	void missingEventMovesToDeadLetterQueueAfterBoundedReceives() {
		String eventId = "evt-rescore-dead-letter";
		List<Message> deadLetterMessages = List.of();
		String deadLetterQueueUrl = null;
		setQueueVisibilityTimeout(0);
		try {
			poller.pollOnce();
			deadLetterQueueUrl = queueUrl(DEAD_LETTER_QUEUE_NAME);

			String deadLetterQueueArn = queueAttribute(deadLetterQueueUrl, QueueAttributeName.QUEUE_ARN);
			String redrivePolicy = queueAttribute(queueUrl, QueueAttributeName.REDRIVE_POLICY);
			assertThat(redrivePolicy)
					.contains("\"deadLetterTargetArn\":\"" + deadLetterQueueArn + "\"")
					.contains("\"maxReceiveCount\":\"" + MAX_RECEIVE_COUNT + "\"");

			send(body("tenant-a", eventId), "tenant-a");
			for (int attempt = 0;
					attempt < MAX_RECEIVE_COUNT + 2 && deadLetterMessages.isEmpty();
					attempt++) {
				poller.pollOnce();
				deadLetterMessages = receiveMessages(deadLetterQueueUrl);
			}

			assertThat(deadLetterMessages).anySatisfy(message -> {
				assertThat(message.body()).contains(eventId);
				assertThat(message.messageAttributes().get("tenant_id").stringValue()).isEqualTo("tenant-a");
			});
			assertThat(poller.acceptedEventIds()).doesNotContain(eventId);
			assertThat(findScore("tenant-a", "acct-from-missing-event")).isNull();
		}
		finally {
			if (deadLetterQueueUrl != null) {
				String finalDeadLetterQueueUrl = deadLetterQueueUrl;
				deadLetterMessages.forEach(message -> delete(finalDeadLetterQueueUrl, message));
			}
			setQueueVisibilityTimeout(30);
		}
	}

	private void insertEvent(String tenantId, String eventId, String accountId, String eventType) {
		jdbcTemplate.update(
				"""
						INSERT INTO ingested_events (
							tenant_id, event_id, account_external_id, event_type,
							occurred_at, schema_version, request_id
						) VALUES (?, ?, ?, ?, ?, 1, 'req-rescore')
						""",
				tenantId,
				eventId,
				accountId,
				eventType,
				Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
	}

	private Map<String, Object> findScore(String tenantId, String accountExternalId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"""
						SELECT tenant_id, account_external_id, health_score, score_version, risk_probability
						FROM account_scores
						WHERE tenant_id = ? AND account_external_id = ?
						""",
				tenantId,
				accountExternalId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private void send(String body, String attributeTenantId) {
		sqsClient.sendMessage(SendMessageRequest.builder()
				.queueUrl(queueUrl)
				.messageBody(body)
				.messageAttributes(Map.of("tenant_id", MessageAttributeValue.builder()
						.dataType("String")
						.stringValue(attributeTenantId)
						.build()))
				.build());
	}

	private List<Message> receiveMessages() {
		return receiveMessages(queueUrl);
	}

	private List<Message> receiveMessages(String targetQueueUrl) {
		return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
				.queueUrl(targetQueueUrl)
				.maxNumberOfMessages(10)
				.waitTimeSeconds(1)
				.messageAttributeNames("tenant_id")
				.build())
				.messages();
	}

	private void delete(Message message) {
		delete(queueUrl, message);
	}

	private void delete(String targetQueueUrl, Message message) {
		sqsClient.deleteMessage(DeleteMessageRequest.builder()
				.queueUrl(targetQueueUrl)
				.receiptHandle(message.receiptHandle())
				.build());
	}

	private String queueUrl(String queueName) {
		return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
	}

	private String queueAttribute(String targetQueueUrl, QueueAttributeName attributeName) {
		return sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
				.queueUrl(targetQueueUrl)
				.attributeNames(attributeName)
				.build())
				.attributes()
				.get(attributeName);
	}

	private void setQueueVisibilityTimeout(int seconds) {
		sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
				.queueUrl(queueUrl)
				.attributes(Map.of(QueueAttributeName.VISIBILITY_TIMEOUT, Integer.toString(seconds)))
				.build());
	}

	private static String body(String tenantId, String eventId) {
		return "{\"tenant_id\":\"" + tenantId + "\",\"event_id\":\"" + eventId + "\",\"request_id\":\"req-rescore\"}";
	}

	private static Path migrations() {
		Path moduleRelative = Path.of("..", "platform-service", "src", "main", "resources", "db", "migration");
		if (Files.isDirectory(moduleRelative)) {
			return moduleRelative;
		}
		Path repoRelative = Path.of("apps", "platform-service", "src", "main", "resources", "db", "migration");
		if (Files.isDirectory(repoRelative)) {
			return repoRelative;
		}
		throw new IllegalStateException("platform-service Flyway migrations not found");
	}
}
