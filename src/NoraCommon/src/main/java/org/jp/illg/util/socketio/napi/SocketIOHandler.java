package org.jp.illg.util.socketio.napi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.ProcessResult;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIO.ChannelType;
import org.jp.illg.util.socketio.SocketIO.SocketIOProcessingHandlerInterface;
import org.jp.illg.util.socketio.SocketIOEntry;
import org.jp.illg.util.socketio.SocketIOEntryTCPClient;
import org.jp.illg.util.socketio.SocketIOEntryTCPServerClient;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.BufferProtocol;
import org.jp.illg.util.socketio.model.ErrorEvent;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.model.OperationSet;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.define.PacketTracerFunction;
import org.jp.illg.util.socketio.napi.define.ParserFunction;
import org.jp.illg.util.socketio.napi.define.UnknownPacketHandler;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.KeyEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SocketIOHandler<BUFT extends BufferEntry> implements SocketIOProcessingHandlerInterface{

	private static final int udpBufferSize = 1024 * 128;	// 128k


	private static final String logHeader;

	/**
	 * Properties
	 */

	/**
	 * UDPバッファサイズ
	 */
	@Getter
	@Setter
	private int bufferSizeUDP;
	private static final int bufferSizeUDPDefault = 1024 * 32;

	/**
	 * TCPバッファサイズ
	 */
	@Getter
	@Setter
	private int bufferSizeTCP;
	private static final int bufferSizeTCPDefault = 1024 * 128;

	/**
	 * ダイレクトバッファフラグ
	 */
	@Getter
	@Setter
	private boolean directBuffer;
	private static final boolean directBufferDefault = true;


	private final HostIdentType hostIdentType;

	/**
	 * 上位側ハンドラ
	 */
	@Getter
	private final SocketIOHandlerInterface handler;

	/**
	 * ネットワーク側セレクタ
	 */
	@Getter
	private final SocketIO socketIO;

	/**
	 * ネットワーク側セレクタを内包するか
	 */
	@Getter
	private final boolean socketIOInternal;

	/**
	 * 受信側バッファエントリクラス
	 */
	private final Class<BUFT> bufferEntryClass;

	/**
	 * UDP受信用テンポラリバッファ
	 */
	private final ByteBuffer udpBuffer;

	/**
	 * UDP受信用テンポラリバッファロッカー
	 */
	private final Lock udpBufferLocker;

	/**
	 * 受信バッファマップ
	 */
	private final Map<SelectionKey, KeyEntry<BUFT>> readBuffers;

	/**
	 * 受信バッファロッカー
	 */
	private final Lock readBuffersLocker;

	/**
	 * 受信バッファ最終掃除絶対時間
	 */
	private final Timer readBuffersCleanTimestamp;

	/**
	 * 送信バッファマップ
	 */
	private final Map<SelectionKey, KeyEntry<BufferEntry>> writeBuffers;

	/**
	 * 送信バッファロッカー
	 */
	private final Lock writeBuffersLocker;

	/**
	 * 送信バッファロッカー
	 */
	private final Timer writeBuffersCleanTimestamp;


	/**
	 * スタティックイニシャライザ
	 */
	static {
		logHeader = SocketIOHandlerWithThread.class.getSimpleName() + " : ";
	}

	/**
	 * コンストラクタ
	 *
	 * @param handler 上位側ハンドラ
	 * @param bufferEntryClass 受信バッファエントリクラス
	 * @param hostIdentType 受信バッファ区別タイプ
	 */
	public SocketIOHandler(
		@NonNull final SocketIOHandlerInterface handler,
		final Class<BUFT> bufferEntryClass, final HostIdentType hostIdentType
	) {
		this(handler, null, null, null, bufferEntryClass, hostIdentType);
	}

	/**
	 * コンストラクタ
	 *
	 * @param handler 上位側ハンドラ
	 * @param exceptionListener スレッド例外補足リスナ
	 * @param bufferEntryClass 受信バッファエントリクラス
	 * @param hostIdentType 受信バッファ区別タイプ
	 */
	public SocketIOHandler(
		@NonNull final SocketIOHandlerInterface handler,
		final ThreadUncaughtExceptionListener exceptionListener,
		final Class<BUFT> bufferEntryClass, final HostIdentType hostIdentType
	) {
		this(handler, null, null, exceptionListener, bufferEntryClass, hostIdentType);
	}

	/**
	 * コンストラクタ
	 *
	 * @param handler 上位側ハンドラ
	 * @param socketIO ソケットIO
	 * @param exceptionListener スレッド例外補足リスナ
	 * @param bufferEntryClass 受信バッファエントリクラス
	 * @param hostIdentType 受信バッファ区別タイプ
	 */
	public SocketIOHandler(
		@NonNull final SocketIOHandlerInterface handler,
		final SocketIO socketIO,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull Class<BUFT> bufferEntryClass, final HostIdentType hostIdentType
	) {
		this(handler, socketIO, null, exceptionListener, bufferEntryClass, hostIdentType);
	}

	/**
	 * コンストラクタ
	 *
	 * @param handler 上位側ハンドラ
	 * @param socketIO ネットワークセレクタ
	 * @param exceptionListener 例外補足リスナ
	 * @param bufferEntryClass 受信バッファエントリクラス
	 * @param hostIdentType 受信バッファ区別タイプ
	 */
	public SocketIOHandler(
		@NonNull final SocketIOHandlerInterface handler,
		final SocketIO socketIO, final ExecutorService workerExecutor,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull Class<BUFT> bufferEntryClass, final HostIdentType hostIdentType
	) {
		super();

		this.handler = handler;

		if(socketIO != null) {
			this.socketIO = socketIO;
			socketIOInternal = false;
		}
		else {
			ExecutorService worker = workerExecutor;
			if(worker == null) {
				worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
					@Override
					public Thread newThread(Runnable r) {
						final Thread thread = new Thread(r);
						thread.setName("SocketIOWorker_" + thread.getId());

						return thread;
					}
				});
			}
			this.socketIO = new SocketIO(exceptionListener, worker);
			socketIOInternal = true;
		}

		this.bufferEntryClass = bufferEntryClass;

		if (hostIdentType != null)
			this.hostIdentType= hostIdentType;
		else
			this.hostIdentType = HostIdentType.RemoteAddressPort;

		readBuffers = new ConcurrentHashMap<>();
		readBuffersLocker = new ReentrantLock();
		readBuffersCleanTimestamp = new Timer();
		writeBuffers = new ConcurrentHashMap<>();
		writeBuffersLocker = new ReentrantLock();
		writeBuffersCleanTimestamp = new Timer();

		udpBuffer = ByteBuffer.allocateDirect(udpBufferSize);
		udpBufferLocker = new ReentrantLock();

		bufferSizeTCP = bufferSizeTCPDefault;
		bufferSizeUDP = bufferSizeUDPDefault;
		directBuffer = directBufferDefault;
	}

	@Override
	public OperationRequest socketIOReadEvent(SocketIOEntry<? extends SelectableChannel> entry) {
		if(
			entry.getChannelType() != ChannelType.TCPServerClient &&
			entry.getChannelType() != ChannelType.TCPClient &&
			entry.getChannelType() != ChannelType.UDP
		) {return null;}

		KeyEntry<BUFT> keyEntry = null;
		readBuffersLocker.lock();
		try {
			keyEntry = readBuffers.get(entry.getKey());
			if(keyEntry == null) {
				keyEntry = new KeyEntry<>(
					entry.getKey(), hostIdentType, bufferEntryClass, bufferSizeTCP, bufferSizeUDP, directBuffer,
					entry.getChannelType() != ChannelType.UDP ? entry.getRemoteAddress() : null, entry.getLocalAddress()
				);

				readBuffers.put(entry.getKey(), keyEntry);

				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"Creating read key entry.\n" + keyEntry.toString(4)
					);
				}
			}
		}finally {
			readBuffersLocker.unlock();
		}

		final OperationRequest operationSet = new OperationRequest(OperationSet.READ);

		InetSocketAddress localAddress,remoteAddress;
		int numReadBytes = 0;

		try {
			keyEntry.getLocker().lock();
			try {
				keyEntry.updateActivityTime();

				BUFT bufferEntry = null;

				switch(entry.getChannelType()) {
				case TCPServerClient:
					final SocketIOEntryTCPServerClient tcpServerClientEntry = (SocketIOEntryTCPServerClient)entry;
					localAddress = tcpServerClientEntry.getLocalAddress();
					remoteAddress = tcpServerClientEntry.getRemoteAddress();

					bufferEntry = keyEntry.getTCPBuffer();
					if(bufferEntry == null) {return operationSet;}

					bufferEntry.getLocker().lock();
					try {
						bufferEntry.setBufferState(
							BufferState.toWRITE(bufferEntry.getBuffer(), bufferEntry.getBufferState())
						);
						numReadBytes = tcpServerClientEntry.getChannel().read(bufferEntry.getBuffer());
						if(numReadBytes >= 1 || numReadBytes == -1) {
							bufferEntry.getBufferPacketInfo().add(
								new PacketInfo((numReadBytes > 0 ? numReadBytes : 0), false)
							);
							bufferEntry.setUpdate(true);

							bufferEntry.updateActivityTime();
						}

						bufferEntry.setBufferState(
							BufferState.toREAD(bufferEntry.getBuffer(), bufferEntry.getBufferState())
						);

						if(log.isTraceEnabled()) {
							bufferEntry.getBuffer().position(
								bufferEntry.getBuffer().limit() - (numReadBytes > 0 ? numReadBytes : 0)
							);

							log.trace(
								logHeader +
								"Receive TCPServerClient packet from " + remoteAddress + " via " + localAddress + ".\n" +
								(numReadBytes > 0 ? FormatUtil.byteBufferToHexDump(bufferEntry.getBuffer(), 4) : "[END]")
							);

							bufferEntry.getBuffer().rewind();
						}
					}finally {
						bufferEntry.getLocker().unlock();
					}

					if(numReadBytes == 0) {return operationSet;}

					break;

				case TCPClient:
					final SocketIOEntryTCPClient tcpClientEntry = (SocketIOEntryTCPClient)entry;
					localAddress = tcpClientEntry.getLocalAddress();
					remoteAddress = tcpClientEntry.getRemoteAddress();

					bufferEntry = keyEntry.getTCPBuffer();
					if(bufferEntry == null) {return operationSet;}


					bufferEntry.getLocker().lock();
					try {
						bufferEntry.setBufferState(
							BufferState.toWRITE(bufferEntry.getBuffer(), bufferEntry.getBufferState())
						);
						numReadBytes =  tcpClientEntry.getChannel().read(bufferEntry.getBuffer());
						if(numReadBytes >= 1 || numReadBytes == -1) {
							bufferEntry.getBufferPacketInfo().add(
								new PacketInfo((numReadBytes > 0 ? numReadBytes : 0), false)
							);
							bufferEntry.setUpdate(true);

							bufferEntry.updateActivityTime();
						}

						bufferEntry.setBufferState(
							BufferState.toREAD(bufferEntry.getBuffer(), bufferEntry.getBufferState())
						);

						if(log.isTraceEnabled()) {;
							bufferEntry.getBuffer().position(
								bufferEntry.getBuffer().limit() - (numReadBytes > 0 ? numReadBytes : 0)
							);

							log.trace(
								logHeader +
								"Receive TCPClient packet from " + remoteAddress + " via " + localAddress + ".\n" +
								(numReadBytes > 0 ? FormatUtil.byteBufferToHexDump(bufferEntry.getBuffer(), 4) : "    [END]")
							);

							bufferEntry.getBuffer().rewind();
						}
					}finally {
						bufferEntry.getLocker().unlock();
					}

					if(numReadBytes == 0) {return operationSet;}
					break;

				case UDP:
					final SocketIOEntryUDP udpEntry = (SocketIOEntryUDP)entry;
					localAddress = udpEntry.getLocalAddress();

					udpBufferLocker.lock();
					try {
						udpBuffer.clear();

						final SocketAddress udpSA = udpEntry.getChannel().receive(udpBuffer);
						if(udpSA == null || !(udpSA instanceof InetSocketAddress)) {return null;}
						remoteAddress = (InetSocketAddress)udpSA;
						numReadBytes = udpBuffer.position();
						udpBuffer.flip();

						if(numReadBytes <= 0) {return operationSet;}

						bufferEntry = keyEntry.getBuffer(remoteAddress, localAddress);
						if(bufferEntry == null) {return operationSet;}

						bufferEntry.getLocker().lock();
						try {
							bufferEntry.setBufferState(
								BufferState.toWRITE(bufferEntry.getBuffer(), bufferEntry.getBufferState())
							);
							if(bufferEntry.getBuffer().remaining() >= udpBuffer.remaining())
								bufferEntry.getBuffer().put(udpBuffer);
							else {
								final int overflow = udpBuffer.remaining() - bufferEntry.getBuffer().remaining();
								udpBuffer.limit(udpBuffer.limit() - overflow);

								bufferEntry.getBuffer().put(udpBuffer);

								numReadBytes = udpBuffer.position();

								if(log.isDebugEnabled()) {
									log.debug(
										logHeader +
										"UDP read buffer " + overflow + " bytes overflow, " +
										"RemoteAddress = " + remoteAddress + "/LocalAddress = " + localAddress
									);
								}
							}
							if(numReadBytes >= 1) {
								bufferEntry.getBufferPacketInfo().add(new PacketInfo(numReadBytes, false));
								bufferEntry.setUpdate(true);

								bufferEntry.updateActivityTime();
							}

							bufferEntry.setBufferState(
								BufferState.toREAD(bufferEntry.getBuffer(), bufferEntry.getBufferState())
							);

							if(log.isTraceEnabled()) {
								bufferEntry.getBuffer().position(bufferEntry.getBuffer().limit() - numReadBytes);

								log.trace(
									logHeader +
									"Receive UDP packet from " + remoteAddress + " via " + localAddress + ".\n" +
									FormatUtil.byteBufferToHexDump(bufferEntry.getBuffer(), 4)
								);

								bufferEntry.getBuffer().rewind();
							}
						}finally {
							bufferEntry.getLocker().unlock();
						}
					}finally {
						udpBufferLocker.unlock();
					}

					break;

				case TCPServer:
				case Unknown:
				default:
					throw new RuntimeException();
				}
			}finally {
				keyEntry.getLocker().unlock();
			}

		}catch(ClosedChannelException ex) {

			return null;
		}catch(IOException ex) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Error occurred at socket read().", ex);

			socketIOErrorEvent(entry, ex);

			return null;
		}

		//終端検出
		if(numReadBytes < 0) {
			if(log.isDebugEnabled()) {
				log.debug(
					logHeader +
					"Disconnected, Protocol:" + entry.getChannelType() +
					"/RemoteAddress:" + remoteAddress +
					"/LocalAddress:" + localAddress
				);
			}

			handler.disconnectedEvent(
				entry.getKey(),
				ChannelProtocol.toChannelProtocol(entry.getChannelType()),
				localAddress, remoteAddress
			);

			//終端に達したのでチャネルをクローズする
			closeChannel(entry);

//			numReadBytes = 0;
		}

		//イベント実行
		operationSet.combine(
			handler.readEvent(
				keyEntry.getKey(),
				ChannelProtocol.toChannelProtocol(entry.getChannelType()),
				localAddress, remoteAddress
			)
		);

		if(remoteAddress != null)
			handler.updateReceiveBuffer(remoteAddress, numReadBytes >= 0 ? numReadBytes : -1);

		cleanReadBuffer();

		return operationSet;
	}

	@Override
	public OperationRequest socketIOWriteEvent(SocketIOEntry<? extends SelectableChannel> entry) {
		if(
			entry.getChannelType() != ChannelType.TCPServerClient &&
			entry.getChannelType() != ChannelType.TCPClient &&
			entry.getChannelType() != ChannelType.UDP
		) {return null;}

		final OperationRequest operationSet = new OperationRequest();

		KeyEntry<BufferEntry> keyEntry = null;
		writeBuffersLocker.lock();
		try {
			keyEntry = writeBuffers.get(entry.getKey());
		}finally {
			writeBuffersLocker.unlock();
		}
		if(keyEntry == null) {
			operationSet.addUnsetRequest(OperationSet.WRITE);

			return operationSet;
		}


		final List<ErrorEvent> errorEvents = new ArrayList<>(1);

		final ProcessResult<Boolean> dataRemain = new ProcessResult<>(false);
		final ProcessResult<Boolean> disconnected = new ProcessResult<>(false);

		writeKeyEntry(entry, keyEntry, errorEvents, dataRemain, disconnected);

		//全て書き込めなければ、再度、書き込み可能状態になるまで待機
		if(dataRemain.getResult()) {
			operationSet.addSetRequest(OperationSet.WRITE);
		}
		else {
			operationSet.addUnsetRequest(OperationSet.WRITE);
		}

		//エラーイベントがあればコールする
		for(final Iterator<ErrorEvent> it = errorEvents.iterator(); it.hasNext();) {
			final ErrorEvent event = it.next();
			it.remove();

			if(log.isWarnEnabled())
				log.warn(logHeader + "Channel transmit error occurred.", event.getException());

			socketIOErrorEvent(event.getEntry(), event.getException());
		}

		//切断要求が処理されていれば、切断イベントをコール
		if(disconnected.getResult()) {
			handler.disconnectedEvent(
				entry.getKey(),
				ChannelProtocol.toChannelProtocol(entry.getChannelType()),
				entry.getLocalAddress(), entry.getRemoteAddress()
			);
		}

		cleanWriteBuffer();

		return operationSet;
	}

	@Override
	public void socketIOErrorEvent(SocketIOEntry<? extends SelectableChannel> entry, Exception ex) {
		final InetSocketAddress localAddress = getLocalAddress(entry);
		final InetSocketAddress remoteAddress = getRemoteAddress(entry);

		handler.errorEvent(
				entry.getKey(),
				ChannelProtocol.toChannelProtocol(entry.getChannelType()),
				localAddress, remoteAddress, ex
		);
	}

	@Override
	public OperationRequest socketIOAcceptedEvent(SocketIOEntry<? extends SelectableChannel> entry) {
		final InetSocketAddress localAddress = getLocalAddress(entry);
		final InetSocketAddress remoteAddress = getRemoteAddress(entry);

		return handler.acceptedEvent(
				entry.getKey(),
				ChannelProtocol.toChannelProtocol(entry.getChannelType()),
				localAddress, remoteAddress
		);
	}

	@Override
	public OperationRequest socketIOConnectedEvent(SocketIOEntry<? extends SelectableChannel> entry) {
		final InetSocketAddress localAddress = getLocalAddress(entry);
		final InetSocketAddress remoteAddress = getRemoteAddress(entry);

		return handler.connectedEvent(
				entry.getKey(),
				ChannelProtocol.toChannelProtocol(entry.getChannelType()),
				localAddress, remoteAddress
		);
	}

	public boolean hasReceivedReadBuffer() {
		return getReceivedReadBuffer().isPresent();
	}

	public Optional<BUFT> getReceivedReadBuffer() {
		readBuffersLocker.lock();
		try {
			for (final KeyEntry<BUFT> entry : readBuffers.values()) {
				entry.getLocker().lock();
				try {
					if(
						entry.getProtocol() == BufferProtocol.TCP &&
						entry.getTCPBuffer().isUpdate()
					) {
						return Optional.of(entry.getTCPBuffer());
					}
					else if(entry.getProtocol() == BufferProtocol.UDP) {
						for(final BUFT buffer : entry.getUDPBuffer()) {
							if(buffer.isUpdate()) {
								return Optional.of(buffer);
							}
						}
					}
				}finally {
					entry.getLocker().unlock();
				}
			}
		} finally {
			readBuffersLocker.unlock();
		}

		return Optional.empty();
	}

	public boolean parseReceivedReadBuffer(
		@NonNull final ParserFunction parser
	) {
		return parseReceivedReadBuffer(null, parser, null);
	}

	public boolean parseReceivedReadBuffer(
		final PacketTracerFunction packetTracer,
		@NonNull final ParserFunction parser,
		final UnknownPacketHandler unknownPacketHandler
	) {
		boolean update = false;

		Optional<BUFT> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			final BUFT buffer = opEntry.get();

			buffer.getLocker().lock();
			try {
				if(!buffer.isUpdate()) {continue;}

				buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

				for (final Iterator<PacketInfo> it = buffer.getBufferPacketInfo().iterator();
					it.hasNext();
				) {
					final PacketInfo packetInfo = it.next();
					final int bufferLength = packetInfo.getPacketBytes();
					it.remove();

					if (bufferLength <= 0) {continue;}

					final ByteBuffer receiveBuffer = ByteBuffer.allocate(bufferLength);
					for (int i = 0; i < bufferLength; i++) {
						receiveBuffer.put(buffer.getBuffer().get());
					}
					BufferState.toREAD(receiveBuffer, BufferState.WRITE);

					if(packetTracer != null) {
						try {
							packetTracer.apply(
								receiveBuffer,
								buffer.getRemoteAddress(), buffer.getLocalAddress()
							);
						}finally {
							receiveBuffer.rewind();
						}
					}

					boolean match = false;
					do {
						if(parser.apply(
							receiveBuffer,
							bufferLength,
							buffer.getRemoteAddress(),
							buffer.getLocalAddress()
						))
							update = match = true;
						else
							match = false;

						final int limit = receiveBuffer.remaining();
						receiveBuffer.compact();
						receiveBuffer.rewind();
						receiveBuffer.limit(limit);
					} while (match);

					if(unknownPacketHandler != null && receiveBuffer.hasRemaining()) {
						unknownPacketHandler.apply(
							receiveBuffer,
							buffer.getRemoteAddress(), buffer.getLocalAddress()
						);
					}
				}
			}finally{
				buffer.setUpdate(false);
				buffer.getLocker().unlock();
			}
		}

		return update;
	}

	public @NonNull Optional<BUFT> getReadBufferUDP(
		@NonNull final SelectionKey key, @NonNull final InetSocketAddress dstAddress
	){
		return getReadBuffer(key, dstAddress);
	}

	public @NonNull Optional<BUFT> getReadBufferTCP(
		@NonNull final SelectionKey key
	){
		return getReadBuffer(key, null);
	}

	public @NonNull Optional<BUFT> getReadBuffer(
		@NonNull final SelectionKey key, final InetSocketAddress dstAddress
	){
		readBuffersLocker.lock();
		try {
			final KeyEntry<BUFT> keyEntry = readBuffers.get(key);
			if(keyEntry == null) {return Optional.empty();}

			if(keyEntry.getProtocol() == BufferProtocol.TCP)
				return Optional.ofNullable(keyEntry.getTCPBuffer());
			else if(keyEntry.getProtocol() == BufferProtocol.UDP && dstAddress != null) {
				final InetSocketAddress localAddress = getLocalAddressBySelectionKey(key);

				return Optional.ofNullable(keyEntry.getBuffer(dstAddress, localAddress));
			}
			else
				return Optional.empty();
		}finally {
			readBuffersLocker.unlock();
		}
	}

	public boolean writeUDP(
		@NonNull SelectionKey key, @NonNull InetSocketAddress dstAddress, @NonNull ByteBuffer buffer
	) {
		if(!buffer.hasRemaining()) {return false;}

		return this.writeBuffer(ChannelType.UDP, key, buffer, dstAddress, true, false, false);
	}

	public boolean writeUDPPacket(
		@NonNull SelectionKey key, @NonNull InetSocketAddress dstAddress, @NonNull ByteBuffer buffer
	) {
		if(!buffer.hasRemaining()) {return false;}

		return this.writeBuffer(ChannelType.UDP, key, buffer, dstAddress, false, false, false);
	}

	public boolean writeTCP(
		@NonNull SelectionKey key, @NonNull ByteBuffer buffer
	) {
		if(!buffer.hasRemaining()) {return false;}

		return this.writeBuffer(ChannelType.TCPClient, key, buffer, null, true, false, false);
	}

	public boolean writeTCPPacket(
		@NonNull SelectionKey key, @NonNull ByteBuffer buffer
	) {
		if(!buffer.hasRemaining()) {return false;}

		return this.writeBuffer(ChannelType.TCPClient, key, buffer, null, false, false, false);
	}

	protected boolean isWriteCompleted(@NonNull SelectionKey key) {
		writeBuffersLocker.lock();
		try {
			final KeyEntry<BufferEntry> keyEntry = writeBuffers.get(key);
			if(keyEntry == null) {return false;}

			return keyEntry.isBufferEmpty();
		}finally {writeBuffersLocker.unlock();}
	}

	public boolean disconnectTCP(@NonNull SelectionKey key) {
		return this.writeBuffer(ChannelType.TCPClient, key, null, null, false, false, true);
	}

	public static void closeChannel(SocketIOEntry<? extends SelectableChannel> entry) {
		if(entry == null) {return;}

		try {
			if(entry.getChannel() != null && entry.getChannel().isOpen())
				entry.getChannel().close();
		}catch(IOException ex) {
			if(log.isDebugEnabled())
				log.debug("Channel close error.", ex);
		}
	}

	public static void closeChannel(SelectionKey key) {
		if(key == null) {return;}

		try {
			key.channel().close();
		}catch(IOException ex) {
			if(log.isDebugEnabled())
				log.debug("Channel close error.", ex);
		}
	}

	public void cleanBuffer() {
		cleanReadBuffer();
		cleanWriteBuffer();
	}

	public void clearBuffer(){
		readBuffersLocker.lock();
		try{
			for(final Iterator<KeyEntry<BUFT>> it = readBuffers.values().iterator(); it.hasNext();) {
				final KeyEntry<BUFT> keyEntry = it.next();
				it.remove();

				keyEntry.getLocker().lock();
				try {
					keyEntry.clear();
				}finally {
					keyEntry.getLocker().unlock();
				}
			}
		}finally{
			readBuffersLocker.unlock();
		}

		writeBuffersLocker.lock();
		try {
			for(final Iterator<KeyEntry<BufferEntry>> it = writeBuffers.values().iterator(); it.hasNext();) {
				final KeyEntry<BufferEntry> keyEntry = it.next();
				it.remove();

				keyEntry.getLocker().lock();
				try {
					keyEntry.clear();
				}finally {
					keyEntry.getLocker().unlock();
				}
			}
		}finally {
			writeBuffersLocker.unlock();
		}
	}

	private InetSocketAddress getLocalAddressBySelectionKey(final SelectionKey key) {
		InetSocketAddress localAddress = null;

		if(key.channel() instanceof SocketChannel)
			localAddress = (InetSocketAddress)((SocketChannel)key.channel()).socket().getLocalSocketAddress();
		else if(key.channel() instanceof DatagramChannel)
			localAddress = (InetSocketAddress)((DatagramChannel)key.channel()).socket().getLocalSocketAddress();
		else
			throw new RuntimeException("Illegal channel type = " + key.channel() + ".");

		return localAddress;
	}

	private InetSocketAddress getRemoteAddressBySelectionKey(final SelectionKey key) {
		InetSocketAddress remoteAddress = null;

		if(key.channel() instanceof SocketChannel)
			remoteAddress = (InetSocketAddress)((SocketChannel)key.channel()).socket().getRemoteSocketAddress();
		else if(key.channel() instanceof DatagramChannel)
			remoteAddress = (InetSocketAddress)((DatagramChannel)key.channel()).socket().getRemoteSocketAddress();
		else
			throw new RuntimeException("Illegal channel type = " + key.channel() + ".");

		return remoteAddress;
	}

	private boolean writeBuffer(
		ChannelType channelType, SelectionKey key,
		ByteBuffer buffer, InetSocketAddress dstAddress,
		boolean packetCombine, boolean ignoreOverflow, boolean disconnect
	) {
		if(
			!key.isValid() ||	// key is cancelled ?
			(	//UDPの場合には宛先アドレスの解決を確認
				channelType == ChannelType.UDP &&
				(dstAddress == null || dstAddress.isUnresolved())
			)
		) {
			return false;
		}

		boolean bufferOverflow = false;
		boolean operationSuccess = false;

		final InetSocketAddress localAddress = getLocalAddressBySelectionKey(key);
		if(localAddress == null) {return false;}

		KeyEntry<BufferEntry> keyEntry = null;

		writeBuffersLocker.lock();
		try {
			keyEntry = writeBuffers.get(key);

			if(keyEntry == null) {
				if(channelType == ChannelType.UDP) {
					keyEntry = new KeyEntry<>(
						key, HostIdentType.RemoteLocalAddressPort,
						BufferEntry.class, getBufferSizeTCP(), getBufferSizeUDP(), isDirectBuffer()
					);
				}
				else {
					keyEntry = new KeyEntry<>(
						key, HostIdentType.RemoteLocalAddressPort,
						BufferEntry.class, getBufferSizeTCP(), getBufferSizeUDP(), isDirectBuffer(),
						getRemoteAddressBySelectionKey(key), localAddress
					);
				}

				writeBuffers.put(key, keyEntry);

				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"Creating write key entry.\n" + keyEntry.toString(4)
					);
				}
			}
		}finally {writeBuffersLocker.unlock();}

		keyEntry.getLocker().lock();
		try {
			keyEntry.updateActivityTime();

			final BufferEntry bufferEntry =
				channelType == ChannelType.UDP && dstAddress != null ?
					keyEntry.getBuffer(dstAddress, localAddress) : keyEntry.getTCPBuffer();
			if(bufferEntry == null) {
				if(log.isErrorEnabled())
					log.error(logHeader + "Failed to get write buffer.");

				return false;
			}

			bufferEntry.updateActivityTime();

			if(
				disconnect &&
				(
					channelType == ChannelType.TCPClient ||
					channelType == ChannelType.TCPServerClient
				)
			) {
				//切断要求
				bufferEntry.getBufferPacketInfo().add(new PacketInfo(-1, false));

				//更新フラグを立てる
				bufferEntry.setUpdate(true);

				if(log.isTraceEnabled())
					log.trace(logHeader + "Disconnect request write = " + key + ".");
			}
			else if(buffer != null && buffer.hasRemaining()) {
				//通常書き込み
				bufferEntry.setBufferState(
					BufferState.toWRITE(bufferEntry.getBuffer(), bufferEntry.getBufferState())
				);

				//オーバーフローバイト数計算
				final int numOverflowBytes = buffer.remaining() - bufferEntry.getBuffer().remaining();
				bufferOverflow = numOverflowBytes > 0;

				// write buffer has free space?
				if(
					!bufferEntry.getBuffer().hasRemaining() ||
					(!ignoreOverflow && bufferOverflow)
				) {
					if(log.isErrorEnabled())
						log.error(logHeader + "Write buffer overflow.");

					return false;
				}

				//ソースバッファのリミット値を保存
				final int bufferLimitSnapshot = buffer.limit();

				//転送先バッファの容量が足りない場合には、ソースバッファを制限する
				if(bufferOverflow) {buffer.limit(buffer.limit() - numOverflowBytes);}

				//バッファコピー
				final int transportBytes = buffer.remaining();
				bufferEntry.getBuffer().put(buffer);

				//コピーしたバイト数を保管
				bufferEntry.getBufferPacketInfo().add(new PacketInfo(transportBytes, packetCombine));

				//更新フラグを立てる
				bufferEntry.setUpdate(true);

				//ソースバッファのリミット値を戻す
				buffer.limit(bufferLimitSnapshot);
			}
			else {
				//処理データ無し
				return true;
			}

			//書き込みをリクエスト
			try {
				operationSuccess =
					getSocketIO().interestOps(key, new OperationRequest(OperationSet.WRITE));
			}catch(CancelledKeyException ex) {
				if(log.isWarnEnabled())
					log.warn("Cancelled key.", ex);
			}

		}finally {
			keyEntry.getLocker().unlock();
		}

		return !bufferOverflow && operationSuccess;
	}

	private void cleanReadBuffer() {
		if(readBuffersCleanTimestamp.isTimeout()) {
			readBuffersLocker.lock();
			try {
			//アクセスのないバッファは捨てる
				for(
					final Iterator<Map.Entry<SelectionKey, KeyEntry<BUFT>>> it = readBuffers.entrySet().iterator();
					it.hasNext();
				) {
					final KeyEntry<BUFT> keyEntry = it.next().getValue();

					keyEntry.getLocker().lock();
					try {
						keyEntry.cleanTimeoutBuffer();

						if(keyEntry.isAccessTimeout(60, TimeUnit.SECONDS)) {
							it.remove();

							if(log.isDebugEnabled()) {
								log.debug(
									logHeader +
									"Remove inactive key entry.\n" + keyEntry.toString(4)
								);
							}
						}
					}finally {
						keyEntry.getLocker().unlock();
					}
				}
			}finally {readBuffersLocker.unlock();}

			readBuffersCleanTimestamp.setTimeoutTime(10, TimeUnit.SECONDS);
			readBuffersCleanTimestamp.updateTimestamp();
		}
	}

	private void cleanWriteBuffer() {
		if(writeBuffersCleanTimestamp.isTimeout()) {
			writeBuffersLocker.lock();
			try {
			//アクセスのないバッファは捨てる
				for(
					final Iterator<Map.Entry<SelectionKey, KeyEntry<BufferEntry>>> it = writeBuffers.entrySet().iterator();
					it.hasNext();
				) {
					KeyEntry<BufferEntry> keyEntry = it.next().getValue();

					keyEntry.getLocker().lock();
					try {
						keyEntry.cleanTimeoutBuffer();

						if(keyEntry.isAccessTimeout(60, TimeUnit.SECONDS)) {
							it.remove();
						}
					}finally {
						keyEntry.getLocker().unlock();
					}
				}
			}finally {writeBuffersLocker.unlock();}

			writeBuffersCleanTimestamp.setTimeoutTime(10, TimeUnit.SECONDS);
			writeBuffersCleanTimestamp.updateTimestamp();
		}
	}

	private static boolean writeKeyEntry(
		final SocketIOEntry<? extends SelectableChannel> channel,
		final KeyEntry<BufferEntry> entry,
		final List<ErrorEvent> errorEvents,
		final ProcessResult<Boolean> dataRemain,
		final ProcessResult<Boolean> disconnected
	) {
		boolean result = true;

		dataRemain.setResult(false);

		entry.getLocker().lock();
		try {
			if(
				entry.getProtocol() == BufferProtocol.TCP &&
				(
					entry.getTCPBuffer().getBuffer().hasRemaining() ||
					!entry.getTCPBuffer().getBufferPacketInfo().isEmpty()
				)
			) {
				entry.updateActivityTime();

				final ProcessResult<Boolean> remain = new ProcessResult<>(false);

				if(!writeKeyEntryInt(channel, entry.getTCPBuffer(), errorEvents, remain, disconnected)) {
					result = false;
				}

				if(remain.getResult()) {dataRemain.setResult(true);}
			}
			else if(
				entry.getProtocol() == BufferProtocol.UDP
			) {
				entry.updateActivityTime();

				for(final BufferEntry buffer : entry.getUDPBuffer()) {
					if(
						!buffer.getBuffer().hasRemaining() || buffer.getBufferPacketInfo().isEmpty()
					) {continue;}

					final ProcessResult<Boolean> remain = new ProcessResult<>(false);

					if(!writeKeyEntryInt(channel, buffer, errorEvents, remain, disconnected)) {
						result = false;
					}

					if(remain.getResult()) {dataRemain.setResult(true);}
				}
			}
		}finally {
			entry.getLocker().unlock();
		}

		return result;
	}

	private static boolean writeKeyEntryInt(
		final SocketIOEntry<? extends SelectableChannel> channel,
		final BufferEntry buffer,
		final List<ErrorEvent> errorEvents,
		final ProcessResult<Boolean> dataRemain,
		final ProcessResult<Boolean> disconnected
	) {
		boolean result = true;

		dataRemain.setResult(false);
		disconnected.setResult(false);

		buffer.getLocker().lock();
		try {
			buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

			boolean isDisconnectRequest = false;

			//何バイト送信するべきか計算する
			int requestBytes = 0;
			for(final PacketInfo packetInfo : buffer.getBufferPacketInfo()) {
				if(packetInfo.getPacketBytes() < 0) {
					//書き込みバイト数にマイナスが入っていれば切断
					isDisconnectRequest =
						channel.getChannelType() == ChannelType.TCPClient ||
						channel.getChannelType() == ChannelType.TCPServerClient;

					break;
				}
				else if(requestBytes == 0) {
					requestBytes = packetInfo.getPacketBytes();
					if(!packetInfo.isPacketCombine()) {break;}
				}
				else if(packetInfo.isPacketCombine())
					requestBytes += packetInfo.getPacketBytes();
				else
					break;
			}
			if(requestBytes <= 0) {
				buffer.getBufferPacketInfo().clear();
				buffer.getBuffer().clear();
				buffer.setBufferState(BufferState.INITIALIZE);
				buffer.setUpdate(false);
				buffer.updateActivityTime();

				if(isDisconnectRequest) {
					closeChannel(channel);
					disconnected.setResult(true);

					if(log.isTraceEnabled()) {
						log.trace(logHeader + "Disconnected " + channel.getKey() + ".");
					}
				}

				return true;
			}

			final int storeBufferLimit = buffer.getBuffer().limit();
			buffer.getBuffer().limit(buffer.getBuffer().position() + requestBytes);

			int sendBytes;
			//送信
			try {
				switch(channel.getChannelType()) {
				case TCPServerClient:
					assert channel instanceof SocketIOEntryTCPServerClient;
					sendBytes = ((SocketIOEntryTCPServerClient)channel).getChannel().write(buffer.getBuffer());
					break;
				case TCPClient:
					assert channel instanceof SocketIOEntryTCPClient;
					sendBytes = ((SocketIOEntryTCPClient)channel).getChannel().write(buffer.getBuffer());
					break;
				case UDP:
					assert channel instanceof SocketIOEntryUDP;
					sendBytes = ((SocketIOEntryUDP)channel).getChannel().send(buffer.getBuffer(), buffer.getRemoteAddress());
					break;
				case TCPServer:
				case Unknown:
				default:
					sendBytes = 0;
					break;
				}

			}catch(IOException ex) {
				if(log.isDebugEnabled()) {
					log.debug(logHeader + "Channel error on write.", ex);
				}

				errorEvents.add(new ErrorEvent(channel, ex));

//				sendBytes = 0;
				sendBytes = requestBytes;
				buffer.getBuffer().position(buffer.getBuffer().position() + requestBytes);

				result = false;
			}

			if(log.isTraceEnabled()) {
				int pos = buffer.getBuffer().position();
				buffer.getBuffer().position(pos - sendBytes);
				log.trace(
					logHeader +
					"Channel transmit " + sendBytes + "bytes to " + buffer.getRemoteAddress() +
					(buffer.getLocalAddress() != null ? (" via " + buffer.getLocalAddress()) : "") + ".\n" +
					FormatUtil.byteBufferToHexDump(buffer.getBuffer(), sendBytes, 4)
				);
				buffer.getBuffer().position(pos);
			}

			buffer.getBuffer().limit(storeBufferLimit);

			//送信したバイト数からパケット情報を削除する
			for(final Iterator<PacketInfo> it = buffer.getBufferPacketInfo().iterator(); it.hasNext();) {
				final PacketInfo packetInfo = it.next();

				if(packetInfo.getPacketBytes() < 0) {
					it.remove();
					break;
				}
				else if(packetInfo.getPacketBytes() < sendBytes) {
					it.remove();
					sendBytes -= packetInfo.getPacketBytes();
				}else if(packetInfo.getPacketBytes() > sendBytes){
					packetInfo.setPacketBytes(packetInfo.getPacketBytes() - sendBytes);
					sendBytes = 0;
					break;
				}else {
					it.remove();
					break;
				}
			}

			if(log.isDebugEnabled()){
				int calcTotalBytes = 0;

				for(final PacketInfo packetInfo : buffer.getBufferPacketInfo()) {
					if(packetInfo.getPacketBytes() > 0)
						calcTotalBytes += packetInfo.getPacketBytes();
				}

				if(calcTotalBytes != buffer.getBuffer().remaining()) {
					if(log.isDebugEnabled()) {
						log.error(
							logHeader +
							"Internal error! calcTotalBytes(" + calcTotalBytes + ") and buffer remaining(" +
							buffer.getBuffer().remaining() + ") mismatch."
						);
					}

					buffer.getBufferPacketInfo().clear();
					buffer.getBuffer().clear();
					buffer.setBufferState(BufferState.INITIALIZE);
				}
				assert calcTotalBytes == buffer.getBuffer().remaining();
			}

			final boolean hasRemaing =
				buffer.getBuffer().hasRemaining() ||
				!buffer.getBufferPacketInfo().isEmpty();

			//未送信のデータがあれば更新フラグを立てておく
			buffer.setUpdate(hasRemaing);

			buffer.updateActivityTime();

			dataRemain.setResult(hasRemaing);

			//切断要求があれば切断する
			if(isDisconnectRequest) {
				closeChannel(channel);
				disconnected.setResult(true);

				if(log.isTraceEnabled()) {
					log.trace(logHeader + "Disconnected " + channel.getKey() + ".");
				}
			}

		}finally {
			buffer.getLocker().unlock();
		}

		return result;
	}

	private InetSocketAddress getRemoteAddress(SocketIOEntry<? extends SelectableChannel> entry) {
		assert entry != null;

		InetSocketAddress result = null;

		if(
			entry.getChannelType() != ChannelType.UDP &&
			entry.getChannelType() != ChannelType.Unknown
		) {result = entry.getRemoteAddress();}

		return result;
	}

	private InetSocketAddress getLocalAddress(SocketIOEntry<? extends SelectableChannel> entry) {
		assert entry != null;

		InetSocketAddress result = entry.getLocalAddress();

		return result;
	}

}
