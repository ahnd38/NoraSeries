package org.jp.illg.dstar.service.reflectorname.importers.multiforward;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARSystemManager;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.service.reflectorname.define.ReflectorHostsImporterType;
import org.jp.illg.dstar.service.reflectorname.importers.ReflectorHostsImporterBase;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.io.http.ResponseParser;
import org.jp.illg.util.io.http.ResponseParser.Response;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiForwardRepeaterHostsImporter extends ReflectorHostsImporterBase{

	private static class MultiForwardConnectedRepeaterData{

		private MultiForwardConnectedRepeaterData() {}

		@Getter
		@Setter
		private String callsign;

		@Getter
		@Setter
		private String ipAddress;

		@Getter
		@Setter
		private int port;

		@Getter
		@Setter
		private String status;

		@Getter
		@Setter
		private String area;

		@Override
		public String toString() {return toString(0);}

		public String toString(int indent) {
			if(indent < 0) {indent = 0;}
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < indent; i++) {sb.append(' ');}

			sb.append("Callsign:");
			sb.append(callsign);
			sb.append("/IPAddress:");
			sb.append(String.format("%-15s", ipAddress));
			sb.append("/Port:");
			sb.append(String.format("%-5s", port));
			sb.append("/Status:");
			sb.append(status);
			sb.append("/Area:");
			sb.append(area);

			return sb.toString();
		}
	}

	private static final int importIntervalMinutes = 15;


	private static final String logTag =
		MultiForwardRepeaterHostsImporter.class.getSimpleName() + " : ";

	private static final SimpleDateFormat lastModifiedPattern =
		new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

	private static final Pattern repeaterListRowPattern = Pattern.compile("\\{.+\\}");
	private static final Pattern repeaterListRowEntryPattern =
		Pattern.compile("\\\"([A-Za-z0-9\\.\\-_\\s]+)\\\":((\\\"([A-Za-z0-9\\.\\-_\\s]+)\\\")|([0-9]+))");


	private boolean importTaskExecuting;

	private URL targetURL;
	private String lastModifiedString;
	private Date lastModified;

	private String repeaterHosts;

	private boolean hasUpdateData;

	private final Timer importTaskIntervalTimekeeper;

	private String url;
	private static final String urlPropertyName = "URL";
	private static final String urlDefault = "http://hole-punchd.d-star.info:30011/repeater.json";

	private String userAgent;
	private static final String userAgentPropertyName = "UserAgent";
	private static final String userAgentDefault = "dmonitor/01.64";


	public MultiForwardRepeaterHostsImporter(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor
	) {
		super(systemID, exceptionListener, workerExecutor);

		importTaskExecuting = false;
		importTaskIntervalTimekeeper = new Timer();

		targetURL = null;
		lastModifiedString = null;
		lastModified = null;

		repeaterHosts = null;

		hasUpdateData = false;

		url = urlDefault;
		userAgent = userAgentDefault;
	}

	@Override
	public ReflectorHostsImporterType getImporterType() {
		return ReflectorHostsImporterType.JARLMultiForward;
	}

	@Override
	public ThreadProcessResult processImporter() {
		if(
			targetURL != null &&
			!importTaskExecuting &&
			importTaskIntervalTimekeeper.isTimeout() &&
			DSTARSystemManager.isIdleSystem(systemID, 90, TimeUnit.SECONDS)
		) {
			importTaskExecuting = true;
			try {
				workerExecutor.submit(new RunnableTask() {
					@Override
					public void task() {
						importTaskExecuting = true;
						try {
							final List<MultiForwardConnectedRepeaterData> repeaterList = getRepeaterList();
							if(repeaterList == null) {return;}

							repeaterHosts = convertRepeaterList(targetURL, repeaterList, lastModified);

							hasUpdateData = true;

							if(log.isDebugEnabled())
								log.debug(logTag + "Receive repeater list...\n" + repeaterHosts);
						}finally {
							importTaskExecuting = false;
						}
					}
				});
			}catch(RejectedExecutionException ex) {
				if(log.isErrorEnabled())
					log.error(logTag + "Could not execute task");
			}

			importTaskIntervalTimekeeper.updateTimestamp();
			importTaskIntervalTimekeeper.setTimeoutTime(
				(new Random().nextInt(importIntervalMinutes) + importIntervalMinutes) >> 1,
				TimeUnit.MINUTES
			);
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	public String getTargetName() {
		return targetURL.toExternalForm();
	}

	@Override
	public boolean hasUpdateReflectorHosts() {
		return hasUpdateData;
	}

	@Override
	public InputStream getReflectorHosts() {
		hasUpdateData = false;

		return repeaterHosts != null ?
			new ByteArrayInputStream(repeaterHosts.getBytes(StandardCharsets.UTF_8)) : null;
	}

	@Override
	protected boolean setPropertiesInternal(@NonNull Properties properties) {
		url = PropertyUtils.getString(properties, urlPropertyName, urlDefault);
		userAgent = PropertyUtils.getString(properties, userAgentPropertyName, userAgentDefault);

		try {
			targetURL = new URL(url);
		} catch (MalformedURLException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Illegal URL = " + url);

			return false;
		}

		return true;
	}

	private List<MultiForwardConnectedRepeaterData> getRepeaterList() {
		if(log.isInfoEnabled())
			log.info(logTag + "Trying update multi_forward repeater list..." + targetURL.toExternalForm());

		final Response response = getRepeaterListJSON(targetURL, userAgent, lastModifiedString);
		if(response == null) {return null;}

		switch(response.getStatusCode()) {
		case 200:	//OK
			for(
				final Iterator<Map.Entry<String, String>> it =
					response.getHeaders().entrySet().iterator(); it.hasNext();
			) {
				final Map.Entry<String, String> entry = it.next();
				final String key = entry.getKey();
				final String value = entry.getValue();

				if("Last-Modified".equalsIgnoreCase(key)) {
					lastModifiedString = value;

					try {
						lastModified = lastModifiedPattern.parse(value);
					} catch (final ParseException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Illegal last modified format", ex);

						lastModified = new Date();
					}

					if(log.isDebugEnabled())
						log.debug(logTag + "Last modified = " + value);
				}
			}

			final String repeaterListJSON = new String(response.getBody());
			if(log.isTraceEnabled())
				log.trace(logTag + "Receive data...\n" + repeaterListJSON);

			final List<MultiForwardConnectedRepeaterData> repeaterList = parseConnectedTable(repeaterListJSON);
			if(repeaterList == null) {return null;}

			if(log.isInfoEnabled()) {
				log.info(
					logTag +
					"Receive multi_forward active repeater list, " + repeaterList.size() + " repeaters..." + targetURL.toExternalForm()
				);
			}

			return repeaterList;

		case 304:	//Not Modified
			if(log.isDebugEnabled())
				log.debug(logTag + "Server returned 304");

			if(log.isInfoEnabled())
				log.info(logTag + "Already have latest repeater list..." + targetURL.toExternalForm());

			break;

		case 403:	//Forbidden
			if(log.isErrorEnabled())
				log.error(logTag + "Server returned 403 forbidden..." + targetURL.toExternalForm());

			break;

		default:
			if(log.isErrorEnabled())
				log.error(logTag + "Server returned " + response.getStatusCode() + "..." + targetURL.toExternalForm());

			break;
		}

		return null;
	}

	private static Response getRepeaterListJSON(
		final URL url,
		final String userAgent,
		final String lastModified
	) {
		try(
			final Socket socket = new Socket(url.getHost(), url.getPort());
			final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		){
			socket.setSoTimeout(5000);

			final StringBuilder request = new StringBuilder();
			request.append("GET ");
			request.append(url.getPath());
			request.append(" HTTP/1.1\r\n");

			request.append("Host: ");
			request.append(url.getHost());
			request.append(":");
			request.append(url.getPort());
			request.append("\r\n");

			request.append("Accept-Language: ja-JP\r\n");

			request.append("User-Agent: ");
			request.append(userAgent);
			request.append("\r\n");

			if(lastModified != null)
				request.append(String.format("If-Modified-Since: %29.29s\r\n", lastModified));

			request.append("\r\n");

			if(log.isTraceEnabled())
				log.trace(logTag + "Request HTTP request to " + url + "\n" + request.toString());

			writer.append(request);
			writer.flush();

			return ResponseParser.getResponse(reader);
		}catch(final IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Server connection error = " + url, ex);
		}

		return null;
	}

	/**
	 * レピータリストJSON(※独自仕様)をパースする
	 * @param json レピータリストJSON
	 * @return レピータリスト
	 *
	 * <b>入力データがJSONのシンタックスルールに沿っていないケースがあるので、標準的なライブラリは使用不可</b>
	 *
	 * InputSample:
	 * {"Connected Table":[
	 * {"callsign":"JK1ZRW B","ip_address":"183.177.205.139","port":51000,"status":"off","area":"1","zr_call":"JK1ZRW  "},
	 * {"callsign":"JP1YCD A","ip_address":"27.91.220.53","port":51000,"status":"off","area":"1","zr_call":"JP1YCD  "},
	 * {"callsign":"JP1YCS A","ip_address":"153.158.255.156","port":51000,"status":"off","area":"1","zr_call":"JP1YCS  "}
	 * ]
	 * }
	 */
	private static final List<MultiForwardConnectedRepeaterData> parseConnectedTable(final String json) {
		final List<MultiForwardConnectedRepeaterData> table = new LinkedList<>();

		final Matcher rowMatcher = repeaterListRowPattern.matcher(json);
		while(rowMatcher.find()) {
			final String repeaterInfoJSON = rowMatcher.group();

			final MultiForwardConnectedRepeaterData repeaterInfo = new MultiForwardConnectedRepeaterData();
			final Matcher entryMatcher = repeaterListRowEntryPattern.matcher(repeaterInfoJSON);
			while(entryMatcher.find() && entryMatcher.groupCount() >= 5) {
				final String key = entryMatcher.group(1);
				final String value =
					entryMatcher.group(4) != null ? entryMatcher.group(4) : entryMatcher.group(5);
				if(value == null) {
					if(log.isWarnEnabled())
						log.warn(logTag + "Illegal repeater entry data\n    " + repeaterInfo);

					break;
				}

				if("callsign".equalsIgnoreCase(key))
					repeaterInfo.setCallsign(DSTARUtils.formatFullLengthCallsign(value.trim()));
				else if("ip_address".equalsIgnoreCase(key))
					repeaterInfo.setIpAddress(value.trim());
				else if("port".equalsIgnoreCase(key)) {
					int port = -1;
					try {
						port = Integer.valueOf(value.trim());

						repeaterInfo.setPort(port);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Illegal port value\n    " + repeaterInfoJSON);
					}
				}
				else if("status".equalsIgnoreCase(key))
					repeaterInfo.setStatus(value.trim());
				else if("area".equalsIgnoreCase(key))
					repeaterInfo.setArea(value.trim());
			}

			if(
				!CallSignValidator.isValidJapanRepeaterCallsign(repeaterInfo.getCallsign()) ||
				repeaterInfo.getIpAddress() == null ||
				!SystemUtil.getIpv4RegExpPattern().matcher(repeaterInfo.getIpAddress()).matches() ||
				repeaterInfo.getPort() <= 0 || repeaterInfo.getPort() > 65535
			) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Illegal repeater entry data\n    " + repeaterInfoJSON);

				continue;
			}

			table.add(repeaterInfo);
		}

		return table;
	}

	private static final String convertRepeaterList(
		final URL url,
		final List<MultiForwardConnectedRepeaterData> repeaterList,
		final Date lastModified
	) {
		final StringBuilder sb = new StringBuilder();
		sb.append("# ");
		sb.append(url.toExternalForm());

		sb.append("\n");

		sb.append("# ");

		sb.append("\n");

		if(lastModified != null) {
			sb.append("# ");
			sb.append(FormatUtil.dateFormat(lastModified.getTime()));

			sb.append("\n");
		}

		sb.append("\n");

		for(final Iterator<MultiForwardConnectedRepeaterData> it = repeaterList.iterator(); it.hasNext();) {
			final MultiForwardConnectedRepeaterData repeater = it.next();

			sb.append(repeater.getCallsign());

			sb.append("\t");

			sb.append(repeater.getIpAddress());
			sb.append(":");
			sb.append(repeater.getPort());

			sb.append("\t");

			sb.append(DSTARProtocol.JARLLink.getName());

			sb.append("\t");

			sb.append("5");	//Priority

			if(it.hasNext()) {sb.append("\n");}
		}

		return sb.toString();
	}
}
