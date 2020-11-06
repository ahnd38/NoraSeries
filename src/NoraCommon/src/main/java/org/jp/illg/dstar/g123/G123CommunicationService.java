package org.jp.illg.dstar.g123;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.g123.model.G2Entry;
import org.jp.illg.dstar.g123.model.G2HeaderCacheEntry;
import org.jp.illg.dstar.g123.model.G2Packet;
import org.jp.illg.dstar.g123.model.G2RouteStatus;
import org.jp.illg.dstar.g123.model.G2TransmitPacketEntry;
import org.jp.illg.dstar.g123.model.Poll;
import org.jp.illg.dstar.g123.model.VoiceDataFromInet;
import org.jp.illg.dstar.g123.model.VoiceDataToInet;
import org.jp.illg.dstar.g123.model.VoiceHeaderFromInet;
import org.jp.illg.dstar.g123.model.VoiceHeaderToInet;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.ProcessResult;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.SocketIOHandlerWithThread;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class G123CommunicationService extends SocketIOHandlerWithThread<BufferEntry>{

	private static final int g2ProtocolPort = 40000;
	private static final int headerCacheLimit = 10;

	private static final String logHeader;

	@Getter
	@Setter
	private int portNumber;

	@Getter
	private int protocolVersion;
	public static final int protocolVersionDefault = 1;
	public static final int protocolVersionMin = 1;
	public static final int protocolVersionMax = 2;

	@Getter
	private final DSTARGateway gateway;

	@Getter
	@Setter
	private boolean useProxyGateway;

	@Getter
	@Setter
	private String proxyGatewayAddress;

	@Getter
	@Setter
	private String trustAddress;
	private static final String defaultTrustAddress = DSTARDefines.JpTrustServerAddress;

	@Getter
	@Setter
	private int trustPort;
	private static final int defaultTrustPort = 30001;

	private final ExecutorService workerExecutor;

	private final VoiceDataFromInet voiceDataFromInet = new VoiceDataFromInet();
	private final VoiceHeaderFromInet voiceHeaderFromInet = new VoiceHeaderFromInet();
	private final Poll poll = new Poll();

	private final List<G2Entry> entries;
	private final Lock entriesLocker;

	private final Queue<G2Packet> recvPackets;

	private final Queue<DSTARPacket> readPackets;
	private final Lock readPacketsLocker;

	private final Queue<G2HeaderCacheEntry> headerCaches;
	private final Lock headerCachesLocker;

	SocketIOEntryUDP g2Channel;
	SocketIOEntryUDP proxyChannel;

	private final Timer proxyTransmitPollTimekeeper;
	private final Timer proxyReceivePollTimekeeper;
	private boolean isProxyConnected;

	private final int proxyClientID;

	static {
		logHeader = G123CommunicationService.class.getSimpleName() + " : ";
	}

	public G123CommunicationService(
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		final ThreadUncaughtExceptionListener exceptionListener,
		final SocketIO socketIO
	) {
		super(
			exceptionListener,
			G123CommunicationService.class,
			socketIO,
			BufferEntry.class,
			HostIdentType.RemoteAddressOnly
		);

		this.gateway = gateway;
		this.workerExecutor = workerExecutor;

		entries = new ArrayList<>();
		entriesLocker = new ReentrantLock();

		recvPackets = new LinkedList<>();

		readPackets = new LinkedList<>();
		readPacketsLocker = new ReentrantLock();

		headerCaches = new LinkedList<>();
		headerCachesLocker = new ReentrantLock();

		proxyTransmitPollTimekeeper = new Timer();
		proxyTransmitPollTimekeeper.updateTimestamp();
		proxyReceivePollTimekeeper = new Timer();
		proxyReceivePollTimekeeper.updateTimestamp();
		isProxyConnected = false;

		proxyClientID = DSTARUtils.generateQueryID();

		setProtocolVersion(protocolVersionDefault);

		setPortNumber(g2ProtocolPort);

		setTrustAddress(defaultTrustAddress);
		setTrustPort(defaultTrustPort);
	}

	public G123CommunicationService(
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		this(gateway, workerExecutor, exceptionListener, null);
	}

	@Override
	public boolean start() {
		if(isRunning()){
			if(log.isDebugEnabled())
				log.debug(logHeader + "Already running.");

			return true;
		}

		if(getPortNumber() != 0 && getPortNumber() <= 1023) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Illegal g2 port number = " + getPortNumber() +
					",replace g2 default port number = " + g2ProtocolPort + "."
				);
			}

			setPortNumber(g2ProtocolPort);
		}

		if(
			!super.start(
				new Runnable() {
					@Override
					public void run() {
						g2Channel =
							getSocketIO().registUDP(
								new InetSocketAddress(getPortNumber()),
								G123CommunicationService.this.getHandler(),
								G123CommunicationService.this.getClass().getSimpleName() + "@" + getPortNumber()
							);

						if(isUseProxyGateway()) {
							proxyChannel =
								getSocketIO().registUDP(
									new InetSocketAddress(0),
									G123CommunicationService.this.getHandler(),
									G123CommunicationService.this.getClass().getSimpleName() + "@->" +
									getProxyGatewayAddress()
								);
						}

					}
				}
			) ||
			g2Channel == null ||
			(isUseProxyGateway() && proxyChannel == null)
		){
			this.stop();

			closeChannel();

			return false;
		}

		return true;
	}

	public void stop() {
		super.stop();

		closeChannel();
	}

	public void setProtocolVersion(int protocolVersion) {
		if(protocolVersion < protocolVersionMin)
			this.protocolVersion = protocolVersionMin;
		else if(protocolVersion > protocolVersionMax)
			this.protocolVersion = protocolVersionMax;
		else
			this.protocolVersion = protocolVersion;
	}

	public boolean writePacket(DSTARPacket packet, InetAddress remoteAddress) {
		if(packet == null || remoteAddress == null) {return false;}

		boolean success = true;
		switch(packet.getPacketType()) {
		case DV:
			if(packet.getDVPacket().hasPacketType(PacketType.Header))
				success &= writeHeader(packet, remoteAddress);

			if(packet.getDVPacket().hasPacketType(PacketType.Voice))
				success &= writeVoice(packet, remoteAddress);

			break;

		case DD:
			//TODO
			break;

		default:
			return false;
		}

		wakeupProcessThread();

		return success;
	}

	public DSTARPacket readPacket() {
		readPacketsLocker.lock();
		try {
			if(readPackets.isEmpty())
				return null;
			else
				return readPackets.poll();

		}finally {readPacketsLocker.unlock();}
	}

	public boolean readPacketAll(Queue<DSTARPacket> packetQueue){
		if(packetQueue == null) {return false;}

		boolean readblePackets = false;

		readPacketsLocker.lock();
		try {
			for(Iterator<DSTARPacket> it = readPackets.iterator(); it.hasNext();) {
				DSTARPacket packet = it.next();
				it.remove();

				packetQueue.add(packet);

				readblePackets = true;
			}

			return readblePackets;
		}finally {readPacketsLocker.unlock();}
	}

	@Override
	public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		wakeupProcessThread();
	}

	@Override
	public OperationRequest readEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest acceptedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {

		return null;
	}

	@Override
	public OperationRequest connectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {

	}

	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	) {

	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		proxyTransmitPollTimekeeper.updateTimestamp();
		proxyReceivePollTimekeeper.updateTimestamp();

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		super.threadFinalize();

		closeChannel();

		recvPackets.clear();
	}

	@Override
	protected ThreadProcessResult processThread() {

		if(parsePacket(recvPackets)) {
			for(final Iterator<G2Packet> it = recvPackets.iterator(); it.hasNext();) {
				final G2Packet packet = it.next();
				it.remove();

				switch(packet.getPacketType()) {
				case DV:
					if(packet.getDVPacket().hasPacketType(PacketType.Header))
						onReceiveHeader(packet);

					if(packet.getDVPacket().hasPacketType(PacketType.Voice))
						onReceiveVoice(packet);

					if(packet.getDVPacket().hasPacketType(PacketType.Poll))
						onReceivePoll(packet);

					break;

				case DD:
					//TODO DD
					break;

				default:
					break;
				}
			}
		}

		entriesLocker.lock();
		try {
			findEntry(ConnectionDirectionType.OUTGOING)
			.forEach(new Consumer<G2Entry>() {
				@Override
				public void accept(G2Entry entry) {
					Optional<G2TransmitPacketEntry> transmitPacketEntry = null;
					while((transmitPacketEntry = entry.getTransmitter().outputRead()).isPresent()) {
						final G2TransmitPacketEntry transmitPacket = transmitPacketEntry.get();

						transmitG2PacketToNetwork(
							transmitPacket.getPacketType(),
							transmitPacket.getG2Packet(), entry.isHeaderTransmitted()
						);

						if(transmitPacket.getPacketType() == PacketType.Header) {
							entry.setHeaderTransmitted(true);
						}
					}
				}
			});
		}finally {entriesLocker.unlock();}


		if(isUseProxyGateway()) {
			if(proxyTransmitPollTimekeeper.isTimeout(5, TimeUnit.SECONDS)) {
				proxyTransmitPollTimekeeper.updateTimestamp();

				transmitPollToProxyGateway();
			}

			if(proxyReceivePollTimekeeper.isTimeout(60, TimeUnit.SECONDS)) {
				proxyReceivePollTimekeeper.updateTimestamp();

				if(isProxyConnected) {
					isProxyConnected = false;

					if(log.isWarnEnabled())
						log.warn(logHeader + "Proxy gateway server connection timeout !!");
				}
			}
		}

		cleanupEntries();

		boolean isCanSleep = false;
		entriesLocker.lock();
		try {
			isCanSleep = entries.isEmpty();
		}finally {
			entriesLocker.unlock();
		}

		if(isCanSleep)
			setProcessLoopIntervalTimeMillis(1000L);
		else
			setProcessLoopIntervalTimeMillis(5L);

		return ThreadProcessResult.NoErrors;
	}

	private boolean transmitPollToProxyGateway() {
		if(!isUseProxyGateway()) {return false;}

		final InetSocketAddress destinationAddress =
			new InetSocketAddress(getProxyGatewayAddress(), g2ProtocolPort);
		if(destinationAddress.isUnresolved()) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Could not resolve proxy gateway address " + getProxyGatewayAddress() + ".");

			return false;
		}

		final String gatewayCallsign = getGateway().getGatewayCallsign();

		final Poll poll = new Poll();
		poll.setRemoteId(proxyClientID);
		ArrayUtil.copyOf(poll.getRepeater1Callsign(), gatewayCallsign.toCharArray());
		for(DSTARRepeater repeater : getGateway().getRepeaters())
			poll.getRepeaters().add(repeater.getRepeaterCallsign());

		return writeUDPPacket(
			proxyChannel.getKey(), destinationAddress, ByteBuffer.wrap(poll.assembleCommandData())
		);
	}

	private void cleanupEntries() {
		entriesLocker.lock();
		try {
			for(Iterator<G2Entry> it = entries.iterator(); it.hasNext();) {
				G2Entry entry = it.next();

				if(entry.getActivityTimestamp().isTimeout(30, TimeUnit.SECONDS)) {
					it.remove();

					if(log.isDebugEnabled())
						log.debug(logHeader + "Remove entry.\n" + entry.toString(4));
				}
			}
		}finally {entriesLocker.unlock();}
	}

	private boolean onReceiveHeader(final G2Packet packet) {
		assert packet != null;

		if(!packet.getDVPacket().hasPacketType(PacketType.Header)) {return false;}

		final ProcessResult<G2Entry> newEntryHeader = new ProcessResult<>();

		entriesLocker.lock();
		try {
			findEntry(
				ConnectionDirectionType.INCOMING,
				packet.getRemoteAddress().getAddress(), packet.getRemoteAddress().getPort(),
				packet.getBackBoneHeader().getFrameIDNumber()
			)
			.findFirst()
			.ifPresentOrElse(
				new Consumer<G2Entry>() {
					@Override
					public void accept(G2Entry entry) {

						packet.setConnectionDirection(ConnectionDirectionType.INCOMING);
						packet.setLoopBlockID(entry.getLoopblockID());

						if(entry.getRouteStatus() == G2RouteStatus.Invalid) {
							//無効状態でヘッダを受信したので、有効に切り替える
							entry.setHeader(packet);

							entry.setRouteStatus(G2RouteStatus.Valid);

							addReadPacket(packet);

							addHeaderCache(packet);

							//ゲートウェイに着信を通知
							newEntryHeader.setResult(entry);
						}
						else if(entry.getRouteStatus() == G2RouteStatus.Valid) {
							//受信したヘッダと受信済みヘッダを照合する
							if(!entry.getHeader().getRFHeader().equals(packet.getRFHeader())) {

								if(log.isInfoEnabled()) {
									log.info(
										logHeader +
										"Detected different header in the same frame...\n" +
										"[OldHeader]:\n" +
										entry.getHeader().getRFHeader().toString(4) + "\n" +
										"[NewHeader]\n" +
										packet.getRFHeader().toString(4)
									);
								}

								//異なる場合
								entry.setHeader(packet);

								//一旦、受信を止める
								final DSTARPacket endPacket = packet.clone();
								endPacket.setDVPacket(
									DSTARUtils.createPreLastVoicePacket(
										entry.getFrameID(), (byte)entry.getSequence()
									)
								);
								addReadPacket(endPacket);

								final DSTARPacket lastPacket = packet.clone();
								lastPacket.setDVPacket(
									DSTARUtils.createLastVoicePacket(
										entry.getFrameID(),
										DSTARUtils.getNextShortSequence((byte)entry.getSequence())
									)
								);
								addReadPacket(lastPacket);

								addReadPacket(packet);
							}
						}

						updateHeaderCacheActivityTime(packet);

						entry.getActivityTimestamp().updateTimestamp();
					}
				}
				,new Runnable() {
					public void run() {
						//新規接続
						final G2Entry entry =
							new G2Entry(
								ConnectionDirectionType.INCOMING,
								getProtocolVersion(),
								packet.getBackBoneHeader().getFrameIDNumber()
							);

						entry.setRemoteAddressPort(packet.getRemoteAddress());

						entry.setSequence(0x0);
						entry.setHeader(packet);

						entry.setRouteStatus(G2RouteStatus.Valid);

						entry.getActivityTimestamp().updateTimestamp();

						entries.add(entry);

						if(log.isDebugEnabled())
							log.debug(logHeader + "Created incoming entry by header packet.\n" + entry.toString(4));

						packet.setConnectionDirection(ConnectionDirectionType.INCOMING);
						packet.setLoopBlockID(entry.getLoopblockID());
						addReadPacket(packet);

						//ヘッダキャッシュに格納
						addHeaderCache(packet);

						//ゲートウェイに着信を通知
						newEntryHeader.setResult(entry);
					}
				}
			);
		}finally {entriesLocker.unlock();}


		//ゲートウェイに着信を通知
		if(newEntryHeader.getResult() != null) {
			workerExecutor.submit(new Runnable() {
				@Override
				public void run() {
					getGateway().notifyIncomingPacketFromG123Route(
						String.valueOf(newEntryHeader.getResult().getHeader().getRFHeader().getMyCallsign()),
						newEntryHeader.getResult().getRemoteAddressPort().getAddress()
					);
				}
			});
		}

		return true;
	}

	private boolean onReceiveVoice(final G2Packet packet) {
		assert packet != null;

		if(!packet.getDVPacket().hasPacketType(PacketType.Voice)) {return false;}

		entriesLocker.lock();
		try {
			findEntry(
				ConnectionDirectionType.INCOMING,
				packet.getRemoteAddress().getAddress(), packet.getRemoteAddress().getPort(),
				packet.getBackBoneHeader().getFrameIDNumber()
			)
			.findFirst()
			.ifPresentOrElse(
				new Consumer<G2Entry>() {
					@Override
					public void accept(final G2Entry entry) {

						entry.setSequence(packet.getBackBoneHeader().getSequenceNumber());

						if(entry.getRouteStatus() == G2RouteStatus.Invalid) {
							//ミニデータからの復帰処理
							if(entry.getSlowdataDecoder().decode(packet.getDVData().getDataSegment()) ==
								DataSegmentDecoderResult.Header
							) {
								final ProcessResult<Boolean> valid = new ProcessResult<>(false);

								//ミニデータからのヘッダをデコーダから取得
								final Header header = entry.getSlowdataDecoder().getHeader();

								if(CallSignValidator.isValidUserCallsign(
									header.getMyCallsign()
								)) {
									//レピータ指定ゲート超え
									if(
										CallSignValidator.isValidAreaRepeaterCallsign(header.getYourCallsign()) &&
										packet.getYourCallsign()[0] == '/'
									) {
										final String repeaterCallsign =
											DSTARUtils.convertAreaRepeaterCallToRepeaterCall(
												DSTARUtils.formatFullLengthCallsign(
													String.valueOf(packet.getYourCallsign())
												)
											);

										//Yourにセットされていたレピータは、Nora管理下にあるか？
										if(getGateway().getRepeater(repeaterCallsign) != null) {
											//レピータ1/2書き換え
											header.setRepeater1Callsign(
												getGateway().getGatewayCallsign().toCharArray()
											);
											header.setRepeater2Callsign(repeaterCallsign.toCharArray());

											valid.setResult(true);
										}
									}
									//ユーザー指定ゲート超え
									else if(CallSignValidator.isValidUserCallsign(header.getYourCallsign())){
										//ヘッダキャッシュの中から同一Yourのヘッダを探す
										findHeaderCache(null, String.valueOf(header.getYourCallsign()))
										.findFirst()
										.ifPresent(new Consumer<G2HeaderCacheEntry>() {
											@Override
											public void accept(G2HeaderCacheEntry entry) {
												//レピータ1/2書き換え
												ArrayUtil.copyOf(
													header.getRepeater1Callsign(),
													getGateway().getGatewayCallsign().toCharArray()
												);
												ArrayUtil.copyOf(
													header.getRepeater2Callsign(),
													entry.getHeaderPacket().getRFHeader().getRepeater2Callsign()
												);

												valid.setResult(true);
											}
										});
									}
								}

								if(valid.getResult()) {
									if(log.isDebugEnabled())
										log.debug(logHeader + String.format("Resync frame %04X by slowdata.\n%s", entry.getFrameID(), header.toString(4)));

									packet.getDVPacket().setRfHeader(header);
									packet.getBackBoneHeader().setSequenceNumber((byte)0x0);
									packet.getBackBoneHeader().setFrameType(BackBoneHeaderFrameType.VoiceDataHeader);

									entry.setHeader(packet);
									entry.setRouteStatus(G2RouteStatus.Valid);

									addReadPacket(packet);
								}
							}
						}

						//
						if(entry.getRouteStatus() == G2RouteStatus.Valid) {
							if(entry.getHeader() != null) {
								if(packet.getPacketType() == DSTARPacketType.DD)
									packet.getDDPacket().setRfHeader(entry.getHeader().getRFHeader());
								else if(packet.getPacketType() == DSTARPacketType.DV)
									packet.getDVPacket().setRfHeader(entry.getHeader().getRFHeader());
							}

							addReadPacket(packet);

							if(entry.getHeader() != null) {
								if(
									entry.getSequence() == 0x14 &&
									!packet.isLastFrame()
								) {addReadPacket(entry.getHeader());}

								updateHeaderCacheActivityTime(entry.getHeader());
							}

							if(packet.isLastFrame()) {
								entry.setRouteStatus(G2RouteStatus.Terminated);
							}
						}

						entry.getActivityTimestamp().updateTimestamp();
					}
				}
				,new Runnable() {
					public void run() {
						//新規接続

						final G2Entry entry =
							new G2Entry(
								ConnectionDirectionType.INCOMING,
								getProtocolVersion(),
								packet.getBackBoneHeader().getFrameIDNumber()
							);

						entry.setRemoteAddressPort(packet.getRemoteAddress());

						entry.setSequence(packet.getBackBoneHeader().getSequenceNumber());

						findHeaderCache(packet.getBackBoneHeader().getFrameIDNumber())
						.findFirst()
						.ifPresentOrElse(new Consumer<G2HeaderCacheEntry>() {
							@Override
							public void accept(G2HeaderCacheEntry cache) {
								entry.setHeader(cache.getHeaderPacket());
								entry.setRouteStatus(G2RouteStatus.Valid);

								addReadPacket(cache.getHeaderPacket());
								addReadPacket(packet);
							}
						}, new Runnable() {
							@Override
							public void run() {
								entry.setRouteStatus(G2RouteStatus.Invalid);

								entry.getSlowdataDecoder().decode(packet.getDVData().getDataSegment());
							}
						});

						entry.getActivityTimestamp().updateTimestamp();

						entries.add(entry);

						if(log.isDebugEnabled())
							log.debug(logHeader + "Created incoming entry by voice packet.\n" + entry.toString(4));
					}
				}
			);
		}finally {entriesLocker.unlock();}

		return true;
	}

	private boolean onReceivePoll(final G2Packet packet) {
		if(!packet.getDVPacket().hasPacketType(PacketType.Poll)) {return false;}

		proxyReceivePollTimekeeper.updateTimestamp();
		if(isUseProxyGateway() && !isProxyConnected) {
			isProxyConnected = true;

			if(log.isInfoEnabled())
				log.info(logHeader + "Connected to proxy gateway = " + getProxyGatewayAddress() + ".");
		}

		return true;
	}

	private boolean writeVoice(final DSTARPacket packet, InetAddress remoteAddress) {
		assert packet != null && remoteAddress != null;

		entriesLocker.lock();
		try {
			findEntry(
				ConnectionDirectionType.OUTGOING,
				remoteAddress,
				g2ProtocolPort,
				packet.getBackBoneHeader().getFrameIDNumber()
			)
			.findFirst()
			.ifPresent(
				new Consumer<G2Entry>() {
					@Override
					public void accept(G2Entry entry) {
						writePacket(PacketType.Voice, entry, packet);

						entry.getActivityTimestamp().updateTimestamp();
					}
				}
			);
		}finally {entriesLocker.unlock();}

		return true;
	}

	private boolean writeHeader(final DSTARPacket packet, final InetAddress remoteAddress) {
		assert packet != null && remoteAddress != null;

		entriesLocker.lock();
		try {
			findEntry(
				ConnectionDirectionType.OUTGOING,
				remoteAddress,
				g2ProtocolPort,
				packet.getBackBoneHeader().getFrameIDNumber()
			)
			.findFirst()
			.ifPresentOrElse(
				new Consumer<G2Entry>() {
					@Override
					public void accept(G2Entry entry) {
						entry.getActivityTimestamp().updateTimestamp();
					}
				},
				new Runnable() {
					@Override
					public void run() {
						final G2Entry entry =
							new G2Entry(
								ConnectionDirectionType.OUTGOING,
								getProtocolVersion(),
								packet.getBackBoneHeader().getFrameIDNumber()
							);

						entry.setRemoteAddressPort(new InetSocketAddress(remoteAddress, g2ProtocolPort));

						entry.setRouteStatus(G2RouteStatus.Valid);

						entry.setSequence((byte)0x00);

						entry.setHeader(packet);

						entry.getActivityTimestamp().updateTimestamp();

						entry.getTransmitter().reset();

						entries.add(entry);

						if(log.isDebugEnabled())
							log.debug(logHeader + "Created outgoing entry by header packet.\n" + entry.toString(4));

						writePacket(PacketType.Header, entry, packet);
					}
				}
			);
		}finally {entriesLocker.unlock();}

		return true;
	}

	private boolean writePacket(
		final PacketType packetType, final G2Entry entry, final DSTARPacket packet
	) {
		G2Packet g2Packet = null;

		if(packetType == PacketType.Header) {
			g2Packet = new VoiceHeaderToInet(
				entry.getLoopblockID(),
				entry.getDirection(),
				packet.getDVPacket()
			);
		}
		else if(packetType == PacketType.Voice) {
			g2Packet = new VoiceDataToInet(
				entry.getLoopblockID(),
				entry.getDirection(),
				packet.getDVPacket()
			);
		}
		else {return false;}

		g2Packet.getRFHeader().setRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL);

		g2Packet.getBackBoneHeader().setId((byte)0x20);
		g2Packet.getBackBoneHeader().setSendRepeaterID((byte)0x01);
		g2Packet.getBackBoneHeader().setDestinationRepeaterID((byte)0x01);
		g2Packet.getBackBoneHeader().setSendTerminalID((byte)0x00);

		g2Packet.setRemoteAddress(entry.getRemoteAddressPort());


		switch(packetType) {
		case Header:
			entry.getTransmitter().inputWrite(new G2TransmitPacketEntry(
				packetType, g2Packet, entry.getRemoteAddressPort().getAddress(), g2ProtocolPort,
				FrameSequenceType.Start
			));
			break;

		case Voice:
			entry.getTransmitter().inputWrite(new G2TransmitPacketEntry(
				packetType, g2Packet, entry.getRemoteAddressPort().getAddress(), g2ProtocolPort,
				packet.isLastFrame() ? FrameSequenceType.End : FrameSequenceType.None
			));

			if(
				entry.getProtocolVersion() >= 2 &&
				packet.getDVPacket().getBackBone().isMaxSequence() &&
				!packet.getDVPacket().isEndVoicePacket() &&
				entry.getHeader() != null
			) {
				writePacket(entry.getHeader(), entry.getRemoteAddressPort().getAddress());
			}
			break;

		default:
			return false;
		}

		return true;
	}

	private boolean addReadPacket(DSTARPacket packet) {
		assert packet != null;

		readPacketsLocker.lock();
		try {
			return readPackets.add(packet);
		}finally {readPacketsLocker.unlock();}
	}

	private boolean parsePacket(Queue<G2Packet> receivePackets) {
		assert receivePackets != null;

		boolean update = false;

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			final BufferEntry buffer = opEntry.get();

			buffer.getLocker().lock();
			try {
				if(!buffer.isUpdate()) {continue;}

				buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

				for(Iterator<PacketInfo> itBufferBytes = buffer.getBufferPacketInfo().iterator(); itBufferBytes.hasNext();) {
					final PacketInfo packetInfo = itBufferBytes.next();
					int bufferLength = packetInfo.getPacketBytes();
					itBufferBytes.remove();

					if(bufferLength <= 0) {continue;}

					ByteBuffer receivePacket = ByteBuffer.allocate(bufferLength);
					for(int i = 0; i < bufferLength; i++) {receivePacket.put(buffer.getBuffer().get());}
					BufferState.toREAD(receivePacket, BufferState.WRITE);

					boolean match = false;
					G2Packet parsedCommand = null;
					do {
						if(
							(parsedCommand = parsePacket(voiceDataFromInet, receivePacket)) != null ||
							(parsedCommand = parsePacket(voiceHeaderFromInet, receivePacket)) != null ||
							(parsedCommand = parsePacket(poll, receivePacket)) != null
						) {
							parsedCommand.setRemoteAddress(buffer.getRemoteAddress());

							receivePackets.add(parsedCommand.clone());

							if(log.isTraceEnabled()) {
								receivePacket.rewind();

								log.trace(
									logHeader + "Receive G2 packet.\n" +
									parsedCommand.toString() + "\n" +
									FormatUtil.byteBufferToHexDump(receivePacket, 4)
								);
							}

							update = match = true;
						}else {
							match = false;
						}
					}while(match);
				}

				buffer.setUpdate(false);

			}finally {
				buffer.getLocker().unlock();
			}
		}

		return update;
	}

	private G2Packet parsePacket(G2Packet packet, ByteBuffer buffer) {
		assert packet != null && buffer != null;

		packet.clear();
		packet = packet.parseCommandData(buffer);
		if(packet != null) {
			packet.updateTimestamp();

			return packet;
		}else {
			return null;
		}
	}

	private void closeChannel(){
		if(this.g2Channel != null && this.g2Channel.getChannel().isOpen()) {
			try {
				this.g2Channel.getChannel().close();
				this.g2Channel = null;
			}catch(IOException ex) {
				if(log.isDebugEnabled())
					log.debug("Error occurred at channel close.", ex);
			}
		}
		if(this.proxyChannel != null && this.proxyChannel.getChannel().isOpen()) {
			try {
				this.proxyChannel.getChannel().close();
				this.proxyChannel = null;
			}catch(IOException ex) {
				if(log.isDebugEnabled())
					log.debug("Error occurred at channel close.", ex);
			}
		}
	}

	private boolean transmitG2PacketToNetwork(
		final PacketType packetType, final G2Packet g2Packet, final boolean oneShot
	) {
		assert g2Packet != null;

		final DVPacket packet = g2Packet.getDVPacket();

		// ヘッダのフラグを削除
		if(packetType == PacketType.Header) {
			packet.getRfHeader().replaceCallsignsIllegalCharToSpace();

			packet.getBackBone().setId((byte)0x20);
			packet.getBackBone().setDestinationRepeaterID((byte)0x01);
			packet.getBackBone().setSendRepeaterID((byte)0x01);
			packet.getBackBone().setSendTerminalID((byte)0x00);
			packet.getBackBone().setSequenceNumber((byte)0x0);
			packet.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceDataHeader);

			packet.getRfHeader().setRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL);
			RepeaterControlFlag rptFlg = RepeaterControlFlag.getTypeByValue(packet.getRfHeader().getFlags()[0]);

			if(rptFlg != RepeaterControlFlag.CANT_REPEAT && rptFlg != RepeaterControlFlag.AUTO_REPLY)
				packet.getRfHeader().setRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL);
		}
		else if(packetType == PacketType.Voice) {
			packet.getBackBone().setId((byte)0x20);
			packet.getBackBone().setDestinationRepeaterID((byte)0x01);
			packet.getBackBone().setSendRepeaterID((byte)0x01);
			packet.getBackBone().setSendTerminalID((byte)0x00);

		}

		//ヘッダであれば5パケット繰り返して送信する
		//ただし、ワンショット指定があれば単発とする
		final int txLimit = !oneShot && packetType == PacketType.Header ? 5 : 1;

		byte[] transmitBytes = g2Packet.assembleCommandData();
		for(int c = 0; c < txLimit; c++) {
			super.writeUDPPacket(
					isUseProxyGateway() ? proxyChannel.getKey() : g2Channel.getKey(),
					g2Packet.getRemoteAddress(),
					ByteBuffer.wrap(transmitBytes)
			);
		}

		if(log.isTraceEnabled()) {
			log.trace(
				logHeader +
				"Transmit G2 packet.\n" + g2Packet.toString(4) + "\n" +
				FormatUtil.bytesToHexDump(transmitBytes, 4)
			);
		}

		return true;
	}

	@SuppressWarnings("unused")
	private Stream<G2Entry> findEntry(){
		return findEntry(null, null, null, -1, -1, -1);
	}

	private Stream<G2Entry> findEntry(
		ConnectionDirectionType direction
	){
		return findEntry(direction, null, null, -1, -1, -1);
	}

	private Stream<G2Entry> findEntry(
		ConnectionDirectionType direction,
		InetAddress remoteAddress,
		int remotePort,
		int frameID
	){
		return findEntry(direction, null, remoteAddress, remotePort, frameID, -1);
	}

	private Stream<G2Entry> findEntry(
		final ConnectionDirectionType direction,
		final G2RouteStatus routeStatus,
		final InetAddress remoteAddress,
		final int remotePort,
		final int frameID,
		final int sequence
	){
		entriesLocker.lock();
		try {
			Stream<G2Entry> result =
				Stream.of(entries)
				.filter(new Predicate<G2Entry>() {
					@Override
					public boolean test(G2Entry entry) {
						boolean match =
							(
								direction == null ||
								entry.getDirection() == direction
							) &&
							(
								routeStatus == null ||
								entry.getRouteStatus() == routeStatus
							) &&
							(
								remoteAddress == null ||
								(
									entry.getRemoteAddressPort() != null &&
									entry.getRemoteAddressPort().getAddress() != null &&
									entry.getRemoteAddressPort().getAddress().equals(remoteAddress)
								)
							) &&
							(
								remotePort < 0 ||
								(
									entry.getRemoteAddressPort() != null &&
									entry.getRemoteAddressPort().getPort() == remotePort
								)
							) &&
							(
								frameID < 0 ||
								entry.getFrameID() == frameID
							) &&
							(
								sequence < 0 ||
								entry.getSequence() == sequence
							);

						return match;
					}
				});

			return result;
		}finally {
			entriesLocker.unlock();
		}
	}

	private boolean updateHeaderCacheActivityTime(DSTARPacket header) {
		assert header != null;

		final int frameID = header.getBackBoneHeader().getFrameIDNumber();

		final ProcessResult<Boolean> result = new ProcessResult<>(false);

		findHeaderCache(frameID)
		.forEach(new Consumer<G2HeaderCacheEntry>() {
			@Override
			public void accept(G2HeaderCacheEntry entry) {
				entry.getActivityTime().updateTimestamp();

				result.setResult(true);
			}
		});

		return result.getResult();
	}

	private boolean addHeaderCache(DSTARPacket header) {
		final int frameID = header.getPacketType() == DSTARPacketType.DD ?
			header.getDDPacket().getBackBone().getFrameIDNumber() : header.getDVPacket().getBackBone().getFrameIDNumber();

		headerCachesLocker.lock();
		try {
			for(Iterator<G2HeaderCacheEntry> it = headerCaches.iterator(); it.hasNext();) {
				G2HeaderCacheEntry entry = it.next();

				if(entry.getFrameID() == frameID) {it.remove();}
			}

			while(headerCaches.size() >= headerCacheLimit) {
				final ProcessResult<Boolean> error = new ProcessResult<>(false);

				findHeaderCache()
				.min(
					ComparatorCompat.<G2HeaderCacheEntry>comparingLong(new ToLongFunction<G2HeaderCacheEntry>() {
						@Override
						public long applyAsLong(G2HeaderCacheEntry entry) {
							return entry.getActivityTime().getTimestampMilis();
						}
					})
				)
				.ifPresentOrElse(
					new Consumer<G2HeaderCacheEntry>() {
						@Override
						public void accept(G2HeaderCacheEntry entry) {
							if(headerCaches.remove(entry)) {
								if(log.isDebugEnabled())
									log.debug(logHeader + "Deleted header cache entry.\n" + entry.toString(4));
							}
							else{error.setResult(true);}
						}
					},
					new Runnable() {
						@Override
						public void run() {
							error.setResult(true);
						}
					}
				);

				if(error.getResult()) {throw new AssertionError();}
			}

			final G2HeaderCacheEntry cacheEntry = new G2HeaderCacheEntry(frameID, header);
			cacheEntry.getActivityTime().updateTimestamp();

			return headerCaches.add(cacheEntry);

		}finally {headerCachesLocker.unlock();}
	}

	private Stream<G2HeaderCacheEntry> findHeaderCache(){
		return findHeaderCache(-1, null, null, null, null);
	}

	private Stream<G2HeaderCacheEntry> findHeaderCache(String myCallsign, String yourCallsign){
		return findHeaderCache(-1, myCallsign, yourCallsign, null, null);
	}

	private Stream<G2HeaderCacheEntry> findHeaderCache(int frameID){
		return findHeaderCache(frameID, null, null, null, null);
	}

	private Stream<G2HeaderCacheEntry> findHeaderCache(
		final int frameID,
		final String myCallsign,
		final String yourCallsign,
		final String repeater1Callsign,
		final String repeater2Callsign
	){
		headerCachesLocker.lock();
		try {
			Stream<G2HeaderCacheEntry> result =
				Stream.of(headerCaches)
				.filter(new Predicate<G2HeaderCacheEntry>() {

					@Override
					public boolean test(G2HeaderCacheEntry entry) {
						final Header cacheHeader = entry.getHeaderPacket().getPacketType() == DSTARPacketType.DD ?
							entry.getHeaderPacket().getDDPacket().getRfHeader() : entry.getHeaderPacket().getDVPacket().getRfHeader();

						final boolean match =
							(
								frameID < 0 ||
								entry.getFrameID() == frameID
							) &&
							(
								myCallsign == null ||
								Arrays.equals(
									cacheHeader.getMyCallsign(),
									myCallsign.toCharArray()
								)
							) &&
							(
								yourCallsign == null ||
								Arrays.equals(
									cacheHeader.getYourCallsign(),
									yourCallsign.toCharArray()
								)
							) &&
							(
								repeater1Callsign == null ||
								Arrays.equals(
									cacheHeader.getRepeater1Callsign(),
									repeater1Callsign.toCharArray()
								)
							) &&
							(
								repeater2Callsign == null ||
								Arrays.equals(
									cacheHeader.getRepeater2Callsign(),
									repeater2Callsign.toCharArray()
								)
							);

						return match;
					}

				})
				.sorted(ComparatorCompat.comparingLong(new ToLongFunction<G2HeaderCacheEntry>() {
					@Override
					public long applyAsLong(G2HeaderCacheEntry entry) {
						return entry.getActivityTime().getTimestampMilis();
					}
				}).reversed());

			return result;
		}finally {headerCachesLocker.unlock();}
	}

	@SuppressWarnings("unused")
	private boolean removeHeaderCache(final int frameID) {
		headerCachesLocker.lock();
		try {
			for(final Iterator<G2HeaderCacheEntry> it = headerCaches.iterator(); it.hasNext();) {
				final G2HeaderCacheEntry cache = it.next();

				if(cache.getFrameID() == frameID) {
					it.remove();

					return true;
				}
			}
		}finally {
			headerCachesLocker.unlock();
		}

		return false;
	}
}
