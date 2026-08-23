package com.tenantmetrics.worker;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tenantmetrics.worker.scoring.AccountScoreRefresher;

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
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

@Component
@EnableConfigurationProperties(EventQueueProperties.class)
class AcceptedEventPoller {

	private final EventQueueProperties properties;
	private final TenantTaggedEventHandler handler;
	private final AccountScoreRefresher scoreRefresher;
	private final List<String> acceptedEventIds = new ArrayList<>();
	private final List<String> rejectedReasons = new ArrayList<>();
	private SqsClient sqsClient;
	private String queueUrl;

	AcceptedEventPoller(
			EventQueueProperties properties,
			TenantTaggedEventHandler handler,
			AccountScoreRefresher scoreRefresher) {
		this.properties = properties;
		this.handler = handler;
		this.scoreRefresher = scoreRefresher;
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
				if (scoreRefresher.refresh(result.tenantId(), result.eventId())) {
					acceptedEventIds.add(result.eventId());
					delete(message);
				}
			}
			else {
				if (result.rejection() != null) {
					rejectedReasons.add(result.rejection());
				}
				delete(message);
			}
		}
		return messages.size();
	}

	List<String> acceptedEventIds() {
		return List.copyOf(acceptedEventIds);
	}

	List<String> rejectedReasons() {
		return List.copyOf(rejectedReasons);
	}

	private void delete(Message message) {
		sqsClient.deleteMessage(DeleteMessageRequest.builder()
				.queueUrl(queueUrl)
				.receiptHandle(message.receiptHandle())
				.build());
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
		String deadLetterQueueUrl = createQueue(properties.getDeadLetterName());
		String deadLetterQueueArn = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
				.queueUrl(deadLetterQueueUrl)
				.attributeNames(QueueAttributeName.QUEUE_ARN)
				.build())
				.attributes()
				.get(QueueAttributeName.QUEUE_ARN);
		queueUrl = createQueue(properties.getName());
		sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
				.queueUrl(queueUrl)
				.attributes(Map.of(
						QueueAttributeName.REDRIVE_POLICY,
						redrivePolicy(deadLetterQueueArn, properties.getMaxReceiveCount())))
				.build());
	}

	private String createQueue(String queueName) {
		sqsClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build());
		return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
	}

	private static String redrivePolicy(String deadLetterQueueArn, int maxReceiveCount) {
		if (maxReceiveCount < 1) {
			throw new IllegalArgumentException("platform.events.queue.max-receive-count must be at least 1");
		}
		return "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"%d\"}"
				.formatted(deadLetterQueueArn, maxReceiveCount);
	}
}
