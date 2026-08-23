package com.tenantmetrics.platform;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.tenantmetrics.platform.events.AcceptedEventOutboxDispatcher;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EventEnqueueTests extends AbstractPlatformPostgresTest {

	private static final String TENANT_A_KEY = "tenant-a-test-key";
	private static final String TENANT_B_KEY = "tenant-b-test-key";

	@DynamicPropertySource
	static void delayScheduledOutboxDispatch(DynamicPropertyRegistry registry) {
		registry.add("platform.events.outbox.initial-delay-ms", () -> "600000");
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AcceptedEventOutboxDispatcher outboxDispatcher;

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
		sqsClient.createQueue(CreateQueueRequest.builder().queueName("accepted-events").build());
		queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName("accepted-events").build()).queueUrl();
		sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
		jdbcTemplate.update("DELETE FROM accepted_event_outbox");
	}

	@Test
	void acceptedEventIsCommittedToOutboxThenPublishedWithTenantA() throws Exception {
		ingest(TENANT_A_KEY, "idem-enq-a", "evt-enq-a", "acct-a");

		assertThat(receiveAll()).isEmpty();
		assertThat(countOutbox("tenant-a", "evt-enq-a", false)).isEqualTo(1);
		assertThat(outboxDispatcher.dispatchOnce()).isEqualTo(1);

		Message message = requireMessageContaining("evt-enq-a");
		assertThat(message.body()).contains("\"tenant_id\":\"tenant-a\"");
		assertThat(message.body()).doesNotContain("tenant-b");
		assertThat(countOutbox("tenant-a", "evt-enq-a", true)).isEqualTo(1);
	}

	@Test
	void forgedTenantClaimCannotEnqueueAsAnotherTenant() throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b")
						.header("Idempotency-Key", "idem-enq-forged")
						.content("""
								{
								  "tenant_id": "tenant-b",
								  "events": [
								    {
								      "event_id": "evt-enq-forged",
								      "account_external_id": "acct-b",
								      "event_type": "billing.invoice_paid",
								      "occurred_at": "2026-08-22T12:00:00Z",
								      "schema_version": 1
								    }
								  ]
								}
								"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));

		assertThat(countOutbox("tenant-a", "evt-enq-forged", false)).isEqualTo(1);
		assertThat(countOutbox("tenant-b", "evt-enq-forged", false)).isZero();
		outboxDispatcher.dispatchOnce();

		Message message = requireMessageContaining("evt-enq-forged");
		assertThat(message.body()).contains("\"tenant_id\":\"tenant-a\"");
		assertThat(message.body()).doesNotContain("\"tenant_id\":\"tenant-b\"");
	}

	@Test
	void tenantBEventIsEnqueuedSeparately() throws Exception {
		ingest(TENANT_A_KEY, "idem-enq-shared-a", "evt-enq-shared", "acct-a");
		ingest(TENANT_B_KEY, "idem-enq-shared-b", "evt-enq-shared", "acct-b");

		assertThat(countOutbox("tenant-a", "evt-enq-shared", false)).isEqualTo(1);
		assertThat(countOutbox("tenant-b", "evt-enq-shared", false)).isEqualTo(1);
		outboxDispatcher.dispatchOnce();

		List<Message> messages = receiveAll();
		assertThat(messages).anyMatch(message -> message.body().contains("evt-enq-shared")
				&& message.body().contains("\"tenant_id\":\"tenant-a\""));
		assertThat(messages).anyMatch(message -> message.body().contains("evt-enq-shared")
				&& message.body().contains("\"tenant_id\":\"tenant-b\""));
	}

	@Test
	void replayDoesNotEnqueueASecondMessage() throws Exception {
		ingest(TENANT_A_KEY, "idem-enq-replay", "evt-enq-replay", "acct-a");
		ingest(TENANT_A_KEY, "idem-enq-replay", "evt-enq-replay", "acct-a");

		assertThat(countOutbox("tenant-a", "evt-enq-replay", false)).isEqualTo(1);
		outboxDispatcher.dispatchOnce();

		List<Message> messages = receiveAll().stream()
				.filter(message -> message.body().contains("evt-enq-replay"))
				.toList();
		assertThat(messages).hasSize(1);
	}

	private void ingest(String apiKey, String idempotencyKey, String eventId, String accountId) throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", apiKey)
						.header("Idempotency-Key", idempotencyKey)
						.content(batchJson(eventId, accountId)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));
	}

	private Message requireMessageContaining(String eventId) {
		return receiveAll().stream()
				.filter(message -> message.body().contains(eventId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no SQS message contained " + eventId));
	}

	private List<Message> receiveAll() {
		return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
				.queueUrl(queueUrl)
				.maxNumberOfMessages(10)
				.waitTimeSeconds(2)
				.build())
				.messages();
	}

	private int countOutbox(String tenantId, String eventId, boolean published) {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*)
						FROM accepted_event_outbox
						WHERE tenant_id = ?
						  AND event_id = ?
						  AND published_at IS %s NULL
						""".formatted(published ? "NOT" : ""),
				Integer.class,
				tenantId,
				eventId);
		return count == null ? 0 : count;
	}

	private static String batchJson(String eventId, String accountExternalId) {
		return """
				{
				  "events": [
				    {
				      "event_id": "%s",
				      "account_external_id": "%s",
				      "event_type": "billing.invoice_paid",
				      "occurred_at": "2026-08-22T12:00:00Z",
				      "schema_version": 1
				    }
				  ]
				}
				""".formatted(eventId, accountExternalId);
	}
}
