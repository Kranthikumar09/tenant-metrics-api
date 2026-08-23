package com.tenantmetrics.worker;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventConsumeTests {

	static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:4.4.0"))
			.withServices(LocalStackContainer.Service.SQS);

	static {
		LOCALSTACK.start();
	}

	@DynamicPropertySource
	static void registerQueue(DynamicPropertyRegistry registry) {
		registry.add("platform.events.queue.endpoint", () -> LOCALSTACK.getEndpoint().toString());
		registry.add("platform.events.queue.region", LOCALSTACK::getRegion);
		registry.add("platform.events.queue.name", () -> "accepted-events");
		registry.add("platform.events.queue.access-key", LOCALSTACK::getAccessKey);
		registry.add("platform.events.queue.secret-key", LOCALSTACK::getSecretKey);
		registry.add("platform.events.queue.poll-enabled", () -> "false");
	}

	@Autowired
	private TenantTaggedEventHandler handler;

	@Autowired
	private AcceptedEventPoller poller;

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
	}

	@Test
	void matchingTenantTagsAreAccepted() {
		ConsumeResult result = handler.handle(body("tenant-a", "evt-1"), "tenant-a");
		assertThat(result.accepted()).isTrue();
		assertThat(result.tenantId()).isEqualTo("tenant-a");
		assertThat(result.eventId()).isEqualTo("evt-1");
	}

	@Test
	void missingTenantTagIsRejected() {
		ConsumeResult result = handler.handle("{\"event_id\":\"evt-missing\"}", null);
		assertThat(result.accepted()).isFalse();
		assertThat(result.rejection()).isEqualTo("missing_tenant");
	}

	@Test
	void mismatchedTenantTagsAreRejected() {
		ConsumeResult result = handler.handle(body("tenant-a", "evt-mismatch"), "tenant-b");
		assertThat(result.accepted()).isFalse();
		assertThat(result.rejection()).isEqualTo("mismatched_tenant");
		assertThat(result.tenantId()).isNotEqualTo("tenant-b");
	}

	@Test
	void pollerAcceptsMatchingMessageAndRejectsBadTags() {
		send(body("tenant-a", "evt-poll-ok"), "tenant-a");
		send("{\"event_id\":\"evt-poll-missing\"}", null);
		send(body("tenant-a", "evt-poll-mismatch"), "tenant-b");

		assertThat(poller.pollOnce()).isEqualTo(3);
		assertThat(poller.acceptedEventIds()).containsExactly("evt-poll-ok");
		assertThat(poller.rejectedReasons()).contains("missing_tenant", "mismatched_tenant");
		assertThat(remainingMessages()).isZero();
	}

	private void send(String body, String attributeTenantId) {
		var request = SendMessageRequest.builder()
				.queueUrl(queueUrl)
				.messageBody(body);
		if (attributeTenantId != null) {
			request.messageAttributes(Map.of("tenant_id", MessageAttributeValue.builder()
					.dataType("String")
					.stringValue(attributeTenantId)
					.build()));
		}
		sqsClient.sendMessage(request.build());
	}

	private int remainingMessages() {
		List<software.amazon.awssdk.services.sqs.model.Message> messages = sqsClient.receiveMessage(
				ReceiveMessageRequest.builder()
						.queueUrl(queueUrl)
						.maxNumberOfMessages(10)
						.waitTimeSeconds(1)
						.build())
				.messages();
		return messages.size();
	}

	private static String body(String tenantId, String eventId) {
		return "{\"tenant_id\":\"" + tenantId + "\",\"event_id\":\"" + eventId + "\",\"request_id\":\"req-1\"}";
	}
}
