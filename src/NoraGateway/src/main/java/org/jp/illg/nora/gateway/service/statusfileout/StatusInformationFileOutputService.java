package org.jp.illg.nora.gateway.service.statusfileout;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.model.HeardEntry;
import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.dstar.reporter.model.GatewayRouteStatusReport;
import org.jp.illg.dstar.reporter.model.GatewayStatusReport;
import org.jp.illg.dstar.reporter.model.RepeaterRouteStatusReport;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.nora.gateway.reporter.NoraGatewayStatusReporter;
import org.jp.illg.nora.gateway.reporter.NoraGatewayStatusReporterWinLinux;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.Timer;
import org.jp.illg.util.logback.appender.NotifyAppender;
import org.jp.illg.util.logback.appender.NotifyAppenderListener;
import org.jp.illg.util.logback.appender.NotifyLogEvent;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.ToLongFunction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatusInformationFileOutputService
implements NoraGatewayStatusReportListener, NotifyAppenderListener, AutoCloseable{

	private static final long outputIntervalTimeMillis = 2000;

	private static final DateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US);

	private class LogEntry{
		@Getter
		private UUID id;
		@Getter
		private long time;
		@Getter
		private String message;

		public LogEntry(String message) {
			this.id = UUID.randomUUID();
			this.time = System.currentTimeMillis();
			this.message = message;
		}
	}

	private final ApplicationInformation<?> applicationVersion;
	private final String fileName;

	private final Deque<LogEntry> logs;
	private boolean logUpdate;


	private NoraGatewayStatusReporter reporter;

	@Getter
	@Setter
	private String outputPath;

	private BasicStatusInformation info;

	private final Timer outputIntervalTimekeeper;

	private int failCount;
	private Exception lastException;


	public StatusInformationFileOutputService(
		@NonNull ApplicationInformation<?> applicationVersion,
		@NonNull NoraGatewayStatusReporter reporter,
		@NonNull String outputPath
	) {
		super();

		this.applicationVersion = applicationVersion;
		this.reporter = reporter;
		setOutputPath(outputPath);

		fileName = this.applicationVersion.getApplicationName() + ".status";

		logs = new LinkedList<>();
		logUpdate = false;

		info = null;

		outputIntervalTimekeeper = new Timer();

		failCount = 0;
		lastException = null;

		if(!NotifyAppender.isListenerRegisterd(this))
			NotifyAppender.addListener(this);

		this.reporter.addListener(this);
	}

	@Override
	public void close() {

	}

	@Override
	public void listenerProcess() {

	}

	public void report(BasicStatusInformation info) {

		if(
			outputIntervalTimekeeper.isTimeout(outputIntervalTimeMillis, TimeUnit.MILLISECONDS) &&
			(logUpdate || !equalsInfo(this.info, info))
		) {
			outputIntervalTimekeeper.updateTimestamp();
			logUpdate = false;

			if(getOutputPath() == null || "".equals(getOutputPath()))
				setOutputPath("./");
			else if(!getOutputPath().endsWith("/"))
				setOutputPath(getOutputPath() + "/");

			final boolean success =
				writeStatusInformation(info, new File(getOutputPath() + fileName)) &&
				writeStatusInformationJson(info, new File(getOutputPath() + fileName + ".json"));

			if(!success) {
				if(failCount < 10)
					failCount++;
				else {
					if(log.isWarnEnabled())
						log.warn("Failed status information file output.", lastException);

					failCount = 0;
				}
			}else {
				failCount = 0;
			}

			this.info = info;
		}
	}

	private boolean equalsInfo(BasicStatusInformation infoA, BasicStatusInformation infoB) {
		if(infoA == null && infoB == null)
			return true;

		if(
			(infoA == null && infoB != null) ||
			(infoA != null && infoB == null)
		) {return false;}


		return infoA.equalsNoraGatewayStatusInformation(infoB);
	}

	private boolean writeStatusInformation(BasicStatusInformation info, File destinationFile) {

		try(BufferedWriter writer = new BufferedWriter(new FileWriter(destinationFile))) {
			writeHeader(writer, applicationVersion);

			writer.write("\r\n");

			writeGatewayStatusReport(writer, info.getGatewayStatusReport());

			writer.write("\r\n");

			writeRoutingServiceStatusReport(
				writer, info.getGatewayStatusReport().getRoutingServiceReports()
			);
			writer.write("\r\n");

			writeRepeaterStatusReports(writer, info.getRepeaterStatusReports());
			writer.write("\r\n");

			writeHeardEntries(writer, info.getGatewayStatusReport().getHeardReports());
			writer.write("\r\n");

			writeLogs(writer, logs);
			writer.write("\r\n");


			writer.flush();

		}catch(IOException ex) {
			lastException = ex;
			return false;
		}

		return true;
	}

	private boolean writeStatusInformationJson(BasicStatusInformation info, File destinationFile) {

		try(BufferedWriter writer = new BufferedWriter(new FileWriter(destinationFile))) {
			final Gson gson = new GsonBuilder().setPrettyPrinting().create();

			writer.write(gson.toJson(info));

			writer.flush();

		}catch(IOException ex) {
			lastException = ex;

			return false;
		}

		return true;
	}

	private static void writeHeader(
		final Writer writer,
		final ApplicationInformation<?> applicationVersion
	) throws IOException{
		writer.write("# ");
		writer.write(applicationVersion.getApplicationName());
		writer.write(" v");
		writer.write(applicationVersion.getApplicationVersion());
		writer.write("\r\n");

		writer.write("#     Status Information\r\n");
		writer.write("#\r\n");

//		writer.write("#");
		writer.write(dateFormat.format(new Date()));
		writer.write("\r\n");
	}

	private static void writeGatewayStatusReport(
		final Writer writer, final GatewayStatusReport gatewayStatusReport
	) throws IOException
	{
		assert writer != null && gatewayStatusReport != null;

		writer.write("[Gateway]\r\n");
		writer.write("# Callsign\r\n");
		writer.write(gatewayStatusReport.getGatewayCallsign());
		writer.write("\r\n");

		if(!gatewayStatusReport.getRouteReports().isEmpty()) {
			writer.write("#    FrameID,FrameStartTime,YourCallsign,Repeater1Callsign.Repeater2Callsign,MyCallsign");
			writer.write("\r\n");

			for(GatewayRouteStatusReport route : gatewayStatusReport.getRouteReports()) {
				writer.write("    ");
				writer.write(String.format("%04X", route.getFrameID()));
				writer.write(",");
				writer.write(String.valueOf(route.getFrameSequenceStartTime() / (long)1000));
				writer.write(",");
				writer.write(route.getYourCallsign());
				writer.write(",");
				writer.write(route.getRepeater1Callsign());
				writer.write(",");
				writer.write(route.getRepeater2Callsign());
				writer.write(",");
				writer.write(route.getMyCallsign());
				writer.write(" ");
				writer.write(route.getMyCallsignAdd());

				writer.write("\r\n");
			}
		}
	}

	private static void writeRoutingServiceStatusReport(
		final Writer writer, final List<RoutingServiceStatusReport> reports
	) throws IOException
	{
		assert writer != null && reports != null;

		writer.write("[RoutingServices]\r\n");
		writer.write("# Type,Status\r\n");
		for(final RoutingServiceStatusReport report : reports) {
			writer.write(report.getServiceType().toString());
			writer.write(",");
			writer.write(report.getServiceStatus().toString());

			writer.write("\r\n");
		}
	}

	private static void writeRepeaterStatusReports(
		final Writer writer,
		final List<RepeaterStatusReport> repeaterStatusReports
	) throws IOException{

		writer.write("[Repeaters]\r\n");
		writer.write("# Callsign,LinkedReflectorCallsign,CurrentRoutingService,RepeaterType");
		writer.write("\r\n");

		List<RepeaterStatusReport> repeaters =
			Stream.of(repeaterStatusReports)
			.sorted(ComparatorCompat.<RepeaterStatusReport, String>comparing(new Function<RepeaterStatusReport, String>(){
				@Override
				public String apply(RepeaterStatusReport repeater) {
					return repeater.getRepeaterCallsign();
				}
			}, ComparatorCompat.<String>naturalOrder()))
			.toList();

		for(RepeaterStatusReport repeater : repeaters) {
			writer.write(repeater.getRepeaterCallsign());
			writer.write(",");
			writer.write(repeater.getLinkedReflectorCallsign());
			writer.write(",");
			writer.write(repeater.getRoutingService().getTypeName());
			writer.write(",");
			writer.write(repeater.getRepeaterType().getTypeName());
			writer.write("\r\n");

			if(!repeater.getRouteReports().isEmpty()) {
				writer.write("#    FrameID,FrameStartTime,YourCallsign,Repeater1Callsign.Repeater2Callsign,MyCallsign");
				writer.write("\r\n");

				for(RepeaterRouteStatusReport route : repeater.getRouteReports()) {
					writer.write("    ");
					writer.write(String.format("%04X", route.getFrameID()));
					writer.write(",");
					writer.write(String.valueOf(route.getFrameSequenceStartTime() / (long)1000));
					writer.write(",");
					writer.write(route.getYourCallsign());
					writer.write(",");
					writer.write(route.getRepeater1Callsign());
					writer.write(",");
					writer.write(route.getRepeater2Callsign());
					writer.write(",");
					writer.write(route.getMyCallsign());
					writer.write(" ");
					writer.write(route.getMyCallsignAdd());

					writer.write("\r\n");
				}
			}
		}
	}

	private static void writeHeardEntries(
		final Writer writer, final List<HeardEntry> entries
	) throws IOException
	{
		writer.write("[HeardLog]\r\n");
		writer.write("# Time,YourCallsign,Repeater1Callsign,Repeater2Callsign,MyCallsign\r\n");

		List<HeardEntry> logs =
		Stream.of(entries)
			.sorted(ComparatorCompat.comparingLong(new ToLongFunction<HeardEntry>() {
				@Override
				public long applyAsLong(HeardEntry entry) {
					return entry.getHeardTime();
				}
			}).reversed())
			.toList();

		for(final HeardEntry entry : logs) {
			writer.write(String.valueOf(TimeUnit.MILLISECONDS.toSeconds(entry.getHeardTime())));
			writer.write(",");
			writer.write(entry.getDirection().toString());
			writer.write(",");
			writer.write(DSTARUtils.formatFullLengthCallsign(entry.getYourCallsign()));
			writer.write(",");
			writer.write(DSTARUtils.formatFullLengthCallsign(entry.getRepeater1Callsign()));
			writer.write(",");
			writer.write(DSTARUtils.formatFullLengthCallsign(entry.getRepeater2Callsign()));
			writer.write(",");
			writer.write(DSTARUtils.formatFullLengthCallsign(entry.getMyCallsignLong()));
			writer.write(" ");
			writer.write(DSTARUtils.formatShortLengthCallsign(entry.getMyCallsignShort()));

			writer.write("\r\n");
		}
	}

	private static void writeLogs(
		final Writer writer, final Deque<LogEntry> logs
	) throws IOException{

		Queue<LogEntry> log;
		synchronized(logs) {
			log = new LinkedList<>();
			for(Iterator<LogEntry> it = logs.descendingIterator(); it.hasNext();)
				log.add(it.next());
		}


		writer.write("[Log]\r\n");

		if(!log.isEmpty()) {
			writer.write("# No,ID,Time,Message");
			writer.write("\r\n");

			int logNumber = 0;
			for(LogEntry entry : log) {

				writer.write("LOG");
				writer.write(String.valueOf(logNumber));
				writer.write(",");

				writer.write(entry.getId().toString());
				writer.write(",");

				writer.write(String.valueOf(entry.getTime() / 1000));
				writer.write(",");

				writer.write(entry.getMessage());

//				writer.write("\r\n");

				logNumber++;
			}
		}

		writer.write("\r\n");
	}

	@Override
	public void notifyLog(String msg) {
		synchronized (logs) {
			while(logs.size() > 50) {
				logs.poll();
			}

			logs.add(new LogEntry(msg));

			logUpdate = true;
		}
	}

	@Override
	public void notifyLogEvent(NotifyLogEvent event) {

	}

}
