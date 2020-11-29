package org.jp.illg.util.io.http;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jp.illg.util.Timer;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResponseParser {

	private enum Status {
		Ok(200, "OK"),
		NoContent(204, "No Content"),
		BadRequest(400, "Bad Request"),
		NotFound(404, "Not Found");

		private int code;
		private String reasonPhrase;

		private Status(int code, String reasonPhrase) {
			this.code = code;
			this.reasonPhrase = reasonPhrase;
		}

		public int getCode() {
			return code;
		}

		public String getReasonPhrase() {
			return reasonPhrase;
		}

		public static Status valueOfCode(int code) {
			for(final Status s : values()) {
				if(s.getCode() == code) {return s;}
			}

			return null;
		}
	}

	public static class Response {
		protected Map<String, String> headers;
		protected byte[] body;

		@Getter
		private String version;

		private Status status;

		private Response(String version, Status status) {
			this.headers = new HashMap<>();
			this.body = new byte[0];
			this.version = version;
			this.status = status;
		}

		public void addHeaderField(String name, String value) {
			this.headers.put(name, value);
		}

		public Map<String, String> getHeaders() {
			return headers;
		}

		public void setBody(byte[] body) {
			this.body = body;
		}

		public byte[] getBody() {
			return body;
		}

		public int getStatusCode() {
			return status.getCode();
		}

		public String getReasonPhrase() {
			return status.getReasonPhrase();
		}

		public String getStartLine() {
			return version + " " + getStatusCode() + " " + getReasonPhrase();
		}

		@Override
		public String toString() {
			return getStartLine() + " headers: " + headers + " body: " + new String(body, StandardCharsets.UTF_8);
		}
	}

	private static Pattern responseLinePattern =
		Pattern.compile("^(?<version>\\S+) (?<statuscode>\\S+) (?<status>\\S+)$");
	private static Pattern headerPattern =
		Pattern.compile("^(?<name>\\S+):[ \\t]?(?<value>.+)[ \\t]?$");
	private static final String empty = "";

	private static final String logTag = ResponseParser.class.getSimpleName() + " : ";

	private ResponseParser() {}


	public static Response getResponse(@NonNull BufferedReader reader) throws IOException{
		final Response response = parseResponseLine(reader);
		if(response == null) {return null;}

		parseHeaderLines(reader, response);
		parseBody(reader, response);

		return response;
	}

	public static String getBody(@NonNull BufferedReader reader) throws IOException{

		final Response response = getResponse(reader);
		if(response == null) {return "";}

		final String body =
			response.getBody() != null ? new String(response.getBody(), StandardCharsets.UTF_8) : "";

		return body;
	}

	private static Response parseResponseLine(final BufferedReader br) throws IOException {
		final String requestLine = br.readLine();
		if(requestLine == null) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Could not parse response line, end of stream.");

			return null;
		}

		final Matcher matcher = responseLinePattern.matcher(requestLine);

		if (!matcher.matches()) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Could not parse response line, not matched response line.");

			return null;
		}

		final String version = matcher.group("version");
		final int statusCode = Integer.valueOf(matcher.group("statuscode"));
		final Status status = Status.valueOfCode(statusCode);
		if(status == null) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Could not parse response line, illegal status code = " + statusCode);

			return null;
		}

		final Response response = new Response(version, status);

		return response;
	}

	private static void parseHeaderLines(final BufferedReader br, final Response response) throws IOException {
		while (true) {
			final String headerField = br.readLine();
			if (empty.equals(headerField.trim())) {break;}

			final Matcher matcher = headerPattern.matcher(headerField);

			if (matcher.matches())
				response.addHeaderField(matcher.group("name").toLowerCase(), matcher.group("value"));
			else
				break;
		}
	}

	private static void parseBody(final BufferedReader br, final Response response) throws IOException {
		if (response.getHeaders().containsKey("transfer-encoding")) {
			parseChunkedBody(br, response);
		} else if (response.getHeaders().containsKey("content-length")) {
			parseSimpleBody(br, response);
		}
		else {
			if(log.isInfoEnabled())
				log.info(logTag + "Could not parse body, illegal body size.");
		}
	}

	private static void parseChunkedBody(
		final BufferedReader br, final Response response
	) throws IOException {
		final String transferEncoding = response.getHeaders().get("transfer-encoding");

		if (transferEncoding.equals("chunked")) {
			int length = 0;
			final ByteArrayOutputStream body = new ByteArrayOutputStream();

			String chunkSizeHex = br.readLine().replaceFirst(" .*$", "");
			int chunkSize = Integer.parseInt(chunkSizeHex, 16);
			while (chunkSize > 0) {
				final char[] chunk = new char[chunkSize];
				br.read(chunk, 0, chunkSize);
				br.skip(2);
				body.write((new String(chunk)).getBytes());
				length += chunkSize;

				chunkSizeHex = br.readLine().replaceFirst(" .*$", "");
				chunkSize = Integer.parseInt(chunkSizeHex, 16);
			}

			response.addHeaderField("content-length", Integer.toString(length));
			response.getHeaders().remove("transfer-encoding");
			response.setBody(body.toByteArray());
		}
	}

	private static void parseSimpleBody(
		final BufferedReader br, final Response response
	) throws IOException {
		int contentLength = -1;
		try {
			contentLength =
				Integer.valueOf(response.getHeaders().get("content-length"));
		}catch(NumberFormatException ex) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Illegal content-length = " + contentLength);
		}
		if(contentLength == 0) {
			return;
		}
		else if(contentLength <= -1) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Illegal content-length = " + contentLength);

			return;
		}

		final Timer timer = new Timer();
		timer.updateTimestamp();

		final char[] body = new char[contentLength];

		boolean isTimeout = false;
		int offset = 0;
		do{
			isTimeout = timer.isTimeout(5, TimeUnit.SECONDS);

			if(isTimeout) {
				if(log.isDebugEnabled())
					log.debug(logTag + "Timeout data receive");

				break;
			}
			else if(br.ready()) {
				final int readBytes = br.read(body, offset, contentLength - offset);
				if(readBytes <= -1) {
					if(log.isTraceEnabled())
						log.trace(logTag + "-1 Disconnected.");

					break;
				}

				offset += readBytes;

				timer.updateTimestamp();

				if(log.isTraceEnabled())
					log.trace(logTag + "Read " + readBytes + "bytes, Total=" + offset + "/" + contentLength);
			}
			else {
				try {
					Thread.sleep(10);
				}catch(InterruptedException ex) {}
			}


		}while(offset < contentLength);

		response.setBody((new String(body, 0, offset)).getBytes());
	}
}

