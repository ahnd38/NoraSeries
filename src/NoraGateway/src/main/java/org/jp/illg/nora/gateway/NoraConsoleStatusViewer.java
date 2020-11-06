package org.jp.illg.nora.gateway;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SystemUtils;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.DSTARGatewayManager;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.ReflectorCommunicationServiceManager;
import org.jp.illg.dstar.routing.RoutingServiceManager;
import org.jp.illg.dstar.routing.model.RoutingServiceServerStatus;
import org.jp.illg.dstar.routing.model.RoutingServiceStatusData;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.logback.appender.NotifyAppender;
import org.jp.illg.util.logback.appender.NotifyAppenderListener;
import org.jp.illg.util.logback.appender.NotifyLogEvent;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import android.annotation.SuppressLint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class NoraConsoleStatusViewer extends ThreadBase implements NotifyAppenderListener {

	private static final long consoleRefleshIntervalMillis = TimeUnit.MILLISECONDS.toMillis(500);
	private static final int displayLatestLogLimit = 5;

	private boolean visibleLogOnly;

	private class NotifyEventEntry{
		@Getter
		@Setter(AccessLevel.PRIVATE)
		private String formatedMessage;

		public NotifyEventEntry(String formatedMessage) {
			super();
			setFormatedMessage(formatedMessage);
		}
	}

	private final UUID systemID;

	private final ApplicationInformation<?> applicationVersion;

	private final Queue<NotifyEventEntry> logEvents;

	private final Deque<NotifyEventEntry> displayingLogEvents;

	private final String consoleHeader;

	private BufferedOutputStream console;

	private String displayInformationBuffer;


	public NoraConsoleStatusViewer(
		@NonNull final UUID systemID,
		final boolean visibleLogOnly,
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationVersion
	) {
		super(exceptionListener, NoraConsoleStatusViewer.class.getSimpleName());

		this.systemID = systemID;

		this.visibleLogOnly = visibleLogOnly;

		setProcessLoopIntervalTimeMillis(consoleRefleshIntervalMillis);

		this.applicationVersion = applicationVersion;
		this.logEvents = new LinkedList<>();
		this.displayingLogEvents = new LinkedList<>();

		if(!visibleLogOnly) {
			final StringBuilder sb = new StringBuilder();
			sb.append("****************************************\n");
			sb.append("* ");
			sb.append(this.applicationVersion.getApplicationName());
			sb.append("@");
			sb.append(this.applicationVersion.getRunningOperatingSystem());
			sb.append((" v"));
			sb.append(this.applicationVersion.getApplicationVersion());
			sb.append("\n");

			sb.append(("*   "));
			sb.append(this.applicationVersion.isGitDirty() ? "[!]" : "");
			sb.append(this.applicationVersion.getGitBranchName());
			sb.append("@");
			sb.append(this.applicationVersion.getGitCommitID());
			sb.append(" by ");
			sb.append(this.applicationVersion.getGitCommitterName());
			sb.append("\n");
			sb.append("****************************************\n");

			consoleHeader = sb.toString();
		}
		else {
			consoleHeader = "";
		}
	}

	@Override
	protected ThreadProcessResult threadInitialize() {

		this.console = new BufferedOutputStream(System.out);

		NotifyAppender.addListener(this);

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult process() {
//		byte[] t = new byte[] {};
//		log.trace(String.valueOf(t[1]));

		final StringBuilder sb = new StringBuilder();

		try {
			if(!this.visibleLogOnly) {
				sb.append(consoleHeader);

				//Gateway information
				getGatewayInformation(sb);

				sb.append("\n");

				//Repeater informations
				getRepeatersInformation(sb);

				sb.append("\n");

				//Routing service informations
				getRoutingServicesInformation(sb);

				sb.append("\n");

				//Reflector processor informations
				getReflectorProcessorsInformation(sb);

				sb.append("\n");

				//Latest logs
				getLatestLogs(sb);
			}
			else {
				getLogs(sb);
			}

			String newInformation = sb.toString();
			if(this.displayInformationBuffer == null || !newInformation.equals(this.displayInformationBuffer)) {
				if(!visibleLogOnly) { clearConsole(console); }

				console.write(newInformation.getBytes());
				console.flush();

				this.displayInformationBuffer = newInformation;
			}
		}catch(IOException ex) {
			return threadFatalError("Console I/O error.", ex);
		}catch (InterruptedException ex){
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		NotifyAppender.removeListener(this);

		return;
	}

	private void getGatewayInformation(StringBuilder sb) {
		assert sb != null;

		final DSTARGateway gateway = DSTARGatewayManager.getGateway(systemID);

		sb.append("[Gateway]:\n");
		if(gateway != null) {
			sb.append(("  Callsign:" + gateway.getGatewayCallsign()));
			sb.append("\n");

			if(gateway.getRouterStatus() != null) {
				for(String routerStatus : gateway.getRouterStatus()) {
					sb.append("  -->");
					sb.append(routerStatus);
					sb.append("\n");
				}
			}
		}else{sb.append("  Nothing gateway information\n");}

		return;
	}

	private void getRepeatersInformation(StringBuilder sb) {
		assert sb != null;

		sb.append("[Repeaters]:\n");

		 final DSTARGateway gateway = DSTARGatewayManager.getGateway(systemID);
		 if(gateway == null) {return;}

		for(final DSTARRepeater repeater : gateway.getRepeaters()) {
			if(repeater != null) {
				sb.append("  Callsign:");
				sb.append(repeater.getRepeaterCallsign());
				sb.append(" / LinkedReflector:");
				if(repeater.isReflectorLinkSupport()) {
					sb.append(repeater.getLinkedReflectorCallsign());
				}else {
					for(int i = 0; DSTARDefines.CallsignFullLength > i; i++) {sb.append("-");}
				}
				if(repeater.getRoutingService() != null) {
					sb.append(" / CurrentRoutingService:");
					sb.append(repeater.getRoutingService().getServiceType().toString());
				}
				sb.append(" / Type:");
				sb.append(repeater.getRepeaterType().toString());
				sb.append("\n");

				if(repeater.getRouterStatus() != null) {
					for(String routerStatus : repeater.getRouterStatus()) {
						sb.append("  -->");
						sb.append(routerStatus);
						sb.append("\n");
					}
				}
			}
		}

		return;
	}

	private void getRoutingServicesInformation(StringBuilder sb) {
		assert sb != null;

		sb.append("[AvailableRoutingServices]:\n");
		for(final RoutingService routingService : RoutingServiceManager.getServices(systemID)) {
			sb.append("  ");
			sb.append("Type:");
			sb.append(routingService.getServiceType().getTypeName());

			final RoutingServiceStatusData routingStatus =
				routingService.getServiceStatus();
			for(final RoutingServiceServerStatus st : routingStatus.getServiceStatus()) {
				sb.append("\n    ");
				sb.append("Status:");
				sb.append(st.getServiceStatus());

				sb.append(" / ");

				sb.append("Server:");
				if(st.isUseProxyGateway()) {
					sb.append(st.getProxyGatewayAddress());
					sb.append(":");
					sb.append(st.getProxyGatewayPort());
					sb.append("(Proxy)");
				}
				else {
					sb.append(st.getServerAddress());
					sb.append(":");
					sb.append(st.getServerPort());
				}
			}

			sb.append("\n");
		}

		return;
	}

	private void getReflectorProcessorsInformation(StringBuilder sb) {
		assert sb != null;

		sb.append("[AvailableReflectorCommunicationServices]:\n");
		for(ReflectorCommunicationService reflector : ReflectorCommunicationServiceManager.getServices(systemID)) {
			sb.append("  ");
			sb.append("Protocol:");
			sb.append(reflector.getProtocolType());
			sb.append(" / ");
			sb.append("Status:");
			sb.append(reflector.getStatus().toString());
			sb.append("\n");
		}

		return;
	}

	private void getLatestLogs(StringBuilder sb) {
		assert sb != null;

		sb.append(("[Latest " + displayLatestLogLimit +  "logs]:\n"));

		synchronized(this.logEvents) {
			while(this.logEvents.size() > displayLatestLogLimit) {this.logEvents.poll();}
			for(Iterator<NotifyEventEntry> it = this.logEvents.iterator(); it.hasNext();) {
				NotifyEventEntry event = it.next();
				it.remove();
				this.displayingLogEvents.addLast(event);
			}
		}
		while(this.displayingLogEvents.size() > displayLatestLogLimit) {this.displayingLogEvents.pollFirst();}
		for(Iterator<NotifyEventEntry> it = this.displayingLogEvents.descendingIterator(); it.hasNext();)
			sb.append((it.next().getFormatedMessage()));

		return;
	}

	private void getLogs(StringBuilder sb) {
		assert sb != null;

		synchronized(this.logEvents) {
			for(Iterator<NotifyEventEntry> it = this.logEvents.iterator(); it.hasNext();) {
				NotifyEventEntry event = it.next();
				it.remove();
				sb.append(event.getFormatedMessage());
			}
		}

		return;
	}

	@Override
	public void notifyLog(String msg) {
		synchronized(this.logEvents) {
			while(this.logEvents.size() >= 1000) {this.logEvents.poll();}
			this.logEvents.add(new NotifyEventEntry(msg));
		}

		super.wakeupProcessThread();
	}

	@Override
	public void notifyLogEvent(NotifyLogEvent event) {

	}

	@SuppressLint("NewAPI")
	private boolean clearConsole(OutputStream console) throws IOException, InterruptedException{
		if(console == null){return false;}

		if (SystemUtils.IS_OS_WINDOWS)
			new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
		else
			console.write("\033[2J\033[;H".getBytes());

		return true;
	}

}
