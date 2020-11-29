package org.jp.illg.dstar.repeater.homeblew;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.config.CallsignEntry;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.repeater.DSTARRepeaterBase;
import org.jp.illg.dstar.repeater.homeblew.model.HRPPacket;
import org.jp.illg.dstar.repeater.homeblew.model.HRPPacketType;
import org.jp.illg.dstar.repeater.homeblew.model.RouteEntry;
import org.jp.illg.dstar.repeater.homeblew.model.RouteStatus;
import org.jp.illg.dstar.repeater.homeblew.protocol.HomebrewRepeaterProtocolProcessor;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlHomeblewHandler;
import org.jp.illg.dstar.service.web.model.HomeblewRepeaterStatusData;
import org.jp.illg.dstar.service.web.model.RepeaterStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadProcessResult;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HomeblewRepeater extends DSTARRepeaterBase
implements WebRemoteControlHomeblewHandler{

	private final String logTag = HomeblewRepeater.class.getSimpleName() + " : ";

	private final Pattern myCallValidatePattern =
		Pattern.compile("^[A-Z0-9]{1}[A-Z0-9]{0,1}[0-9]{1,2}[A-Z]{1,4} {0,4}[ A-Z]{1}$");

	private HomebrewRepeaterProtocolProcessor hbProtocol;

	private Queue<DSTARPacket> gatewayToRepeaterPackets;
	private Queue<DSTARPacket> repeaterToGatewayPackets;

	private final Map<Integer, RouteEntry> routeEntries;
	private final Timer routeEntriesCleanupTimeKeeper;

	@Getter
	@Setter
	private InetAddress remoteRepeaterAddress;
	private static final InetAddress remoteRepeaterAddressDefault = null;
	public static final String remoteRepeaterAddressPropertyName = "RemoteRepeaterAddress";

	@Getter
	@Setter
	private int remoteRepeaterPort;
	private static final int remoteRepeaterPortDefault = 0;
	public static final String remoteRepeaterPortPropertyName = "RemoteRepeaterPort";

	@Getter
	@Setter
	private int localPort;
	private static final int localPortDefault = 0;
	public static final String localPortPropertyName = "LocalPort";

	private final List<String> accessWhiteList;


	public HomeblewRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener
	) {
		this(systemID, gateway, repeaterCallsign, workerExecutor, eventListener, null);
	}

	public HomeblewRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener,
		SocketIO socketIO
	) {
		super(HomeblewRepeater.class, systemID, gateway, repeaterCallsign, workerExecutor, eventListener, socketIO);

		gatewayToRepeaterPackets = new LinkedList<>();
		repeaterToGatewayPackets = new LinkedList<>();
		routeEntries = new ConcurrentHashMap<>();
		routeEntriesCleanupTimeKeeper = new Timer(10, TimeUnit.SECONDS);

		accessWhiteList = new ArrayList<>();

		setRemoteRepeaterAddress(remoteRepeaterAddressDefault);
		setRemoteRepeaterPort(remoteRepeaterPortDefault);

		setLocalPort(localPortDefault);
	}

	@Override
	public void wakeupRepeaterWorker() {
		super.wakeupRepeaterWorker();
	}

	@Override
	public boolean setProperties(RepeaterProperties properties) {

		String remoteAddress =
				properties.getConfigurationProperties().getProperty(remoteRepeaterAddressPropertyName);
		try {
			if(remoteAddress != null)
				setRemoteRepeaterAddress(InetAddress.getByName(remoteAddress));
		} catch (UnknownHostException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not set " + remoteRepeaterAddressPropertyName + " = " + remoteAddress + ".");
		}

		String remotePort = properties.getConfigurationProperties().getProperty(remoteRepeaterPortPropertyName);
		try {
			setRemoteRepeaterPort(Integer.valueOf(remotePort));
		}catch(NumberFormatException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not set " + remoteRepeaterPortPropertyName + " = " + remotePort + ".");
		}

		String localPort = properties.getConfigurationProperties().getProperty(localPortPropertyName);
		try {
			setLocalPort(Integer.valueOf(localPort));
		}catch(NumberFormatException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not set " + localPortPropertyName + " = " + localPort + ".");
		}

		//アクセスホワイトリストを流し込み
		accessWhiteList.addAll(
			Stream.of(properties.getAccessAllowList())
			.filter(new Predicate<CallsignEntry>() {
				@Override
				public boolean test(CallsignEntry e) {
					if(!e.isEnable()) {return false;}

					final String callsign = DSTARUtils.formatFullCallsign(e.getCallsign(), ' ');
					final boolean isCallsignValid = CallSignValidator.isValidUserCallsign(callsign);
					if(!isCallsignValid) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Illegal callsign " + callsign + " in access white list.");
					}
					return isCallsignValid;
				}
			})
			.map(new Function<CallsignEntry, String>(){
				@Override
				public String apply(CallsignEntry e) {
					return DSTARUtils.formatFullCallsign(e.getCallsign(), ' ');
				}

			}).toList()
		);

		return super.setProperties(properties);
	}

	@Override
	public RepeaterProperties getProperties(RepeaterProperties properties) {
		if(getRemoteRepeaterAddress() != null)
			properties.getConfigurationProperties().setProperty(remoteRepeaterAddressPropertyName, getRemoteRepeaterAddress().getHostAddress());

		properties.getConfigurationProperties().setProperty(remoteRepeaterPortPropertyName, String.valueOf(getRemoteRepeaterPort()));

		properties.getConfigurationProperties().setProperty(localPortPropertyName, String.valueOf(getRemoteRepeaterPort()));

		return super.getProperties(properties);
	}

	@Override
	public boolean start() {
		if(isRunning()){
			if(log.isDebugEnabled())
				log.debug(logTag + "Already running.");

			return true;
		}

		if(getLocalPort() < 1024) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not set localPort = " + getLocalPort() + ",must set to over port number 1024.");

			return false;
		}

		if(getRemoteRepeaterAddress() == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "RemoteRepeaterAddress is not set, must be set.");

			return false;
		}

		if(getRemoteRepeaterPort() < 1024) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not set RemoteRepeaterPort = " + getRemoteRepeaterPort() + ",must set to over port number 1024.");

			return false;
		}

		if(hbProtocol != null && hbProtocol.isRunning()){hbProtocol.stop();}

		hbProtocol =
			new HomebrewRepeaterProtocolProcessor(this, this, getLocalPort(), getSocketIO());
		if(
			!hbProtocol.start() ||
			!super.start()
		){
			hbProtocol.stop();
			super.stop();
			return false;
		}

		return true;
	}

	public void stop() {
		super.stop();

		if(this.hbProtocol != null && this.hbProtocol.isRunning()) {this.hbProtocol.stop();}
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return super.threadInitialize();
	}

	@Override
	protected ThreadProcessResult processRepeater() {

		synchronized(repeaterToGatewayPackets) {
			HRPPacket hrpPacket = null;
			while((hrpPacket = hbProtocol.readPacket()) != null) {

				if(
					hrpPacket.getHrpPacketType() != HRPPacketType.Header &&
					hrpPacket.getHrpPacketType() != HRPPacketType.AMBE
				) {continue;}

				final int frameID = hrpPacket.getDVPacket().getBackBone().getFrameIDNumber();
				final RouteEntry entry = routeEntries.get(frameID);

				switch(hrpPacket.getHrpPacketType()) {
				case Header:
					if(entry != null) {continue;}

					//規格外の文字を置換
					hrpPacket.getDVPacket().getRfHeader().replaceCallsignsIllegalCharToSpace();

					final RouteEntry newEntry =
						new RouteEntry(
							frameID,
							checkModemHeader(hrpPacket.getDVPacket().getRfHeader(), frameID) ?
								RouteStatus.Valid : RouteStatus.Invalid,
							hrpPacket.getDVPacket()
						);
					newEntry.getActivityTime().setTimeoutTime(2, TimeUnit.SECONDS);
					newEntry.getActivityTime().updateTimestamp();

					routeEntries.put(frameID, newEntry);

					if(newEntry.getRouteStatus() == RouteStatus.Valid) {
						setLastHeardCallsign(
							DSTARUtils.formatFullLengthCallsign(
								String.valueOf(hrpPacket.getDVPacket().getRfHeader().getMyCallsign())
							)
						);

						hrpPacket.setLoopBlockID(newEntry.getLoopBlockID());

						repeaterToGatewayPackets.add(hrpPacket);
					}

					break;

				case AMBE:
					if(entry == null) {continue;}

					entry.getActivityTime().updateTimestamp();

					if(entry.getRouteStatus() == RouteStatus.Valid)
						hrpPacket.setLoopBlockID(entry.getLoopBlockID());

						repeaterToGatewayPackets.add(hrpPacket);

					if(hrpPacket.getDVPacket().isEndVoicePacket())
						routeEntries.remove(frameID);

					break;

				default:
					break;
				}
			}
		}

		synchronized(gatewayToRepeaterPackets) {
			for(final Iterator<DSTARPacket> it = gatewayToRepeaterPackets.iterator(); it.hasNext();) {
				final DSTARPacket packet = it.next();
				it.remove();

				if(packet.getPacketType() != DSTARPacketType.DV) {continue;}

				if(packet.getDVPacket().hasPacketType(PacketType.Header)) {
					hbProtocol.writeHeader(getRemoteRepeaterAddress(), getRemoteRepeaterPort(), packet);
				}
				if(packet.getDVPacket().hasPacketType(PacketType.Voice)) {
					hbProtocol.writeAMBE(getRemoteRepeaterAddress(), getRemoteRepeaterPort(), packet);
				}
			}
		}

		//タイムアウトしたルートエントリを掃除する
		if(routeEntriesCleanupTimeKeeper.isTimeout()) {
			routeEntriesCleanupTimeKeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			routeEntriesCleanupTimeKeeper.updateTimestamp();

			for(Iterator<Map.Entry<Integer, RouteEntry>> it = routeEntries.entrySet().iterator(); it.hasNext();) {
				Map.Entry<Integer, RouteEntry> entry = it.next();

				if(entry.getValue().getActivityTime().isTimeout()) {it.remove();}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		return;
	}

	@Override
	protected boolean isCanSleep() {
		return routeEntries.isEmpty();
	}

	@Override
	protected boolean isAutoWatchdog() {
		return true;
	};

	@Override
	public DSTARPacket readPacket() {
		synchronized(repeaterToGatewayPackets) {
			return repeaterToGatewayPackets.poll();
		}
	}

	@Override
	public boolean hasReadPacket() {
		synchronized(repeaterToGatewayPackets) {
			return !repeaterToGatewayPackets.isEmpty();
		}
	}

	@Override
	public boolean writePacket(DSTARPacket packet) {
		if(packet == null) {return false;}

		boolean success = false;

		synchronized(gatewayToRepeaterPackets) {
			success = gatewayToRepeaterPackets.add(packet);
		}

		if(success) {wakeupProcessThread();}

		return success;
	}

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public boolean isReflectorLinkSupport() {
		return true;
	}

	@Override
	public List<String> getRouterStatus() {

		return new ArrayList<>();
	}

	@Override
	public boolean initializeWebRemote(WebRemoteControlService service) {
		return service.initializeRepeaterHomeblew(this);
	}

	@Override
	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	) {

	}

	@Override
	public void threadUncaughtExceptionEvent(Exception ex, Thread thread) {
		if(super.getExceptionListener() != null)
			super.getExceptionListener().threadUncaughtExceptionEvent(ex, thread);
	}

	@Override
	public void threadFatalApplicationErrorEvent(String message, Exception ex, Thread thread) {
		if(super.getExceptionListener() != null)
			super.getExceptionListener().threadFatalApplicationErrorEvent(message, ex, thread);
	}

	@Override
	public RepeaterStatusReport getRepeaterStatusReportInternal(
		RepeaterStatusReport report
	){

		report.setRepeaterCallsign(String.valueOf(getRepeaterCallsign()));
		report.setLinkedReflectorCallsign(getLinkedReflectorCallsign() != null ? getLinkedReflectorCallsign() : "");
		report.setRoutingService(
			getRoutingService() != null ? getRoutingService().getServiceType() : RoutingServiceTypes.Unknown
		);
		report.setRepeaterType(getRepeaterType());

		report.getRepeaterProperties().put("isUseRoutingService", String.valueOf(isUseRoutingService()));

		return report;
	}

	@Override
	protected RepeaterStatusData createStatusDataInternal() {
		final HomeblewRepeaterStatusData status = new HomeblewRepeaterStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends RepeaterStatusData> getStatusDataTypeInternal() {
		return HomeblewRepeaterStatusData.class;
	}

	@Override
	public boolean isDataTransferring() {
		return !routeEntries.isEmpty();
	}

	private boolean checkModemHeader(
		@NonNull final Header header, final int frameID
	) {
		assert header != null;

		boolean result = false;

		final String myCallsign = String.valueOf(header.getMyCallsign());
		final String yourCallsign = String.valueOf(header.getYourCallsign());
		final String repeater1Callsign = String.valueOf(header.getRepeater1Callsign());
		final String repeater2Callsign = String.valueOf(header.getRepeater2Callsign());

		final boolean validMyCallsign =
			DSTARUtils.formatFullCallsign(getGateway().getGatewayCallsign(), ' ').equals(
				DSTARUtils.formatFullCallsign(myCallsign, ' ')
			) || containsCallsignInAccessWhiteList(myCallsign);

		if(!header.isSetRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL)) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Repeater:" + super.getRepeaterCallsign() +
					" received header has control flag, ignore.\n" + header.toString(4)
				);
			}

			result = false;
		}
		else if(
			!header.isSetRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL)
		){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Repeater:" + super.getRepeaterCallsign() +
					" received non terminal header from modem...\n" + header.toString(4)
				);
			}

			result = false;
		}
		else if(
			DSTARDefines.EmptyLongCallsign.equals(myCallsign) ||
			super.getGateway().getGatewayCallsign().equals(myCallsign) ||
			myCallsign.startsWith("NOCALL") ||
			myCallsign.startsWith("MYCALL") ||
			!myCallValidatePattern.matcher(myCallsign).matches()
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Repeater:" + super.getRepeaterCallsign() +
					" received invalid my callsign header from modem...\n" + header.toString(4)
				);
			}

			result = false;
		}
		else if(
			!CallSignValidator.isValidUserCallsign(myCallsign)
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Repeater:" + super.getRepeaterCallsign() +
					" received invalid my callsign header from modem...\n" + header.toString(4)
				);
			}

			result = false;
		}
		else if(
			DSTARDefines.EmptyLongCallsign.equals(yourCallsign)
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Repeater:" + super.getRepeaterCallsign() +
					" received invalid empty your callsign header from modem...\n" + header.toString(4)
				);
			}

			result = false;
		}
		else if(
			(
				super.getRepeaterCallsign().equals(repeater1Callsign) &&
				super.getGateway().getGatewayCallsign().equals(repeater2Callsign)
			) ||
			(
				super.getRepeaterCallsign().equals(repeater1Callsign) &&
				super.getRepeaterCallsign().equals(repeater2Callsign)
			)
		) {
			if(!validMyCallsign) {
				if(log.isInfoEnabled()) {
					log.info(
						logTag +
						"Block user " + myCallsign + " / Repeater:" + super.getRepeaterCallsign() + ", not found in access white list."
					);
				}
			}

			result = validMyCallsign;
		}
		else {
			if(log.isInfoEnabled()) {
				log.info(
					logTag +
					"Repeater:" + super.getRepeaterCallsign() +
					" received invalid header from modem...\n" + header.toString(4)
				);
			}

			result = false;
		}

		return result;
	}

	private boolean containsCallsignInAccessWhiteList(final String userCallsign) {
		final String targetCallsign = DSTARUtils.formatFullCallsign(userCallsign, ' ');

		return Stream.of(accessWhiteList)
			.anyMatch(new Predicate<String>() {
				@Override
				public boolean test(String callsign) {
					return callsign.equals(targetCallsign);
				}
			});
	}
}
