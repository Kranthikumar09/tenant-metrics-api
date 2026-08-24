package com.tenantmetrics.platform.events;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

public final class EventRequestBodyLimitFilter extends OncePerRequestFilter {

	private static final String EVENT_BATCH_PATH = "/v1/events:batch";
	private static final String SAFE_DETAIL =
			"Event batch request body exceeds the configured limit";

	private final int maxRequestBytes;

	public EventRequestBodyLimitFilter(EventIngestionProperties properties) {
		this.maxRequestBytes = properties.maxRequestBytes();
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !HttpMethod.POST.matches(request.getMethod()) || !EVENT_BATCH_PATH.equals(path);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (request.getContentLengthLong() > maxRequestBytes) {
			writePayloadTooLarge(response);
			return;
		}

		byte[] body = request.getInputStream().readNBytes(maxRequestBytes + 1);
		if (body.length > maxRequestBytes) {
			writePayloadTooLarge(response);
			return;
		}

		filterChain.doFilter(new BufferedBodyRequest(request, body), response);
	}

	private static void writePayloadTooLarge(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("""
				{"type":"about:blank","title":"Payload Too Large","status":413,"detail":"%s"}
				""".formatted(SAFE_DETAIL));
	}

	private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			return new ByteArrayServletInputStream(body);
		}

		@Override
		public BufferedReader getReader() {
			String encoding = getCharacterEncoding();
			Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
			return new BufferedReader(new InputStreamReader(getInputStream(), charset));
		}

		@Override
		public int getContentLength() {
			return body.length;
		}

		@Override
		public long getContentLengthLong() {
			return body.length;
		}
	}

	private static final class ByteArrayServletInputStream extends ServletInputStream {

		private final ByteArrayInputStream input;

		private ByteArrayServletInputStream(byte[] body) {
			this.input = new ByteArrayInputStream(body);
		}

		@Override
		public int read() {
			return input.read();
		}

		@Override
		public int read(byte[] bytes, int offset, int length) {
			return input.read(bytes, offset, length);
		}

		@Override
		public boolean isFinished() {
			return input.available() == 0;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			Objects.requireNonNull(readListener, "readListener");
			try {
				if (!isFinished()) {
					readListener.onDataAvailable();
				}
				if (isFinished()) {
					readListener.onAllDataRead();
				}
			}
			catch (IOException exception) {
				readListener.onError(exception);
			}
		}
	}
}
