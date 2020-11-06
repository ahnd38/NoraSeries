package org.jp.illg.dstar.gateway.tool.announce;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
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
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.HeardEntryState;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.model.defines.VoiceCharactors;
import org.jp.illg.dstar.routing.define.RoutingServiceStatus;
import org.jp.illg.dstar.routing.model.RoutingServiceServerStatus;
import org.jp.illg.dstar.routing.model.RoutingServiceStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DvVoiceTool;
import org.jp.illg.dstar.util.NewDataSegmentEncoder;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacketImpl;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.thread.task.TaskQueue;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Predicate;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnnounceTool {

	private static class RepeaterTranferData {
		private final DSTARRepeater destinationRepeater;
		private final DSTARPacket packet;

		private RepeaterTranferData(
			final DSTARRepeater destinationRepeater,
			final DSTARPacket packet
		) {
			this.destinationRepeater = destinationRepeater;
			this.packet = packet;
		}
	}

	private static final String logHeader = AnnounceTool.class.getSimpleName() + " : ";

	private final Lock locker = new ReentrantLock();

	private Map<DSTARRepeater, AnnounceRepeaterEntry> tasks;

	private final ExecutorService workerExecutor;

	@Getter
	private DSTARGateway gateway;

	private final TaskQueue<RepeaterTranferData, Boolean> repeaterWriteQueue;

	private final Consumer<RepeaterTranferData> repeaterWriteTask =
		new Consumer<AnnounceTool.RepeaterTranferData>() {
			@Override
			public void accept(RepeaterTranferData d) {
				d.destinationRepeater.writePacket(d.packet);
			}
	};

	public AnnounceTool(
		@NonNull final ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway
	) {
		this.workerExecutor = workerExecutor;
		this.gateway = gateway;

		this.tasks = new HashMap<>();
		this.repeaterWriteQueue = new TaskQueue<>(this.workerExecutor);
	}

	public boolean announceWakeup(
		@NonNull DSTARRepeater repeater,
		final VoiceCharactors charactor,
		final String applicationName,
		final String applicationVersion
	) {
		if(hasTaskByRepeater(repeater)) {return false;}

		final VoiceCharactors voiceCharactor =
			charactor != null && charactor != VoiceCharactors.Unknown ? charactor : VoiceCharactors.Silent;

		return createAnnounce(
			repeater,
			"Ver" + applicationVersion,
			new Consumer<ByteBuffer>() {
				@Override
				public void accept(ByteBuffer buffer) {
					//起動しました
					DvVoiceTool.generateVoiceByFilename(voiceCharactor, "Wakeup", buffer);
				}
			}
		);
	}

	public boolean announceReflectorConnected(
		@NonNull DSTARRepeater repeater,
		final VoiceCharactors charactor,
		final String linkedReflectorCallsign
	) {
		if(
			!CallSignValidator.isValidReflectorCallsign(linkedReflectorCallsign) ||
			hasTaskByRepeater(repeater)
		){return false;}

		final VoiceCharactors voiceCharactor =
			charactor != null && charactor != VoiceCharactors.Unknown ? charactor : VoiceCharactors.Silent;

		return createAnnounce(
			repeater,
			"<--->" + DSTARUtils.formatFullLengthCallsign(linkedReflectorCallsign) + " LINKED",
			new Consumer<ByteBuffer>() {
				@Override
				public void accept(ByteBuffer buffer) {
					//リフレクターコールサイン音声作成
					DvVoiceTool.generateVoiceCallsign(
						voiceCharactor, linkedReflectorCallsign.toCharArray(), buffer, true
					);

					//へ、接続しました
					DvVoiceTool.generateVoiceByFilename(voiceCharactor, "Connected", buffer);
				}
			}
		);
	}

	public boolean announceReflectorDisconnected(
		@NonNull final DSTARRepeater repeater,
		final VoiceCharactors charactor,
		final String linkedReflectorCallsign
	) {
		if(
			!CallSignValidator.isValidReflectorCallsign(linkedReflectorCallsign) ||
			hasTaskByRepeater(repeater)
		){return false;}

		final VoiceCharactors voiceCharactor =
			charactor != null && charactor != VoiceCharactors.Unknown ? charactor : VoiceCharactors.Silent;

		return createAnnounce(
			repeater,
			"<-X->" + DSTARUtils.formatFullLengthCallsign(linkedReflectorCallsign) + " UNLINK",
			new Consumer<ByteBuffer>() {
				@Override
				public void accept(ByteBuffer buffer) {
					//リフレクターコールサイン音声作成
					DvVoiceTool.generateVoiceCallsign(
						voiceCharactor, linkedReflectorCallsign.toCharArray(), buffer, true
					);

					//から、切断しました
					DvVoiceTool.generateVoiceByFilename(voiceCharactor, "Disconnected", buffer);
				}
			}
		);
	}

	public boolean announceReflectorConnectionError(
		@NonNull final DSTARRepeater repeater,
		final VoiceCharactors charactor,
		final String linkedReflectorCallsign
	) {
		if(
			!CallSignValidator.isValidReflectorCallsign(linkedReflectorCallsign) ||
			hasTaskByRepeater(repeater)
		){return false;}

		final VoiceCharactors voiceCharactor =
			charactor != null && charactor != VoiceCharactors.Unknown ? charactor : VoiceCharactors.Silent;

		return createAnnounce(
			repeater,
			"<-X->" + DSTARUtils.formatFullLengthCallsign(linkedReflectorCallsign) + " FAILED",
			new Consumer<ByteBuffer>() {
				@Override
				public void accept(ByteBuffer buffer) {
					//リフレクターコールサイン音声作成
					DvVoiceTool.generateVoiceCallsign(
						voiceCharactor, linkedReflectorCallsign.toCharArray(), buffer, true
					);

					//との、通信エラーが発生しました
					DvVoiceTool.generateVoiceByFilename(voiceCharactor, "ConnectionError", buffer);
				}
			}
		);
	}

	public boolean announceInformation(
		@NonNull final DSTARRepeater repeater,
		final VoiceCharactors charactor,
		final String linkedReflectorCallsign
	) {
		if(hasTaskByRepeater(repeater)) {return false;}

		final VoiceCharactors voiceCharactor =
			charactor != null && charactor != VoiceCharactors.Unknown ? charactor : VoiceCharactors.Silent;

		final boolean isReflectorLinked =
			CallSignValidator.isValidReflectorCallsign(linkedReflectorCallsign);

		return createAnnounce(
			repeater,
			isReflectorLinked ?
				"<--->" + DSTARUtils.formatFullLengthCallsign(linkedReflectorCallsign) + " LINKED" : "<-X-> NOT LINKED",
			new Consumer<ByteBuffer>() {
				@Override
				public void accept(ByteBuffer buffer) {
					if(isReflectorLinked) {
						//リフレクターコールサイン音声作成
						DvVoiceTool.generateVoiceCallsign(
							voiceCharactor, linkedReflectorCallsign.toCharArray(), buffer, true
						);
						DvVoiceTool.generateVoiceByFilename(voiceCharactor, "InfoConnected", buffer);
					}
					else {
						DvVoiceTool.generateVoiceByFilename(voiceCharactor, "InfoNotConnected", buffer);
					}
				}
			}
		);
	}

	public boolean announceCurrentRoutingService(
		@NonNull final DSTARRepeater repeater
	) {
		final int frameID = DSTARUtils.generateFrameID();
		final UUID loopblockID = DSTARUtils.generateLoopBlockID();

		final RoutingService routingService = repeater.getRoutingService();
		final RoutingServiceStatusData routingServiceStatus =
			routingService != null ? routingService.getServiceStatus() : null;
		final boolean isRoutingServiceReady = routingServiceStatus != null ?
			Stream.of(routingServiceStatus.getServiceStatus())
			.anyMatch(new Predicate<RoutingServiceServerStatus>() {
				@Override
				public boolean test(RoutingServiceServerStatus s) {
					return s.getServiceStatus() == RoutingServiceStatus.InService;
				}
			}) : false;

		//メッセージ作成
		String shortMessage;
		if(routingService != null) {
			shortMessage = routingService.getServiceType().getTypeName() + " " +
				(isRoutingServiceReady ? "READY" : "NOT READY");
		}
		else
			shortMessage = "Unknown ???";

		//ヘッダ作成
		final DSTARPacket headerPacket =
			generateAnnounceHeaderPacket(getGateway(), repeater, frameID, loopblockID);

		final NewDataSegmentEncoder dataSegmentEncoder = new NewDataSegmentEncoder();
		dataSegmentEncoder.setShortMessage(DSTARUtils.formatLengthShortMessage(shortMessage));
		dataSegmentEncoder.setEnableShortMessage(true);

		final Queue<DSTARPacket> announcePackets = new LinkedList<>();

		for(int blockIndex = 0; blockIndex < 4; blockIndex++) {
			for(int index = 0; index <= 0x14; index++) {
				final BackBoneHeader backbone =
					new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, frameID);
				backbone.setFrameIDNumber(frameID);
				backbone.setSequenceNumber((byte)index);

				final VoiceAMBE voice = new VoiceAMBE();

				if((blockIndex + 1) >= 4 && index >= 0x13) {
					if(index == 0x13) {
						voice.setVoiceSegment(DSTARUtils.getNullAMBE());
						voice.setDataSegment(DSTARUtils.getEndSlowdata());

						backbone.setFrameType(BackBoneHeaderFrameType.VoiceData);
					}
					else {
						voice.setVoiceSegment(DSTARUtils.getLastAMBE());
						voice.setDataSegment(DSTARUtils.getLastSlowdata());

						backbone.setFrameType(BackBoneHeaderFrameType.VoiceDataLastFrame);
					}
				}
				else {
					voice.setVoiceSegment(DSTARUtils.getNullAMBE());
					dataSegmentEncoder.encode(voice.getDataSegment());

					backbone.setFrameType(BackBoneHeaderFrameType.VoiceData);
				}

				announcePackets.add(
					new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, new DVPacket(backbone, voice))
				);
			}
		}

		final AnnounceRepeaterEntry entry = getAnnounceRepeaterEntry(repeater);
		synchronized(entry) {
			AnnounceTask annoTask = new AnnounceTask(headerPacket);
			annoTask.setShortMessage(shortMessage);

			annoTask.getAnnounceVoice().addAll(announcePackets);

			entry.getAnnounceTasks().add(annoTask);
		}
		announcePackets.clear();

		return true;
	}

	public boolean process() {

		locker.lock();
		try {
			for(AnnounceRepeaterEntry taskRepeaterEntry : tasks.values()) {

				if(taskRepeaterEntry.getAnnounceTasks().isEmpty()) {continue;}

				AnnounceTask announceTask = taskRepeaterEntry.getAnnounceTasks().peek();
				if(announceTask == null) {continue;}

				synchronized(announceTask) {
					boolean reProcess = false;
					do {
						reProcess = false;
						switch(taskRepeaterEntry.getProcessState()) {
						case Initialize:
							taskRepeaterEntry.setProcessState(AnnounceRepeaterProcessState.QueueAdded);
							taskRepeaterEntry.getRateMatcher().reset();
							taskRepeaterEntry.updateLastActivityTime();
							reProcess = true;
							break;

						case QueueAdded:
							taskRepeaterEntry.getRateMatcher().writePacket(new TransmitterPacketImpl(
								PacketType.Header,
								announceTask.getHeader(),
								FrameSequenceType.Start
							));
							for(final DSTARPacket p : announceTask.getAnnounceVoice()) {
								taskRepeaterEntry.getRateMatcher().writePacket(new TransmitterPacketImpl(
									PacketType.Voice,
									p,
									p.isLastFrame() ? FrameSequenceType.End : FrameSequenceType.None
								));
							}

							if(log.isTraceEnabled()) {
								log.trace(
									logHeader +
									"Announce added..." + taskRepeaterEntry.getRepeater().getRepeaterCallsign() +
									"[" + (announceTask.getAnnounceVoice().size() / DSTARDefines.DvFramePerSeconds) + "sec]"
								);
							}
							announceTask.getAnnounceVoice().clear();
							taskRepeaterEntry.setProcessState(AnnounceRepeaterProcessState.ProcessWait);
							taskRepeaterEntry.updateLastActivityTime();
							break;

						case ProcessWait:
							if(System.currentTimeMillis() > (taskRepeaterEntry.getLastActivityTime() + 2000)) {
								taskRepeaterEntry.setProcessState(AnnounceRepeaterProcessState.WaitBusy);
								taskRepeaterEntry.updateLastActivityTime();
							}
							break;

						case WaitBusy:
							if(!taskRepeaterEntry.getRepeater().isBusy()){
								if(log.isDebugEnabled())
									log.debug(logHeader + "Announce start..." + taskRepeaterEntry.getRepeater().getRepeaterCallsign());

								taskRepeaterEntry.getRateMatcher().reset(false);
								taskRepeaterEntry.getRateMatcher().start();
								taskRepeaterEntry.setProcessState(AnnounceRepeaterProcessState.Processing);

								taskRepeaterEntry.updateLastActivityTime();
								reProcess = true;
							}
							else if(System.currentTimeMillis() > (taskRepeaterEntry.getLastActivityTime() + 5000)) {
								//レピータが使用中なので諦める
								taskRepeaterEntry.setProcessState(AnnounceRepeaterProcessState.Completed);
							}
							break;

						case Processing:
							while(
								taskRepeaterEntry.getRateMatcher().hasReadableDvPacket()
							) {
								Optional<TransmitterPacket> packet = taskRepeaterEntry.getRateMatcher().readDvPacket();
								if(!packet.isPresent()) {break;}

								long deltaMillis =
										System.currentTimeMillis() - taskRepeaterEntry.getLastActivityTime();
								if(log.isTraceEnabled()) {
									log.trace(
										logHeader +
										"Write announce packet to repeater(d:" + deltaMillis + "ms).\n" + packet.get().getPacket().toString(4)
									);
								}

								if(packet.get().getPacketType() == PacketType.Header) {
									getGateway().addHeardEntry(
										HeardEntryState.Start,
										DSTARProtocol.Internal,
										ConnectionDirectionType.INCOMING,
										String.valueOf(announceTask.getHeader().getRFHeader().getYourCallsign()),
										String.valueOf(announceTask.getHeader().getRFHeader().getRepeater1Callsign()),
										String.valueOf(announceTask.getHeader().getRFHeader().getRepeater2Callsign()),
										String.valueOf(announceTask.getHeader().getRFHeader().getMyCallsign()),
										String.valueOf(announceTask.getHeader().getRFHeader().getMyCallsignAdd()),
										DSTARUtils.convertRepeaterCallToAreaRepeaterCall(
											String.valueOf(announceTask.getHeader().getRFHeader().getRepeater2Callsign())
										),
										DSTARDefines.EmptyLongCallsign,
										announceTask.getShortMessage(), false, 0.0d, 0.0d,
										0, 0.0d, 0.0d
									);

								}
								else if(
									packet.get().getPacketType() == PacketType.Voice &&
									packet.get().getPacket().isLastFrame()
								) {
									getGateway().addHeardEntry(
										HeardEntryState.End,
										DSTARProtocol.Internal,
										ConnectionDirectionType.INCOMING,
										String.valueOf(announceTask.getHeader().getRFHeader().getYourCallsign()),
										String.valueOf(announceTask.getHeader().getRFHeader().getRepeater1Callsign()),
										String.valueOf(announceTask.getHeader().getRFHeader().getRepeater2Callsign()),
										String.valueOf(announceTask.getHeader().getRFHeader().getMyCallsign()),
										String.valueOf(announceTask.getHeader().getRFHeader().getMyCallsignAdd()),
										DSTARUtils.convertRepeaterCallToAreaRepeaterCall(
											String.valueOf(announceTask.getHeader().getRFHeader().getRepeater2Callsign())
										),
										DSTARDefines.EmptyLongCallsign,
										announceTask.getShortMessage(), false, 0.0d, 0.0d,
										0, 0.0d, 0.0d
									);
								}

								repeaterWriteQueue.addEventQueue(
									repeaterWriteTask,
									new RepeaterTranferData(taskRepeaterEntry.getRepeater(), packet.get().getPacket()),
									getGateway()
								);

								if(packet.get().getPacket().isLastFrame())
									log.debug(logHeader + "End of announce voice transmit.");

								taskRepeaterEntry.updateLastActivityTime();
							}

							if(taskRepeaterEntry.getRateMatcher().isCachePacketEmpty()) {
								if(log.isDebugEnabled())
									log.debug(logHeader + "Announce completed..." + taskRepeaterEntry.getRepeater().getRepeaterCallsign());

								taskRepeaterEntry.setProcessState(AnnounceRepeaterProcessState.Completed);
								taskRepeaterEntry.updateLastActivityTime();

								reProcess = true;
							}
							break;

						case Completed:
							if(log.isDebugEnabled())
								log.debug(logHeader + "Announce reset..." + taskRepeaterEntry.getRepeater().getRepeaterCallsign());

							taskRepeaterEntry.getRateMatcher().reset();
							taskRepeaterEntry.setProcessState(AnnounceRepeaterProcessState.Initialize);
							taskRepeaterEntry.updateLastActivityTime();

							taskRepeaterEntry.getAnnounceTasks().poll();
							break;

						default:
							break;
						}
					}while(reProcess);
				}
			}
		}finally {
			locker.unlock();
		}

		return true;
	}

	@SafeVarargs
	private final boolean createAnnounce(
		@NonNull final DSTARRepeater repeater,
		String shortMessage,
		final Consumer<ByteBuffer>... processors
	) {
		if(shortMessage == null) {shortMessage = "";}

		final int frameID = DSTARUtils.generateFrameID();
		final UUID loopblockID = DSTARUtils.generateLoopBlockID();

		final ByteBuffer buffer =	// 60秒分の領域を確保する
			ByteBuffer.allocate(DSTARDefines.DvFrameLength * (60000 / DSTARDefines.DvFrameIntervalTimeMillis));

		//音声作成
		for(int count = 0; count < 3; count++)
			if(buffer.hasRemaining()) {DvVoiceTool.generateVoiceByFilename(VoiceCharactors.Silent, " ", buffer);}

		if(processors != null) {
			for(final Consumer<ByteBuffer> processor : processors)
				processor.accept(buffer);
		}

		//音声パケット作成
		BufferState.toREAD(buffer, BufferState.WRITE);
		final NewDataSegmentEncoder dataSegmentEncoder = new NewDataSegmentEncoder();
		dataSegmentEncoder.setShortMessage(shortMessage);
		dataSegmentEncoder.setEnableShortMessage(true);
		final Queue<DVPacket> voicePackets =
			DvVoiceTool.generateVoicePacketFromBuffer(
				buffer, frameID, dataSegmentEncoder
			);
		if(voicePackets == null) {return false;}

		final Queue<DSTARPacket> announcePackets = new LinkedList<>();
		for(final DVPacket voicePacket : voicePackets) {
			announcePackets.add(
				new InternalPacket(loopblockID, ConnectionDirectionType.Unknown, voicePacket)
			);
		}

		final AnnounceRepeaterEntry entry = getAnnounceRepeaterEntry(repeater);
		synchronized(entry) {
			final AnnounceTask annoTask = new AnnounceTask(
				generateAnnounceHeaderPacket(getGateway(), repeater, frameID, loopblockID)
			);
			annoTask.setShortMessage(shortMessage);

			annoTask.getAnnounceVoice().addAll(announcePackets);

			entry.getAnnounceTasks().add(annoTask);
		}

		return true;
	}

	private AnnounceRepeaterEntry getAnnounceRepeaterEntry(DSTARRepeater repeater) {
		assert repeater != null;

		AnnounceRepeaterEntry entry = null;

		locker.lock();
		try {
			 entry = tasks.get(repeater);
			if(entry == null) {
				entry = new AnnounceRepeaterEntry(repeater);
				tasks.put(repeater, entry);
			}
		}finally {
			locker.unlock();
		}

		return entry;
	}

	private boolean hasTaskByRepeater(final DSTARRepeater repeater) {
		final AnnounceRepeaterEntry entry = getAnnounceRepeaterEntry(repeater);

		return !entry.getAnnounceTasks().isEmpty();
	}

	private static DSTARPacket generateAnnounceHeaderPacket(
		final DSTARGateway gateway,
		final DSTARRepeater repeater, final int frameID, final UUID loopBlockID
	) {
		final Header newHeader = new Header();
		newHeader.setRepeaterControlFlag(RepeaterControlFlag.AUTO_REPLY);
		newHeader.setRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL);
		newHeader.setYourCallsign(DSTARDefines.CQCQCQ.toCharArray());
		newHeader.setRepeater2Callsign(repeater.getRepeaterCallsign().toCharArray());
		newHeader.setRepeater1Callsign(gateway.getGatewayCallsign().toCharArray());
		newHeader.setMyCallsign(gateway.getGatewayCallsign().toCharArray());
		newHeader.setMyCallsignAdd("INFO".toCharArray());

		final BackBoneHeader backbone =
			new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID);

		final DVPacket dvPacket = new DVPacket(backbone, newHeader);

		final InternalPacket headerPacket =
			new InternalPacket(loopBlockID, ConnectionDirectionType.Unknown, dvPacket);

		if(log.isTraceEnabled())
			log.trace(logHeader + "Generating announce header.\n" + headerPacket.toString());

		return headerPacket;
	}
}
