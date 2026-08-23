package com.tenantmetrics.worker;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@EnableConfigurationProperties(EventQueueProperties.class)
class AcceptedEventPoller {

	private final EventQueueProperties properties;
	private final TenantTaggedEventHandler handler;
	private final List<String> acceptedEventIds = new ArrayList<>();
	private final List<String> rejectedReasons = new ArrayList<>();
	private SqsClient sqsClient;
	private String queueUrl;

	AcceptedEventPoller(EventQueueProperties properties, TenantTaggedEventHandler handler) {
		this.properties = properties;
		this.handler = handler;
	}

	@Scheduled(fixedDelayString = "${platform.events.queue.poll-interval-ms:1000}")
	void scheduledPoll() {
		if (properties.isPollEnabled()) {
			pollOnce();
		}
	}

	int pollOnce() {
		ensureClient();
		List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
				.queueUrl(queueUrl)
				.maxNumberOfMessages(10)
				.waitTimeSeconds(1)
				.messageAttributeNames("tenant_id")
				.build())
				.messages();
		for (Message message : messages) {
			String attributeTenantId = tenantAttribute(message.messageAttributes());
			ConsumeResult result = handler.handle(message.body(), attributeTenantId);
			if (result.accepted()) {
				acceptedEventIds.add(result.eventId());
			}
			else if (result.rejection() != null) {
				rejectedReasons.add(result.rejection());
			}
			sqsClient.deleteMessage(DeleteMessageRequest.builder()
					.queueUrl(queueUrl)
					.receiptHandle(message.receiptHandle())
					.build());
		}
		return messages.size();
	}

	List<String> acceptedEventIds() {
		return List.copyOf(acceptedEventIds);
	}

	List<String> rejectedReasons() {
		return List.copyOf(rejectedReasons);
	}

	private static String tenantAttribute(Map<String, MessageAttributeValue> attributes) {
		if (attributes == null) {
			return null;
		}
		MessageAttributeValue value = attributes.get("tenant_id");
		return value == null ? null : value.stringValue();
	}

	private void ensureClient() {
		if (sqsClient != null) {
			return;
		}
		sqsClient = SqsClient.builder()
				.endpointOverride(URI.create(properties.getEndpoint()))
				.region(Region.of(properties.getRegion()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
				.build();
		sqsClient.createQueue(CreateQueueRequest.builder().queueName(properties.getName()).build());
		queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(properties.getName()).build()).queueUrl();
	}
}
