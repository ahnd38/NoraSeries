package org.jp.illg.dstar.gateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.gateway.helper.G123Handler;
import org.jp.illg.dstar.gateway.helper.ReflectorHandler;
import org.jp.illg.dstar.gateway.helper.RepeaterHandler;
import org.jp.illg.dstar.gateway.helper.RoutingHandler;
import org.jp.illg.dstar.gateway.helper.StatusFunction;
import org.jp.illg.dstar.gateway.helper.model.GatewayHelperProperties;
import org.jp.illg.dstar.gateway.model.ProcessEntry;
import org.jp.illg.dstar.gateway.model.ProcessStates;
import org.jp.illg.dstar.gateway.tool.announce.AnnounceTool;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.defines.VoiceCharactors;
import org.jp.illg.dstar.reporter.model.GatewayRouteStatusReport;
import org.jp.illg.dstar.reporter.model.GatewayStatusReport;
import org.jp.illg.dstar.routing.define.RoutingServiceResult;
import org.jp.illg.dstar.routing.model.RepeaterRoutingInfo;
import org.jp.illg.dstar.routing.model.UserRoutingInfo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DSTARGatewayHelper {

	private static final Lock instanceLocker = new ReentrantLock();
	private static DSTARGatewayHelper instance;

	@SuppressWarnings("unused")
	private final String logTag;

	private final Map<Integer, ProcessEntry> processEntries;
	private final Lock processEntriesLocker;

	@Setter(AccessLevel.PRIVATE)
	@Getter
	private DSTARGatewayImpl gateway;

	@Getter
	private final AnnounceTool announceTool;

	private final GatewayHelperProperties properties;


	private DSTARGatewayHelper(@NonNull final DSTARGatewayImpl gateway) {
		super();

		logTag = this.getClass().getSimpleName() + "[" + gateway.getGatewayCallsign() + "] : ";

		setGateway(gateway);

		announceTool = new AnnounceTool(gateway.getWorkerExecutor(), gateway);

		processEntriesLocker = new ReentrantLock();
		processEntries = new HashMap<Integer, ProcessEntry>();

		properties = new GatewayHelperProperties();
	}

	public static DSTARGatewayHelper getInstance(final DSTARGatewayImpl gateway) {
		if(gateway == null){throw new IllegalArgumentException();}

		instanceLocker.lock();
		try {
			if (instance != null){
				instance.setGateway(gateway);
				return instance;
			}
			else
				return (instance = new DSTARGatewayHelper(gateway));
		}finally {
			instanceLocker.unlock();
		}
	}

	public void setDisableHeardAtReflector(final boolean disableHeardAtReflector) {
		properties.setDisableHeardAtReflector(disableHeardAtReflector);
	}

	public void setAutoReplaceCQFromReflectorLinkCommand(final boolean autoReplaceCQFromReflectorLinkCommand) {
		properties.setAutoReplaceCQFromReflectorLinkCommand(autoReplaceCQFromReflectorLinkCommand);
	}

	public void setAnnounceCharactor(final VoiceCharactors voiceCharactor) {
		properties.setAnnounceCharactor(voiceCharactor);
	}

	public void announceWakeup() {
		for(DSTARRepeater repeater : getGateway().getRepeaters()) {
			announceTool.announceWakeup(
				repeater,
				properties.getAnnounceCharactor(),
				getGateway().getApplicationName(), getGateway().getApplicationVersion()
			);
		}
	}

	/**
	 * Heard送信が完了したことを通知する
	 *
	 * @param queryID クエリID
	 * @param queryResult クエリ結果
	 */
	public void completeHeard(
		@NonNull final UUID queryID,
		@NonNull final RoutingServiceResult queryResult
	) {
		RoutingHandler.completeHeard(
			getGateway(), processEntriesLocker, processEntries,
			queryID, queryResult
		);
	}

	/**
	 * ユーザークエリの解決を通知する
	 *
	 * @param queryID クエリID
	 * @param queryAnswer クエリ結果
	 */
	public void completeResolveQueryUser(
		@NonNull final UUID queryID,
		@NonNull final UserRoutingInfo queryAnswer
	) {
		RoutingHandler.completeResolveQueryUser(
			getGateway(), processEntriesLocker, processEntries,
			queryID, queryAnswer
		);
	}

	/**
	 * レピータクエリの解決を通知する
	 *
	 * @param queryID クエリID
	 * @param queryAnswer クエリ結果
	 */
	public void completeResolveQueryRepeater(
		@NonNull final UUID queryID,
		@NonNull final RepeaterRoutingInfo queryAnswer
	) {
		RoutingHandler.completeResolveQueryRepeater(
			getGateway(), processEntriesLocker, processEntries,
			queryID, queryAnswer
		);
	}

	/**
	 * リフレクターにリンクした事を通知する
	 *
	 * @param repeaterCallsign レピータコールサイン
	 * @param reflectorCallsign リフレクターコールサイン
	 */
	public void notifyLinkReflector(
		@NonNull final String repeaterCallsign,
		@NonNull final String reflectorCallsign
	) {
		ReflectorHandler.notifyLinkReflector(
			properties, getGateway(),
			repeaterCallsign, reflectorCallsign,
			getAnnounceTool()
		);
	}

	/**
	 * リフレクターから切断した事を通知する
	 *
	 * @param repeaterCallsign レピータコールサイン
	 * @param reflectorCallsign 接続されていたリフレクターコールサイン
	 */
	public void notifyUnlinkReflector(
		@NonNull final String repeaterCallsign,
		@NonNull final String reflectorCallsign
	) {
		ReflectorHandler.notifyUnlinkReflector(
			properties, getGateway(),
			repeaterCallsign, reflectorCallsign,
			getAnnounceTool()
		);
	}

	/**
	 * リフレクターを通信エラーが発生し、切断されたことを通知する
	 *
	 * @param repeaterCallsign レピータコールサイン
	 * @param reflectorCallsign リフレクターコールサイン
	 */
	public void notifyLinkFailedReflector(
		@NonNull final String repeaterCallsign,
		@NonNull final String reflectorCallsign
	) {
		ReflectorHandler.notifyLinkFailedReflector(
			properties, getGateway(),
			repeaterCallsign, reflectorCallsign,
			getAnnounceTool()
		);
	}

	/**
	 * リフレクターからのパケットを処理する
	 *
	 * @param packet DVパケット
	 */
	public void processInputPacketFromReflector(@NonNull final DSTARPacket packet) {
		ReflectorHandler.processInputPacketFromReflector(
			getGateway(), processEntriesLocker, processEntries, packet
		);
	}

	/**
	 * G1/G2コールサインルーティングからのパケットを処理する
	 *
	 * @param packet DVパケット
	 */
	public void processInputPacketFromG123(@NonNull final DSTARPacket packet) {
		G123Handler.processInputPacketFromG123(
			getGateway(), processEntriesLocker, processEntries, packet
		);
	}

	/**
	 * レピータからのパケットを処理する
	 */
	public void processInputPacketFromRepeaters() {
		RepeaterHandler.processInputPacketFromRepeaters(
			properties, getGateway(), processEntriesLocker, processEntries, announceTool
		);
	}

	/**
	 * 定期処理を行う
	 *
	 * 主にタイムアウトなど
	 */
	public void processHelper() {
		processEntriesLocker.lock();
		try {
			for (
				final Iterator<Map.Entry<Integer, ProcessEntry>> it = processEntries.entrySet().iterator();
				it.hasNext();
			) {
				final Map.Entry<Integer, ProcessEntry> mapEntry = it.next();

				mapEntry.getValue().getLocker().lock();
				try {
					if (mapEntry.getValue().isTimeoutActivity()) {
						final ProcessEntry entry = mapEntry.getValue();

						StatusFunction.processStatusHeardEntryTimeout(getGateway(), entry.getFrameID(), entry);

						it.remove();

						if (
							entry.getProcessState() != ProcessStates.Valid &&
							entry.getProcessState() != ProcessStates.Invalid
						) {
							if(log.isDebugEnabled())
								log.debug("Process entry timeout on illegal state.\n" + entry.toString());
						}
						else {
							if(log.isTraceEnabled()) {
								log.trace("Process entry timeout.\n    " + entry.toString());
							}
						}
					}
				}finally {
					mapEntry.getValue().getLocker().unlock();
				}
			}
		}finally {
			processEntriesLocker.unlock();
		}
	}

	/**
	 * アナウンスの処理を行う
	 */
	public void processAnnounce() {
		announceTool.process();
	}

	/**
	 * データ転送状況を取得する
	 * @return データ転送中であればtrue
	 */
	public boolean isDataTransferring() {
		processEntriesLocker.lock();
		try {
			return !processEntries.isEmpty();
		}finally {
			processEntriesLocker.unlock();
		}
	}

	public List<String> getRouterStatus(){
		processEntriesLocker.lock();
		try {
			final List<String> routerStatus = new ArrayList<>(processEntries.size());

			for(ProcessEntry entry : this.processEntries.values()) {
				final StringBuilder sb = new StringBuilder();
				sb.append("Mode:");
				sb.append(entry.getProcessMode().toString());
				sb.append(" / ");
				sb.append("ID:");
				sb.append(String.format("%04X", entry.getFrameID()));
				sb.append(" / ");
				sb.append("Time:");
				sb.append(String.format(
					"%3d",
					(int)(System.currentTimeMillis() - entry.getCreatedTimestamp()) / (int)1000
				));
				sb.append("s");
				sb.append(" / ");
				if(entry.getHeaderPacket() != null) {
					final Header header = entry.getHeaderPacket().getRFHeader();
					sb.append("UR:");
					sb.append(header.getYourCallsign());
					sb.append(" / ");
					sb.append("RPT1:");
					sb.append(header.getRepeater1Callsign());
					sb.append(" / ");
					sb.append("RPT2:");
					sb.append(header.getRepeater2Callsign());
					sb.append(" / ");
					sb.append("MY:");
					sb.append(header.getMyCallsign());
					sb.append(" ");
					sb.append(header.getMyCallsignAdd());
				}else {sb.append("Header:nothing");}

				routerStatus.add(sb.toString());
			}

			return routerStatus;
		}finally {
			processEntriesLocker.unlock();
		}
	}

	public GatewayStatusReport getGatewayStatusReport(){
		final GatewayStatusReport report = new GatewayStatusReport();

		report.setGatewayCallsign(getGateway().getGatewayCallsign());
		report.setLastHeardCallsign(getGateway().getLastHeardCallsign());
		report.setScope(getGateway().getScope());
		report.setLatitude(getGateway().getLatitude());
		report.setLongitude(getGateway().getLongitude());
		report.setDescription1(getGateway().getDescription1());
		report.setDescription2(getGateway().getDescription2());
		report.setUrl(getGateway().getUrl());
		report.setName(getGateway().getName());
		report.setLocation(getGateway().getLocation());
		report.setDashboardUrl(getGateway().getDashboardUrl());

		report.setUseProxy(getGateway().isUseProxy());
		report.setProxyServerAddress(getGateway().getProxyServerAddress());
		report.setProxyServerPort(getGateway().getProxyServerPort());

		for(RoutingService service : getGateway().getRoutingServiceAll())
			report.getRoutingServiceReports().add(service.getRoutingServiceStatusReport());

		report.getHeardReports().addAll(getGateway().getHeardEntries());

		processEntriesLocker.lock();
		try {
			for (ProcessEntry entry : processEntries.values()) {
				GatewayRouteStatusReport routeReport = new GatewayRouteStatusReport();

				routeReport.setRouteMode(entry.getProcessMode().toString());
				routeReport.setFrameID(entry.getFrameID());
				routeReport.setFrameSequenceStartTime(entry.getCreatedTimestamp());
				if(entry.getHeaderPacket() != null){
					routeReport.setYourCallsign(String.valueOf(entry.getHeaderPacket().getRFHeader().getYourCallsign()));
					routeReport.setRepeater1Callsign(String.valueOf(entry.getHeaderPacket().getRFHeader().getRepeater1Callsign()));
					routeReport.setRepeater2Callsign(String.valueOf(entry.getHeaderPacket().getRFHeader().getRepeater2Callsign()));
					routeReport.setMyCallsign(String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()));
					routeReport.setMyCallsignAdd(String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsignAdd()));
				}

				report.getRouteReports().add(routeReport);
			}
		}finally {
			processEntriesLocker.unlock();
		}

		return report;
	}
}
