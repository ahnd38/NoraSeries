package org.jp.illg.dstar.repeater.icom;

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.repeater.DSTARRepeaterBase;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.reporter.model.RepeaterRouteStatusReport;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.dstar.service.icom.IcomRepeaterCommunicationService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlExternalICOMRepeaterHandler;
import org.jp.illg.dstar.service.web.model.ExternalICOMRepeaterStatusData;
import org.jp.illg.dstar.service.web.model.RepeaterStatusData;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.task.TaskQueue;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExternalICOMRepeater extends DSTARRepeaterBase
implements WebRemoteControlExternalICOMRepeaterHandler{

	private static class FrameEntry{
		private final long createTimeNanos;
		private final boolean isFromGateway;
		private final int frameID;
		private final DSTARPacket headerPacket;
		private final RepeaterControlFlag flag;
		private RepeaterControlFlag replyFlag;

		private final Timer activityTimekeeper;

		public FrameEntry(
			final boolean isFromGateway,
			final int frameID, final DSTARPacket headerPacket,
			final RepeaterControlFlag flag
		) {
			this.createTimeNanos = SystemUtil.getNanoTimeCounterValue();
			this.isFromGateway = isFromGateway;
			this.frameID = frameID;
			this.headerPacket = headerPacket;
			this.flag = flag;

			this.replyFlag = RepeaterControlFlag.NO_REPLY;

			activityTimekeeper = new Timer();
		}
	}

	private static class ReplyEntry{
		private final long createTimeNanos;
		private long targetTimeNanos;
		private String myCallsign;
		private final String yourCallsign;
		private String repeater1Callsign;
		private RepeaterControlFlag flag;

		public ReplyEntry(
			final String yourCallsign,
			final String myCallsign,
			final String repeater1Callsign,
			final RepeaterControlFlag flag,
			final long scheduleAfterTimeNanos
		) {
			this.createTimeNanos = SystemUtil.getNanoTimeCounterValue();

			this.yourCallsign = yourCallsign;
			this.myCallsign = myCallsign;
			this.repeater1Callsign = repeater1Callsign;
			this.flag = flag;

			targetTimeNanos = createTimeNanos + scheduleAfterTimeNanos;
		}
	}

	private final String logTag;

	private final Lock locker;

	private final Map<Integer, FrameEntry> frameEntries;
	private FrameEntry currentFrameEntry;

	private final List<ReplyEntry> replyEntries;

	private final TaskQueue<DSTARPacket, Boolean> writeQueue;

	private byte repeaterID, terminalID;

	private final Function<DSTARPacket, Boolean> repeaterWriteTask = new Function<DSTARPacket, Boolean>() {
		@Override
		public Boolean apply(DSTARPacket packet) {
			final IcomRepeaterCommunicationService service =
				IcomRepeaterCommunicationService.getInstance(getSystemID());

			return service != null && service.writePacket(packet);
		}
	};

	private final Function<DSTARPacket, Boolean> gatewayWriteTask = new Function<DSTARPacket, Boolean>() {
		@Override
		public Boolean apply(DSTARPacket packet) {
			return addToInetPacket(packet);
		}
	};

	public ExternalICOMRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener
	) {
		this(systemID, gateway, repeaterCallsign, workerExecutor, eventListener, null);
	}

	public ExternalICOMRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway, @NonNull final String repeaterCallsign,
		@NonNull final ExecutorService workerExecutor,
		final EventListener<DStarRepeaterEvent> eventListener,
		SocketIO socketIO
	) {
		super(
			ExternalICOMRepeater.class,
			systemID,
			gateway,
			repeaterCallsign,
			workerExecutor,
			eventListener,
			socketIO
		);

		logTag = ExternalICOMRepeater.class.getSimpleName() + "(" + repeaterCallsign + ") : ";

		locker = new ReentrantLock();

		frameEntries = new ConcurrentHashMap<>();
		replyEntries = new LinkedList<>();

		writeQueue = new TaskQueue<>(workerExecutor);

		repeaterID = (byte)0x00;
		terminalID = (byte)0x00;
	}

	public boolean writePacketFromIcomRepeater(@NonNull final DSTARPacket packet) {
		return processPacket(false, packet);
	}

	public void keepAliveFromRepeater(final String statusMessage) {
		getWorkerExecutor().submit(new RunnableTask(getExceptionListener()) {
			@Override
			public void task() {
				getGateway().kickWatchdogFromRepeater(
					getRepeaterCallsign(), statusMessage != null ? statusMessage : ""
				);
			}
		});

	}

	@Override
	public boolean writePacket(DSTARPacket packet) {
		return processPacket(true, packet);
	}

	@Override
	public boolean isBusy() {
		return IcomRepeaterCommunicationService.getInstance(getSystemID()) == null;
	}

	@Override
	public boolean isReflectorLinkSupport() {
		return true;
	}

	@Override
	public List<String> getRouterStatus() {
		final List<String> routerStatus = new ArrayList<>();

		locker.lock();
		try {
			for(final FrameEntry entry : frameEntries.values()) {
				final StringBuilder sb = new StringBuilder();

				sb.append("Mode:");
				sb.append(entry.isFromGateway ? "Downlink" : "Uplink");
				sb.append(" / ");
				sb.append("ID:");
				sb.append(String.format("%04X", entry.frameID));
				sb.append(" / ");
				sb.append("Time:");
				sb.append(
					String.format(
						"%3d",
						(int)(SystemUtil.getNanoTimeCounterValue() - entry.createTimeNanos) / (int)1000000000
					)
				);
				sb.append("s");
				sb.append(" / ");
				if(entry.headerPacket != null) {
					final Header header = entry.headerPacket.getRFHeader();
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
	public void notifyReflectorLoginUsers(
		@NonNull ReflectorProtocolProcessorTypes reflectorType,
		@NonNull DSTARProtocol protocol, @NonNull String remoteCallsign,
		@NonNull ConnectionDirectionType connectionDir,
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
	public boolean initializeWebRemote(org.jp.illg.dstar.service.web.WebRemoteControlService service) {
		return service.initializeRepeaterExternalICOM(this);
	}

	@Override
	protected RepeaterStatusData createStatusDataInternal() {
		final RepeaterStatusData status = new RepeaterStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends RepeaterStatusData> getStatusDataTypeInternal() {
		return ExternalICOMRepeaterStatusData.class;
	}

	@Override
	protected RepeaterStatusReport getRepeaterStatusReportInternal(RepeaterStatusReport report) {

		locker.lock();
		try {
			for(final FrameEntry entry : frameEntries.values()) {
				final RepeaterRouteStatusReport routeReport = new RepeaterRouteStatusReport();

				routeReport.setRouteMode(entry.isFromGateway ? "Downlink" : "Uplink");
				routeReport.setFrameID(entry.frameID);
				routeReport.setFrameSequenceStartTime(
					SystemUtil.getAbsoluteTimeMillisFromNanoTimeCounterValue(entry.createTimeNanos)
				);
				if(entry.headerPacket != null){
					final Header header = entry.headerPacket.getRFHeader();
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
	protected void threadFinalize() {
	}

	@Override
	protected ThreadProcessResult processRepeater() {

		processFrameEntries();

		processReplyEntries();

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected boolean isCanSleep() {
		locker.lock();
		try {
			return frameEntries.isEmpty() && replyEntries.isEmpty();
		}finally {
			locker.unlock();
		}
	}

	@Override
	protected long getProcessLoopPeriodMillisSleep() {
		return 1000L;
	}

	@Override
	protected long getProcessLoopPeriodMillisNormal() {
		return 100L;
	}

	@Override
	protected boolean isAutoWatchdog() {
		return false;
	}

	@Override
	public boolean isDataTransferring() {
		locker.lock();
		try {
			return !frameEntries.isEmpty() || !replyEntries.isEmpty();
		}finally {
			locker.unlock();
		}
	}

	private void processReplyEntries() {
		locker.lock();
		try {
			if(countOverEstimatedTimeReplyEntries() < 1) {return;}

			final long currentTimeNanos = SystemUtil.getNanoTimeCounterValue();
			final List<ReplyEntry> replyList = Stream.of(replyEntries)
				.filter(new Predicate<ReplyEntry>() {
					@Override
					public boolean test(ReplyEntry e) {
						return e.targetTimeNanos < currentTimeNanos;
					}
				})
				.sorted(ComparatorCompat.comparingLong(new ToLongFunction<ReplyEntry>() {
					@Override
					public long applyAsLong(ReplyEntry e) {
						return e.targetTimeNanos;
					}
				}))
				.toList();

			for(final ReplyEntry entry : replyList) {
				final Queue<DSTARPacket> replyPackets = DSTARUtils.createReplyPacketsICOM(
					DSTARUtils.generateFrameID(),
					entry.flag,
					entry.myCallsign,
					entry.yourCallsign,
					entry.repeater1Callsign,
					getRepeaterCallsign()
				);

				while(!replyPackets.isEmpty())
					writeQueue.addEventQueue(repeaterWriteTask, replyPackets.poll(), getExceptionListener());

				replyEntries.remove(entry);
			}
		}finally {
			locker.unlock();
		}
	}

	private void processFrameEntries() {
		locker.lock();
		try {
			for(final Iterator<FrameEntry> it = frameEntries.values().iterator(); it.hasNext();) {
				final FrameEntry entry = it.next();

				if(entry.activityTimekeeper.isTimeout(2, TimeUnit.SECONDS)) {
					it.remove();

					if(currentFrameEntry == entry) {
						currentFrameEntry = selectNextEntry();
						if(currentFrameEntry != null) {
							//フレームの途中から送信
							writePacket(entry.headerPacket.clone(), entry.isFromGateway);
						}
					}

					if(log.isDebugEnabled())
						log.debug(logTag + "Timeout frame = " + String.format("0x%04X", entry.frameID));
				}
			}
		}finally {
			locker.unlock();
		}
	}

	private boolean processPacket(
		final boolean isFromGateway,
		final DSTARPacket packet
	) {
		final int frameID = packet.getFrameID();
		if(!DSTARUtils.isValidFrameID(frameID)) {return false;}

		locker.lock();
		try {
			FrameEntry entry = frameEntries.get(frameID);
			final boolean isNewFrame = entry == null;

			if(isNewFrame) {
				if(packet.getRFHeader() == null) {return false;}
				final RepeaterControlFlag flag = packet.getRFHeader().getRepeaterControlFlag();

				entry = new FrameEntry(
					isFromGateway, frameID, packet.clone(), flag
				);
				frameEntries.put(frameID, entry);

				if(log.isDebugEnabled())
					log.debug(logTag + "Start frame = " + String.format("0x%04X", frameID));

				if(isFromGateway) {	//ゲートウェイからのパケット
					//ゲートウェイからの制御フラグを受信した場合、
					//レピータからゲートウェイに送信中のフレームから該当フレームを検索しマークする
					if(DSTARUtils.hasControlFlag(flag)) {
						for(final FrameEntry frameEntry : frameEntries.values()) {
							if(
								frameEntry.isFromGateway ||
								!frameEntry.headerPacket.getRFHeader().getMyCallsignString().equals(
									packet.getRFHeader().getYourCallsignString()
								)
							) {continue;}

							frameEntry.replyFlag = flag;
						}

						for(final ReplyEntry replyEntry : replyEntries) {
							if(!replyEntry.yourCallsign.equals(packet.getRFHeader().getYourCallsignString())) {
								continue;
							}

							replyEntry.flag = flag;
							replyEntry.myCallsign = getGateway().getGatewayCallsign();
							replyEntry.repeater1Callsign = getGateway().getGatewayCallsign();
						}
					}
				}
				else {	//レピータからのパケット
					if(repeaterID != packet.getBackBoneHeader().getSendRepeaterID()) {
						if(log.isInfoEnabled())
							log.info(logTag + "Repeater ID changed " + packet.getBackBoneHeader().getSendRepeaterID() + " <- " + repeaterID);

						repeaterID = packet.getBackBoneHeader().getSendRepeaterID();
					}

					if(terminalID != packet.getBackBoneHeader().getSendTerminalID()) {
						if(log.isInfoEnabled())
							log.info(logTag + "Terminal ID changed  " + packet.getBackBoneHeader().getSendTerminalID() + " <- " + terminalID);

						terminalID = packet.getBackBoneHeader().getSendTerminalID();
					}

					setLastHeardCallsign(packet.getRFHeader().getMyCallsignString());
				}

				if(currentFrameEntry == null)
					currentFrameEntry = selectNextEntry();
			}

			if(isFromGateway) {
				packet.getBackBoneHeader().setDestinationRepeaterID(repeaterID);
				packet.getBackBoneHeader().setSendRepeaterID((byte)0x0);
				packet.getBackBoneHeader().setSendTerminalID(terminalID);
			}

			entry.activityTimekeeper.updateTimestamp();

			if(
				entry == currentFrameEntry ||
				//レピータからの制御フラグ付きはそのまま投げる
				(!entry.isFromGateway && DSTARUtils.hasControlFlag(entry.flag))
			) {
				writePacket(packet, isFromGateway);

				if(
					!packet.isLastFrame() &&
					packet.getBackBoneHeader() != null &&
					packet.getBackBoneHeader().isMaxSequence()
				) {
					writePacket(entry.headerPacket.clone(), isFromGateway);
				}
			}

			if(packet.isLastFrame()) {
				frameEntries.remove(frameID);

				if(currentFrameEntry == entry) {
					currentFrameEntry = selectNextEntry();
					if(currentFrameEntry != null) {
						//フレームの途中から送信
						writePacket(entry.headerPacket.clone(), entry.isFromGateway);
						writePacket(packet, isFromGateway);
					}
				}

				if(
					!entry.isFromGateway &&
					entry.headerPacket.getPacketType() == DSTARPacketType.DV &&
					!DSTARUtils.hasControlFlag(entry.flag)
				) {
					addReplyTask(
						entry.headerPacket.getRFHeader().getMyCallsignString(),
						entry.replyFlag != null ? entry.replyFlag : RepeaterControlFlag.NO_REPLY
					);

					processReplyEntries();
				}

				if(log.isDebugEnabled())
					log.debug(logTag + "End frame = " + String.format("0x%04X", frameID));
			}
		}finally {
			locker.unlock();
		}

		return true;
	}

	private FrameEntry selectNextEntry() {
		locker.lock();
		try {
			if(frameEntries.isEmpty()) {return null;}

			final Optional<FrameEntry> entry = Stream.of(frameEntries.values())
				.filter(new Predicate<FrameEntry>() {
					@Override
					public boolean test(FrameEntry e) {
						return e != currentFrameEntry && !DSTARUtils.hasControlFlag(e.flag);
					}
				})
				.min(ComparatorCompat.comparingLong(new ToLongFunction<FrameEntry>() {
					@Override
					public long applyAsLong(FrameEntry e) {
						return e.createTimeNanos;
					}
				}));

			return entry.isPresent() ? entry.get() : null;
		}finally {
			locker.unlock();
		}
	}

	private void writePacket(final DSTARPacket packet, boolean isFromGateway) {
		//途中からフレームを送信
		if(isFromGateway)//ICOMレピータへ書き込み
			writeQueue.addEventQueue(repeaterWriteTask, packet, getExceptionListener());
		else //ゲートウェイへ書き込み
			writeQueue.addEventQueue(gatewayWriteTask, packet, getExceptionListener());
	}

	private boolean addReplyTask(
		final String yourCallsign,
		final RepeaterControlFlag flag
	) {
		locker.lock();
		try {
			for(final ReplyEntry entry : replyEntries) {
				if(entry.yourCallsign.equals(yourCallsign)) {
					entry.targetTimeNanos += TimeUnit.NANOSECONDS.convert(250, TimeUnit.MILLISECONDS);

					return true;
				}
			}

			//新規エントリ
			final ReplyEntry entry = new ReplyEntry(
				yourCallsign,
				getRepeaterCallsign(),
				getRepeaterCallsign(),
				flag,
				TimeUnit.NANOSECONDS.convert(250, TimeUnit.MILLISECONDS)
			);

			return replyEntries.add(entry);
		}finally {
			locker.unlock();
		}
	}

	private int countOverEstimatedTimeReplyEntries() {
		int count = 0;

		final long currentTimeNanos = SystemUtil.getNanoTimeCounterValue();
		locker.lock();
		try {
			for(final ReplyEntry entry : replyEntries) {
				if(entry.targetTimeNanos < currentTimeNanos)
					count++;
			}
		}finally {
			locker.unlock();
		}

		return count;
	}
}
