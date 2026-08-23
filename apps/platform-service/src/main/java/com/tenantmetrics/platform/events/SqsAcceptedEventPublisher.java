package com.tenantmetrics.platform.events;

import java.net.URI;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@EnableConfigurationProperties(EventQueueProperties.class)
class SqsAcceptedEventPublisher implements AcceptedEventPublisher {

	private final EventQueueProperties properties;
	private SqsClient sqsClient;
	private String queueUrl;

	SqsAcceptedEventPublisher(EventQueueProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void connect() {
		this.sqsClient = SqsClient.builder()
				.endpointOverride(URI.create(properties.getEndpoint()))
				.region(Region.of(properties.getRegion()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
				.build();
		sqsClient.createQueue(CreateQueueRequest.builder().queueName(properties.getName()).build());
		this.queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(properties.getName()).build())
				.queueUrl();
	}

	@Override
	public void publish(String tenantId, String eventId, String requestId) {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("tenant_id is required on every queue message");
		}
		if (eventId == null || eventId.isBlank()) {
			throw new IllegalArgumentException("event_id is required on every queue message");
		}
		String body = "{\"tenant_id\":\"" + tenantId + "\",\"event_id\":\"" + eventId + "\",\"request_id\":\""
				+ requestId + "\"}";
		sqsClient.sendMessage(SendMessageRequest.builder()
				.queueUrl(queueUrl)
				.messageBody(body)
				.messageAttributes(Map.of("tenant_id", MessageAttributeValue.builder()
						.dataType("String")
						.stringValue(tenantId)
						.build()))
				.build());
	}
}
