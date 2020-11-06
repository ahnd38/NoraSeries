package org.jp.illg.dstar.repeater.internal;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.ModemTransceiverMode;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.RepeaterModem;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.config.ModemProperties;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.repeater.DSTARRepeaterBase;
import org.jp.illg.dstar.repeater.internal.model.ProcessEntry;
import org.jp.illg.dstar.repeater.internal.model.ProcessMode;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.repeater.modem.DStarRepeaterModemEvent;
import org.jp.illg.dstar.reporter.model.RepeaterRouteStatusReport;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlInternalRepeaterHandler;
import org.jp.illg.dstar.service.web.model.InternalRepeaterStatusData;
import org.jp.illg.dstar.service.web.model.RepeaterStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.CommandDetector;
import org.jp.illg.dstar.util.CommandType;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.NewDataSegmentEncoder;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InternalRepeater extends DSTARRepeaterBase
implements WebRemoteControlInternalRepeaterHandler
{
	private static final long minimumTransmissionTimeSeconds = 2;
	private static final int packetCacheLimit = 100;
	private static final long processEntryTimeoutMillis = TimeUnit.SECONDS.toMillis(2);

	@Data
	@AllArgsConstructor
	private static class ReplyRequest{
		private ProcessEntry entry;
		private boolean success;
	}

	private final String logTag;

	private final Lock locker;

	private final Queue<DSTARPacket> fromInetPackets;

	private final Map<Integer, ProcessEntry> processEntries;
	private ProcessEntry downlinkEntry;
	private ProcessEntry uplinkEntry;
	private final Map<RepeaterModem, ProcessEntry> modemEntries;
	private ProcessEntry flagReplyEntry;
	private final Queue<ReplyRequest> replyFlagRequests;

	private final Timer processIntervalTimer;

	private final EventListener<DStarRepeaterModemEvent> modemEventListener =
		new EventListener<DStarRepeaterModemEvent>() {
			@Override
			public void event(DStarRepeaterModemEvent event, Object attachment) {
				wakeupProcessThread();
			}
		};

	public InternalRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener
	) {
		this(systemID, gateway, repeaterCallsign, workerExecutor, eventListener, null);
	}

	public InternalRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener,
		SocketIO socketIO
	) {
		super(InternalRepeater.class, systemID, gateway, repeaterCallsign, workerExecutor, eventListener, socketIO);

		logTag = InternalRepeater.class.getSimpleName() + "(" + repeaterCallsign + ") : ";

		locker = new ReentrantLock();

		processEntries = new HashMap<>();

		fromInetPackets = new LinkedList<>();

		replyFlagRequests = new LinkedList<>();

		flagReplyEntry = null;

		downlinkEntry = null;
		uplinkEntry = null;
		modemEntries = new HashMap<>();

		processIntervalTimer = new Timer();
	}

	@Override
	public void wakeupRepeaterWorker() {
		super.wakeupRepeaterWorker();
	}

	@Override
	public boolean start() {
		if(!super.start()) {return false;}

		return true;
	}

	@Override
	public void stop() {
		super.stop();
	}

	@Override
	protected ThreadProcessResult threadInitialize() {

		return super.threadInitialize();
	}

	@Override
	protected void threadFinalize() {

	}

	@Override
	public boolean initializeWebRemote(@NonNull final WebRemoteControlService service) {

		if(!service.initializeRepeaterInternal(this))
			return false;

		for(final RepeaterModem modem : getModems()) {
			if(modem.initializeWebRemoteControl(service) == null)
				return false;
		}

		return true;
	}

	@Override
	public boolean setProperties(RepeaterProperties properties) {
		if(!super.setProperties(properties)) {return false;}

		boolean isModemLoadSuccess = true;
		for(final ModemProperties modemProperties : properties.getModemProperties()) {
			if(!modemProperties.isEnable()) {continue;}

			final RepeaterModem modem = createModemInstance(
				modemProperties.getType() != null ? modemProperties.getType() : ""
			);
			if(modem == null) {continue;}

			modem.setGatewayCallsign(getGateway().getGatewayCallsign());
			modem.setRepeaterCallsign(getRepeaterCallsign());

			modem.setAllowDIRECT(modemProperties.isAllowDIRECT());

			modem.setScope(AccessScope.getTypeByTypeNameIgnoreCase(modemProperties.getScope()));

			if(
				!modem.setProperties(modemProperties) ||
				!addRepeaterModem(modem)
			) {
				isModemLoadSuccess = false;

				if(log.isWarnEnabled())
					log.warn(logTag + "Could not initialize modem class..." + modem.getModemType() + ".");

				break;
			}
		}

		return isModemLoadSuccess;
	}

	@Override
	public RepeaterProperties getProperties(RepeaterProperties properties) {
		if(properties == null) {return null;}

		return super.getProperties(properties);
	}

	@Override
	public boolean writePacket(@NonNull DSTARPacket packet) {
		if(
			packet.getPacketType() != DSTARPacketType.DV ||
			(
				!packet.getDVPacket().hasPacketType(PacketType.Voice) &&
				!packet.getDVPacket().hasPacketType(PacketType.Header)
			)
		) {return false;}

		boolean isSuccess = false;

		locker.lock();
		try {
			while(fromInetPackets.size() > packetCacheLimit) {fromInetPackets.poll();}

			isSuccess = fromInetPackets.add(packet);
		}finally {
			locker.unlock();
		}

		processDownlinkPacket();

		return isSuccess;
	}

	@Override
	protected ThreadProcessResult processRepeater() {

		processUplinkPacket();

		processDownlinkPacket();

		processFlagReply();

		//タイムアウトしたプロセスエントリを削除する
		if(processIntervalTimer.isTimeout(100, TimeUnit.MILLISECONDS)) {
			removeProcessEntry();

			processIntervalTimer.updateTimestamp();
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	) {
		for(final RepeaterModem modem : getModems()) {
			final List<ReflectorRemoteUserEntry> copyUsers =
				Stream.of(users).map(new Function<ReflectorRemoteUserEntry, ReflectorRemoteUserEntry>(){
					@Override
					public ReflectorRemoteUserEntry apply(ReflectorRemoteUserEntry t) {
						return t.clone();
					}
				}).toList();

			getWorkerExecutor().submit(new RunnableTask(getExceptionListener()) {
				@Override
				public void task() {
					modem.notifyReflectorLoginUsers(
						reflectorType,
						protocol,
						remoteCallsign, connectionDir, copyUsers
					);
				}
			});
		}
	}

	@Override
	protected boolean isAutoWatchdog() {
		return true;
	}

	@Override
	public boolean isBusy() {
		return uplinkEntry != null || downlinkEntry != null || flagReplyEntry != null;
	}

	@Override
	public boolean isReflectorLinkSupport() {
		return true;
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
	public List<String> getRouterStatus(){
		final List<String> routerStatus = new ArrayList<>();

		locker.lock();
		try {
			for(final ProcessEntry entry : this.processEntries.values()) {
				if(entry.getProcessMode() == ProcessMode.FlagReply) {continue;}

				final StringBuilder sb = new StringBuilder();
				sb.append("Mode:");
				sb.append(entry.getProcessMode().toString());
				sb.append(" / ");
				sb.append("ID:");
				sb.append(String.format("%04X", entry.getFrameID()));
				sb.append(" / ");
				sb.append("Time:");
				sb.append(
					String.format(
						"%3d",
						(int)(System.currentTimeMillis() - entry.getCreatedTimestamp()) / (int)1000
					)
				);
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
		}finally {
			locker.unlock();
		}

		return routerStatus;
	}

	@Override
	public RepeaterStatusReport getRepeaterStatusReportInternal(
		RepeaterStatusReport report
	){
		locker.lock();
		try {
			for (final ProcessEntry entry : this.processEntries.values()) {
				if(entry.getProcessMode() == ProcessMode.FlagReply) {continue;}

				final RepeaterRouteStatusReport routeReport = new RepeaterRouteStatusReport();

				routeReport.setRouteMode(entry.getProcessMode().toString());
				routeReport.setFrameID(entry.getFrameID());
				routeReport.setFrameSequenceStartTime(entry.getCreatedTimestamp());
				if(entry.getHeaderPacket() != null){
					final Header header = entry.getHeaderPacket().getRFHeader();
					routeReport.setYourCallsign(String.valueOf(header.getYourCallsign()));
					routeReport.setRepeater1Callsign(String.valueOf(header.getRepeater1Callsign()));
					routeReport.setRepeater2Callsign(String.valueOf(header.getRepeater2Callsign()));
					routeReport.setMyCallsign(String.valueOf(header.getMyCallsign()));
					routeReport.setMyCallsignAdd(String.valueOf(header.getMyCallsignAdd()));
				}

				report.getRouteReports().add(routeReport);
			}
		}finally {
			locker.unlock();
		}

		return report;
	}

	@Override
	protected RepeaterStatusData createStatusDataInternal() {
		final InternalRepeaterStatusData status =
			new InternalRepeaterStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends RepeaterStatusData> getStatusDataTypeInternal() {
		return InternalRepeaterStatusData.class;
	}

	@Override
	protected boolean isCanSleep() {
		locker.lock();
		try {
			return processEntries.isEmpty();
		}finally {
			locker.unlock();
		}
	}

	@Override
	public boolean isDataTransferring() {
		locker.lock();
		try {
			return !processEntries.isEmpty();
		}finally {
			locker.unlock();
		}
	}

	private void processFlagReply() {

		processReplyFlagRequests();

		locker.lock();
		try {
			//アナウンススタート
			if(!isBusy() && hasFlagReplyEntry()) {
				Stream.of(processEntries.values())
				.filter(new Predicate<ProcessEntry>() {
					@Override
					public boolean test(ProcessEntry entry) {
						return entry.getProcessMode() == ProcessMode.FlagReply;
					}
				})
				.min(
					ComparatorCompat.comparingLong(
						new ToLongFunction<ProcessEntry>(){
							@Override
							public long applyAsLong(ProcessEntry entry){
								return entry.getAnnounceStartTime().getTimestampMilis();
							}
						}
					)
				)
				.ifPresent(
					new Consumer<ProcessEntry>() {
						@Override
						public void accept(ProcessEntry entry) {
							flagReplyEntry = entry;

							if(log.isDebugEnabled()) {
								log.debug(
									logTag +
									"Start flag reply frame " +
									String.format("0x%04X", entry.getHeaderPacket().getBackBoneHeader().getFrameIDNumber()) + ".\n" +
									entry.getHeaderPacket().toString(4)
								);
							}
						}
					}
				);
			}

			if(flagReplyEntry != null) {
				flagReplyEntry.updateActivityTimestamp();

				if(flagReplyEntry.getAnnounceStartTime().isTimeout()) {
					//5秒以上返せなかったら諦める
					if(flagReplyEntry.getAnnounceStartTime().getTimeFromUpdate(TimeUnit.MILLISECONDS) < 5000L) {
						while(!this.flagReplyEntry.getAnnouncePackets().isEmpty()) {
							final DSTARPacket announcePacket =
								flagReplyEntry.getAnnouncePackets().poll();

							writeToModem(announcePacket, null, flagReplyEntry);
						}
					}
					else {
						if(log.isDebugEnabled()) {
							log.debug(logTag + "Remove flag reply entry, could not send reply specified time " + flagReplyEntry);
						}
					}

					removeProcessEntry(flagReplyEntry);
				}
			}
		}finally {
			locker.unlock();
		}
	}

	private void processUplinkPacket() {
		// Inet <- Modem
		for(RepeaterModem modem : getRepeaterModems()) {
			while(modem.hasReadPacket()) {
				final DSTARPacket packet = modem.readPacket();
				if(packet == null) {break;}

				if(log.isTraceEnabled()) {
					log.trace(
						logTag +
						"Input packet from modem " +
						modem.getModemType() + "@" + modem.getModemId() + "\n" + packet.toString(4)
					);
				}

				final int frameID = packet.getBackBoneHeader().getFrameIDNumber();
				ProcessEntry entry = null;
				locker.lock();
				try {
					entry = processEntries.get(frameID);

					if(
						entry == null &&
						packet.getDVPacket().hasPacketType(PacketType.Header)
					) {
						//規定外の文字入力を置換
						packet.getRFHeader().replaceCallsignsIllegalCharToSpace();

						//ヘッダチェック
						entry = createEntryFromModemPacket(modem, packet, frameID);

						packet.getRFHeader().setRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL);

						// Store Header
						entry.setHeaderPacket(packet);


						entry.setEnableMinimumTransmissionTimer(
							CommandDetector.getCommandType(
								String.valueOf(packet.getRFHeader().getYourCallsign())
							) == CommandType.G123
						);

						//Last heardを更新&通知
						if(
							entry.getProcessMode() == ProcessMode.ModemToModem ||
							entry.getProcessMode() == ProcessMode.ModemToGateway
						) {
							setLastHeardCallsign(
								DSTARUtils.formatFullLengthCallsign(
									String.valueOf(packet.getRFHeader().getMyCallsign())
								)
							);

							notifyStatusChanged();
						}

						entry.getMinimumTransmissionTimer().updateTimestamp();

						processEntries.put(frameID, entry);
					}
					else if(
						entry != null &&
						packet.getDVPacket().hasPacketType(PacketType.Voice)
					) {
						//NOP
					}
					else {continue;}

					// we have header or voice data

					entry.updateActivityTimestamp();

					if(
						entry.getProcessMode() != ProcessMode.ModemToGateway &&
						entry.getProcessMode() != ProcessMode.ModemToModem
					) {
						continue;
					}

					final boolean newEntry =
						uplinkEntry == null &&
						modem.getTransceiverMode() != ModemTransceiverMode.TxOnly;

					if(newEntry) {
						uplinkEntry = entry;

						if(log.isDebugEnabled()) {
							log.debug(
								logTag +
								"Start uplink frame " +
								String.format("0x%04X", packet.getBackBoneHeader().getFrameIDNumber()) +
								" from " + modem.getModemType() + "@" + modem.getModemId() + ".\n" +
								entry.getHeaderPacket().toString(4)
							);
						}
					}

					if(entry == uplinkEntry) {
						//ゲートウェイへ送信
						if(newEntry && !packet.getDVPacket().hasPacketType(PacketType.Header)) {
							addToInetPacket(entry.getHeaderPacket());
						}

						addToInetPacket(packet);

						if(
							packet.getDVPacket().hasPacketType(PacketType.Voice) &&
							packet.getBackBoneHeader().isMaxSequence() &&
							!packet.isLastFrame()
						) {
							addToInetPacket(entry.getHeaderPacket());
						}

						//モデムへ送信
						if(writeToModem(packet, modem, entry)) {
							entry.setDestinationModemRoute(true);
						}

						if(
							packet.getDVPacket().hasPacketType(PacketType.Voice) &&
							packet.isLastFrame()
						) {
							if(entry == uplinkEntry && log.isDebugEnabled()) {
								log.debug(
									logTag +
									"End uplink frame " + String.format("0x%04X", packet.getBackBoneHeader().getFrameIDNumber()) +
									" from " + modem.getModemType() + "@" + modem.getModemId() + "."
								);
							}

							//モデム→ネットルートの場合には、最低送信時間を確認する
							if(
								!entry.isEnableMinimumTransmissionTimer() ||
								entry.getMinimumTransmissionTimer().isTimeout(minimumTransmissionTimeSeconds, TimeUnit.SECONDS)
							) {
								replyFlagRequests.add(new ReplyRequest(
									entry,
									entry.getProcessMode() == ProcessMode.ModemToGateway ?
										!entry.isBusyReceived() : entry.isDestinationModemRoute()
								));

								removeProcessEntry(entry);
							}
						}
					}
					else {
						if(
							packet.getDVPacket().hasPacketType(PacketType.Voice) &&
							packet.isLastFrame()
						) {
							replyFlagRequests.add(new ReplyRequest(entry, false));

							removeProcessEntry(entry);
						}
					}
				}finally {
					locker.unlock();
				}
			}
		}
	}

	private void processDownlinkPacket() {
		//Inet -> Modem
		locker.lock();
		try {
			for(final Iterator<DSTARPacket> it = fromInetPackets.iterator(); it.hasNext();) {
				final DSTARPacket packet = it.next();
				it.remove();

				final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

				ProcessEntry entry = this.processEntries.get(frameID);

				if(
					entry == null &&
					packet.getDVPacket().hasPacketType(PacketType.Header)
				) {
					if(getRepeaterCallsign().equals(String.valueOf(packet.getRFHeader().getRepeater2Callsign()))) {
						if(packet.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.CANT_REPEAT)) {
							// Busy header received
							for(ProcessEntry processingEntry : this.processEntries.values()) {
								if(
									!processingEntry.isBusyReceived() &&
									processingEntry.getProcessMode() == ProcessMode.ModemToGateway &&
									String.valueOf(
										processingEntry.getHeaderPacket().getRFHeader().getMyCallsign()
									).equals(String.valueOf(packet.getRFHeader().getYourCallsign()))
								) {
									processingEntry.setBusyReceived(true);

									if(log.isDebugEnabled())
										log.debug(logTag + "Receive busy header...\n" + processingEntry.toString(4));
								}
							}
							continue;
						}
						else if(!isBusy()) {
							entry = new ProcessEntry(frameID, packet, ProcessMode.GatewayToModemValid);

							downlinkEntry = entry;

							if(log.isDebugEnabled()) {
								log.debug(
									logTag +
									"Start downlink frame " +
									String.format("0x%04X", packet.getBackBoneHeader().getFrameIDNumber()) + ".\n" +
									entry.getHeaderPacket().toString(4)
								);
							}
						}
						else {
							entry = new ProcessEntry(frameID, packet, ProcessMode.GatewayToGatewayBusy);

							// Return BUSY header
							if(CallSignValidator.isValidUserCallsign(packet.getRFHeader().getMyCallsign())){
								sendBusyHeaderToInet(
									getRepeaterCallsign(),									// my call
									String.valueOf(packet.getRFHeader().getMyCallsign())	// your call
								);

								if(log.isDebugEnabled())
									log.debug(logTag + "Sending busy flag...\n" + entry.toString(4));
							}
						}
					}
					else {
						entry = new ProcessEntry(frameID, packet, ProcessMode.GatewayToModemInvalid);
					}

					processEntries.put(frameID, entry);
				}
				else if(
					entry != null &&
					packet.getDVPacket().hasPacketType(PacketType.Voice)
				) {
					//NOP
				}
				else {continue;}

				entry.updateActivityTimestamp();

				if(
					entry.getProcessMode() != ProcessMode.GatewayToModemValid &&
					entry.getProcessMode() != ProcessMode.GatewayToGatewayBusy
				) {continue;}

				//Inetからのヘッダの受信時にBusy判定を受けたエントリを、
				//現状でBusyで無ければ復活させる
				if(
					entry.getProcessMode() == ProcessMode.GatewayToGatewayBusy &&
					!isBusy()
				){
					Stream.of(this.processEntries)
					.filter(new Predicate<Map.Entry<Integer, ProcessEntry>>() {
						@Override
						public boolean test(Map.Entry<Integer, ProcessEntry> entry) {
							return entry.getValue().getProcessMode() == ProcessMode.GatewayToGatewayBusy;
						}
					})
					.min(ComparatorCompat.comparingLong(new ToLongFunction<Map.Entry<Integer, ProcessEntry>>() {
						@Override
						public long applyAsLong(Map.Entry<Integer, ProcessEntry> entry) {
							return entry.getValue().getCreatedTimestamp();
						}
					}))
					.ifPresent(new Consumer<Map.Entry<Integer, ProcessEntry>>() {
						@Override
						public void accept(Map.Entry<Integer, ProcessEntry> entry) {
							//キャッシュしたヘッダをモデムへ送信
							writeToModem(entry.getValue().getHeaderPacket(), entry.getValue());

							entry.getValue().setProcessMode(ProcessMode.GatewayToModemValid);

							downlinkEntry = entry.getValue();

							if(log.isDebugEnabled()) {
								log.debug(
									logTag +
									"Start downlink busy frame " +
									String.format("0x%04X", packet.getBackBoneHeader().getFrameIDNumber()) + ".\n" +
									entry.getValue().getHeaderPacket().toString(4)
								);
							}
						}
					});
				}

				//モデムへ書き込み
				if(entry.getProcessMode() == ProcessMode.GatewayToModemValid) {
					//宛先を端末宛てにセット
					if(packet.getRFHeader() != null)
						packet.getRFHeader().setRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL);

					//書き込み
					writeToModem(packet, entry);

					if(
						packet.getDVPacket().hasPacketType(PacketType.Voice) &&
						packet.getBackBoneHeader().isMaxSequence() &&
						!packet.isLastFrame()
					) {
						writeToModem(entry.getHeaderPacket(), entry);
					}
				}

				//終端パケットを受信したらプロセスエントリ削除
				if(
					packet.getDVPacket().hasPacketType(PacketType.Voice) &&
					packet.isLastFrame()
				) {
					if(entry == downlinkEntry && log.isDebugEnabled()) {
						log.debug(
							logTag +
							"End downlink frame " + String.format("0x%04X", packet.getBackBoneHeader().getFrameIDNumber()) + "."
						);
					}

					removeProcessEntry(entry);
				}
			}
		}finally {
			locker.unlock();
		}
	}

	private void processReplyFlagRequests() {
		locker.lock();
		try {
			ReplyRequest reply = null;
			while((reply = replyFlagRequests.poll()) != null) {
				replyFlagToModem(reply.getEntry(), reply.isSuccess());
			}
		}finally {
			locker.unlock();
		}
	}

	private boolean removeProcessEntry() {
		return removeProcessEntry(null);
	}

	private boolean removeProcessEntry(final ProcessEntry entry) {
		boolean removed = false;

		locker.lock();
		try {
			for(final Iterator<Map.Entry<Integer, ProcessEntry>> it = processEntries.entrySet().iterator(); it.hasNext();) {
				final Map.Entry<Integer, ProcessEntry> e = it.next();
				//final int frameID = e.getKey();
				final ProcessEntry targetEntry = e.getValue();

				if(
					(
						(entry != null && targetEntry == entry) ||
						(entry == null && targetEntry.isTimeoutActivity(processEntryTimeoutMillis))
					) &&
					(	// モデム→ネットルートにおいては、最低送信時間を満たすことを確認
						entry == null ||	//エントリ指定は最低送信時間を確認しない
						!targetEntry.isEnableMinimumTransmissionTimer() ||
						targetEntry.getMinimumTransmissionTimer().isTimeout(minimumTransmissionTimeSeconds, TimeUnit.SECONDS)
					)
				) {
					boolean replyTransmit = false;
					if(
						targetEntry.getProcessMode() == ProcessMode.ModemToGateway &&
						!targetEntry.isReplyFlagTranmitted()
					) {
						replyFlagRequests.add(
							new ReplyRequest(targetEntry, !targetEntry.isBusyReceived())
						);

						replyTransmit = true;
					}

					it.remove();

					if(uplinkEntry == targetEntry) {uplinkEntry = null;}
					if(downlinkEntry == targetEntry) {downlinkEntry = null;}
					removeModemProcessEntry(targetEntry);

					if(flagReplyEntry == targetEntry) {flagReplyEntry = null;}

					removed = true;

					if(
						(
							entry == null &&
							(
								targetEntry.getProcessMode() != ProcessMode.ModemToGateway ||
								!replyTransmit
							)
						) && log.isDebugEnabled()
					) {
						log.debug(logTag + "Remove timeout process entry.\n" + targetEntry.toString(4));
					}
				}
			}
		}finally {
			locker.unlock();
		}

		return removed;
	}

	private boolean removeModemProcessEntry(final ProcessEntry entry) {
		boolean removed = false;

		locker.lock();
		try {
			for(final Iterator<Map.Entry<RepeaterModem, ProcessEntry>> it = modemEntries.entrySet().iterator(); it.hasNext();) {
				final Map.Entry<RepeaterModem, ProcessEntry> e = it.next();
				//final RepeaterModem modem = e.getKey();
				final ProcessEntry targetEntry = e.getValue();

				if(targetEntry == entry) {
					it.remove();

					removed = true;
				}
			}
		}finally {
			locker.unlock();
		}

		return removed;
	}

	private boolean writeToModem(
		final DSTARPacket packet,final ProcessEntry entry
	) {
		return writeToModem(packet, null, entry);
	}

	private boolean writeToModem(
		final DSTARPacket packet, final RepeaterModem sourceModem, final ProcessEntry entry
	) {
		boolean hasDestinationModem = false;

		locker.lock();
		try {
			for(RepeaterModem targetModem : getRepeaterModems()) {
				ProcessEntry processingEntry = modemEntries.get(targetModem);

				final boolean isModemStandby =
					targetModem.getTransceiverMode() != ModemTransceiverMode.RxOnly &&
					(
						(
							(sourceModem == null || sourceModem == targetModem) &&
							targetModem.getTransceiverMode() == ModemTransceiverMode.FullDuplex
						) ||
						(
							(sourceModem == null || sourceModem != targetModem) &&
							(
								targetModem.getTransceiverMode() == ModemTransceiverMode.FullDuplex ||
								targetModem.getTransceiverMode() == ModemTransceiverMode.TxOnly ||
								(
									targetModem.getTransceiverMode() == ModemTransceiverMode.HalfDuplex &&
									!targetModem.hasReadPacket()
								)
							)
						)
					);

				final boolean newFrame =
					processingEntry == null && isModemStandby;

				if(newFrame) {
					processingEntry = entry;

					modemEntries.put(targetModem, entry);

					if(log.isDebugEnabled()) {
						log.debug(
							logTag + "Start frame " + String.format("0x%04X", packet.getBackBoneHeader().getFrameIDNumber()) +
							" to modem " + targetModem.getModemType() + "@" + targetModem.getModemId() + "."
						);
					}
				}

				if(processingEntry == entry) {
					if(newFrame && !packet.getDVPacket().hasPacketType(PacketType.Header)) {
						targetModem.writePacket(entry.getHeaderPacket().clone());
					}

					targetModem.writePacket(packet.clone());

					if(
						packet.getDVPacket().hasPacketType(PacketType.Voice) &&
						packet.getBackBoneHeader().isMaxSequence() &&
						!packet.isLastFrame()
					) {
						targetModem.writePacket(entry.getHeaderPacket().clone());
					}

					hasDestinationModem = true;

					if(packet.isLastFrame()) {
						modemEntries.remove(targetModem);

						if(log.isDebugEnabled()) {
							log.debug(
								logTag + "End frame " + String.format("0x%04X", packet.getBackBoneHeader().getFrameIDNumber()) +
								" to modem " + targetModem.getModemType() + "@" + targetModem.getModemId() + "."
							);
						}
					}
				}
			}
		}finally {
			locker.unlock();
		}

		return hasDestinationModem;
	}

	private RepeaterModem createModemInstance(final String modemTypeString) {
		RepeaterModem modem = null;

		final ModemTypes modemType = ModemTypes.getTypeByTypeName(modemTypeString);
		if(modemType == null || modemType == ModemTypes.Unknown) {return null;}

		try {
			@SuppressWarnings("unchecked")
			final Class<? extends RepeaterModem> modemClassObj =
				(Class<? extends RepeaterModem>) Class.forName(modemType.getClassName());

			final Constructor<? extends RepeaterModem> constructor =
				modemClassObj.getConstructor(
					ThreadUncaughtExceptionListener.class,
					ExecutorService.class,
					DSTARGateway.class, DSTARRepeater.class,
					EventListener.class, SocketIO.class
				);

			modem =
				constructor.newInstance(
					getExceptionListener(), getWorkerExecutor(), getGateway(), this, modemEventListener, getSocketIO()
				);
		}catch(ReflectiveOperationException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not load modem class..." + modemType.getClassName() + ".", ex);
		}

		return modem;
	}

	private boolean sendBusyHeaderToInet(
		final String myCallsign, final String yourCallsign
	) {
		final Header header = new Header();
		header.setRepeaterControlFlag(RepeaterControlFlag.CANT_REPEAT);
		header.setRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL);
		header.setMyCallsign(myCallsign.toCharArray());
		header.setYourCallsign(yourCallsign.toCharArray());
		header.setRepeater1Callsign(getRepeaterCallsign().toCharArray());
		header.setRepeater2Callsign(getGateway().getGatewayCallsign().toCharArray());

		final BackBoneHeader backbone = new BackBoneHeader(
			BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, DSTARUtils.generateFrameID()
		);
		backbone.setDestinationRepeaterID((byte)0x00);
		backbone.setSendRepeaterID((byte)0x01);
		backbone.setSendTerminalID((byte)0xff);

		final DVPacket packet = new DVPacket(backbone, header);

		return addToInetPacket(
			new InternalPacket(DSTARUtils.generateLoopBlockID(), ConnectionDirectionType.Unknown, packet)
		);
	}

	private void replyFlagToModem(final ProcessEntry entry, final boolean success) {
		if(entry.isReplyFlagTranmitted()) {return;}

		entry.setReplyFlagTranmitted(true);

		if(entry.getProcessMode() == ProcessMode.ModemToGateway) {
			replyFlagToModem(
				(!entry.isBusyReceived() ? RepeaterControlFlag.NO_REPLY : RepeaterControlFlag.CANT_REPEAT),
				String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()),
				500, TimeUnit.MILLISECONDS,
				(!entry.isBusyReceived() ? "" : "")
			);
		}
		else if(entry.getProcessMode() == ProcessMode.ModemToModem) {
			replyFlagToModem(
				(success ? RepeaterControlFlag.NO_REPLY : RepeaterControlFlag.CANT_REPEAT),
				String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()),
				500, TimeUnit.MILLISECONDS,
				(success ? "" : "")
			);
		}
	}

	private boolean replyFlagToModem(
		final RepeaterControlFlag flag, final String yourCallsign,
		final long replayStartTimeLater, final TimeUnit replyStartTimeLaterUnit,
		final String message
	) {
		locker.lock();
		try {
			//同じエントリがあれば追加しない
			if(!checkSameFlagReplyEntry(yourCallsign)) {return true;}

			int frameID;
			do {
				frameID = DSTARUtils.generateFrameID();
			}while(processEntries.containsKey(frameID));

			final UUID loopblockID = DSTARUtils.generateLoopBlockID();

			final Header header = new Header(
				yourCallsign.toCharArray(),
				getRepeaterCallsign().toCharArray(),
				getRepeaterCallsign().toCharArray(),
				getRepeaterCallsign().toCharArray(),
				DSTARDefines.EmptyShortCallsign.toCharArray()
			);
			header.setRepeaterControlFlag(flag);

			final BackBoneHeader backbone =
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID);

			final InternalPacket headerPacket =
				new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, new DVPacket(backbone, header));


			final ProcessEntry entry =
				new ProcessEntry(frameID, headerPacket, replayStartTimeLater, replyStartTimeLaterUnit);

			processEntries.put(frameID, entry);

			entry.getAnnouncePackets().add(headerPacket);

			final NewDataSegmentEncoder encoder = new NewDataSegmentEncoder();
			if(flag == RepeaterControlFlag.NO_REPLY || flag == RepeaterControlFlag.AUTO_REPLY)
				encoder.setShortMessage((message != null && !"".equals(message)) ? message : "OK (^o^)b");
			else
				encoder.setShortMessage((message != null && !"".equals(message)) ? message : "Fail (;_;)");

			encoder.setEnableShortMessage(true);
			encoder.setEnableEncode(true);

			for(int index = 0; index <= 0x14; index++) {
				final VoiceAMBE voice = new VoiceAMBE();
				final BackBoneHeader bb =
					new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, frameID);
				bb.setSequenceNumber((byte)index);

				final DVPacket voicePacket = new DVPacket(bb, voice);

				if(index <= 0x12) {
					voicePacket.getVoiceData().setVoiceSegment(DSTARUtils.getNullAMBE());
					encoder.encode(voicePacket.getVoiceData().getDataSegment());

					voicePacket.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceData);
				}
				else if(index == 0x13) {
					voicePacket.getVoiceData().setVoiceSegment(DSTARUtils.getNullAMBE());
					voicePacket.getVoiceData().setDataSegment(DSTARUtils.getEndSlowdata());

					voicePacket.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceData);
				}
				else {
					voicePacket.getVoiceData().setVoiceSegment(DSTARUtils.getLastAMBE());
					voicePacket.getVoiceData().setDataSegment(DSTARUtils.getLastSlowdata());

					voicePacket.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceDataLastFrame);
				}

				entry.getAnnouncePackets().add(
					new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, voicePacket)
				);
			}

			if(log.isDebugEnabled()) {
				log.debug(logTag + "Schedule flag reply after " + entry.getAnnounceStartTime().getTimeoutMillis() + "ms...");
			}
		}finally {
			locker.unlock();
		}

		return true;
	}

	private ProcessEntry createEntryFromModemPacket(
		@NonNull RepeaterModem modem, @NonNull final DSTARPacket header, final int frameID
	) {
		assert modem != null && header != null;

		header.getRFHeader().setYourCallsign(
			DSTARUtils.reformatIllegalCQCQCQCallsign(
				String.valueOf(header.getRFHeader().getYourCallsign())
			)
		);

		ProcessEntry entry = null;

		final String myCallsign = String.valueOf(header.getRFHeader().getMyCallsign());
		final String myCallsignIgnoreModule = DSTARUtils.formatFullCallsign(myCallsign, ' ');
		final String yourCallsign = String.valueOf(header.getRFHeader().getYourCallsign());
		final String repeater1Callsign = String.valueOf(header.getRFHeader().getRepeater1Callsign());
		final String repeater2Callsign = String.valueOf(header.getRFHeader().getRepeater2Callsign());

		final boolean isDIRECT =
			DSTARDefines.DIRECT.equals(repeater1Callsign) &&
			DSTARDefines.DIRECT.equals(repeater2Callsign);

		if(
			!header.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL) &&
			!header.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.AUTO_REPLY)
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Repeater:" + super.getRepeaterCallsign() +
					" received header has control flag, ignore.\n" + header.toString(4)
				);
			}

			entry = new ProcessEntry(frameID, header, ProcessMode.ModemToNull, modem);
		}
		else if(
			!header.getRFHeader().isSetRepeaterRouteFlag(RepeaterRoute.TO_REPEATER) &&
			(
				!header.getRFHeader().isSetRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL) ||
				!isDIRECT
			)
		){
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Repeater:" + super.getRepeaterCallsign() +
					" received non repeater header from modem...\n" + header.toString(4)
				);
			}

			entry = new ProcessEntry(frameID, header, ProcessMode.ModemToNull, modem);
		}
		else if(
			DSTARDefines.EmptyLongCallsign.equals(myCallsign) ||
			super.getGateway().getGatewayCallsign().equals(myCallsign) ||
			myCallsign.startsWith("NOCALL") ||
			myCallsign.startsWith("MYCALL") ||
			!CallSignValidator.isValidUserCallsign(myCallsign)
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Repeater:" + super.getRepeaterCallsign() +
					" received invalid my callsign header from modem...\n" + header.toString(4)
				);
			}

			entry = new ProcessEntry(frameID, header, ProcessMode.ModemToNull, modem);
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

			entry = new ProcessEntry(frameID, header, ProcessMode.ModemToNull, modem);
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

			entry = new ProcessEntry(frameID, header, ProcessMode.ModemToNull, modem);
		}
		else if(
			//DIRECTの場合には、ゲートウェイコールサインかDirectMyCallsignで指定されたコールサインを許可
			(
				isAllowDIRECT() && modem.isAllowDIRECT() && isDIRECT &&
				(
					DSTARUtils.formatFullCallsign(getGateway().getGatewayCallsign(), ' ').equals(
						DSTARUtils.formatFullCallsign(myCallsign, ' ')
					) ||
					Stream.of(getDirectMyCallsigns())
					.anyMatch(new Predicate<String>() {
						@Override
						public boolean test(String callsign) {
							return myCallsignIgnoreModule.equals(callsign);
						}
					})
				)
			) ||
			(
				getRepeaterCallsign().equals(repeater1Callsign) &&
				getGateway().getGatewayCallsign().equals(repeater2Callsign)
			)
		) {
			entry = new ProcessEntry(frameID, header, ProcessMode.ModemToGateway, modem);

			if(isDIRECT) {
				header.getRFHeader().setRepeater1Callsign(getRepeaterCallsign().toCharArray());
				header.getRFHeader().setRepeater2Callsign(getGateway().getGatewayCallsign().toCharArray());
			}
		}
		else if(
				getRepeaterCallsign().equals(repeater1Callsign) &&
				getRepeaterCallsign().equals(repeater2Callsign)
		) {
			entry = new ProcessEntry(frameID, header, ProcessMode.ModemToModem, modem);
		}
		else {
			if(log.isWarnEnabled()) {
				log.info(
					logTag +
					"Repeater:" + getRepeaterCallsign() +
					" received invalid header from modem...\n" + header.toString(4)
				);
			}

			entry = new ProcessEntry(frameID, header, ProcessMode.ModemToNull, modem);
		}

		return entry;
	}

	private boolean hasFlagReplyEntry() {
		boolean hasFlagReplyEntry = false;

		locker.lock();
		try {
			for(ProcessEntry entry : processEntries.values()) {
				if(entry.getProcessMode() == ProcessMode.FlagReply) {
					hasFlagReplyEntry = true;
					break;
				}
			}
		}finally {
			locker.unlock();
		}

		return hasFlagReplyEntry;
	}

	private boolean checkSameFlagReplyEntry(
		final String targetCallsign
	) {
		boolean hasSameCallsignEntry = false;
		locker.lock();
		try {
			for(final ProcessEntry e : processEntries.values()) {
				if(
					e.getProcessMode() == ProcessMode.FlagReply &&
					targetCallsign.equals(String.valueOf(e.getHeaderPacket().getRFHeader().getYourCallsign()))
				) {
					e.getAnnounceStartTime().updateTimestamp();

					hasSameCallsignEntry = true;
				}
			}
		}finally {
			locker.unlock();
		}

		if(hasSameCallsignEntry) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Reply flag entry has duplicate callsign = " + targetCallsign);
		}

		return !hasSameCallsignEntry;
	}
}
