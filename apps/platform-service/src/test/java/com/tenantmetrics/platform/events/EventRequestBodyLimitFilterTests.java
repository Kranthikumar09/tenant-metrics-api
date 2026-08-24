package com.tenantmetrics.platform.events;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import static org.assertj.core.api.Assertions.assertThat;

class EventRequestBodyLimitFilterTests {

	@Test
	void declaredOversizeBodyIsRejectedBeforeTheApplicationReadsIt() throws Exception {
		EventRequestBodyLimitFilter filter = filterWithLimit(32);
		MockHttpServletRequest request = eventRequest("x".repeat(33));
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicBoolean invoked = new AtomicBoolean();

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

		assertThat(invoked).isFalse();
		assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(response.getContentAsString())
				.contains("\"status\":413")
				.contains("Event batch request body exceeds the configured limit")
				.doesNotContain("33", "32");
	}

	@Test
	void streamedOversizeBodyIsRejectedWhenContentLengthIsUnknown() throws Exception {
		EventRequestBodyLimitFilter filter = filterWithLimit(32);
		HttpServletRequest request = withoutContentLength(eventRequest("x".repeat(33)));
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicBoolean invoked = new AtomicBoolean();

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

		assertThat(invoked).isFalse();
		assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
	}

	@Test
	void bodyAtTheLimitReachesTheApplicationUnchanged() throws Exception {
		EventRequestBodyLimitFilter filter = filterWithLimit(32);
		HttpServletRequest request = withoutContentLength(eventRequest("x".repeat(32)));
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<byte[]> delivered = new AtomicReference<>();

		filter.doFilter(request, response, (filteredRequest, ignoredResponse) ->
				delivered.set(filteredRequest.getInputStream().readAllBytes()));

		assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
		assertThat(delivered.get()).isEqualTo("x".repeat(32).getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void unrelatedEndpointsAreNotReadOrLimited() throws Exception {
		EventRequestBodyLimitFilter filter = filterWithLimit(8);
		MockHttpServletRequest request = eventRequest("x".repeat(64));
		request.setRequestURI("/v1/predictions");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicBoolean invoked = new AtomicBoolean();

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

		assertThat(invoked).isTrue();
		assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	private static EventRequestBodyLimitFilter filterWithLimit(long bytes) {
		return new EventRequestBodyLimitFilter(
				new EventIngestionProperties(DataSize.ofBytes(bytes)));
	}

	private static MockHttpServletRequest eventRequest(String body) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/events:batch");
		request.setContentType(MediaType.APPLICATION_JSON_VALUE);
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		return request;
	}

	private static HttpServletRequest withoutContentLength(MockHttpServletRequest request) {
		return new HttpServletRequestWrapper(request) {
			@Override
			public int getContentLength() {
				return -1;
			}

			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
	}
}
