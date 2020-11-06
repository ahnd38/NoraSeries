package org.jp.illg.util.ambe.dv3k.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.ProcessResult;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.ambe.dv3k.DV3KDefines;
import org.jp.illg.util.ambe.dv3k.DV3KInterface;
import org.jp.illg.util.ambe.dv3k.DV3KPacket;
import org.jp.illg.util.ambe.dv3k.model.DV3KControllerEvent;
import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketType;
import org.jp.illg.util.ambe.dv3k.packet.channel.ChannelData;
import org.jp.illg.util.ambe.dv3k.packet.channel.DV3KChannelPacketType;
import org.jp.illg.util.ambe.dv3k.packet.control.PRODID;
import org.jp.illg.util.ambe.dv3k.packet.control.RATEP;
import org.jp.illg.util.ambe.dv3k.packet.control.VERSTRING;
import org.jp.illg.util.ambe.dv3k.packet.speech.DV3KSpeechPacketType;
import org.jp.illg.util.ambe.dv3k.packet.speech.SpeechData;
import org.jp.illg.util.event.EventListener;
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
import org.jp.illg.util.thread.task.TaskQueue;

import com.annimon.stream.Optional;
import com.annimon.stream.function.Consumer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DV3KNetworkController
extends SocketIOHandlerWithThread<BufferEntry> implements DV3KInterface{

	private static final int rwPacketsLimit = 100;

	private final String logHeader;

	private final ExecutorService workerExecutor;

	private final TaskQueue<DV3KControllerEvent, Boolean> eventQueue;
	private final EventListener<DV3KControllerEvent> eventListener;

	private Queue<DV3KPacket> writePackets;
	private Queue<DV3KPacket> readPackets;
	private final Lock rwPacketsLocker;

	private SocketIOEntryUDP channel;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String DV3KServerAddress;
	public static final String DV3KServerAddressPropertyName = "DV3KServerAddress";
	private static final String DV3KServerAddressDefault = "";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int DV3KServerPort;
	public static final String DV3KServerPortPropertyName = "DV3KServerPort";
	private static final int DV3KServerPortDefault = 2460;

	private final PRODID dv3kPRODID = new PRODID();
	private final RATEP dv3kRATEP = new RATEP();
	private final VERSTRING dv3kVERSTRING = new VERSTRING();
	private final SpeechData dv3kSpeechData = new SpeechData();
	private final ChannelData dv3kChannelData = new ChannelData();

	private InetSocketAddress destinationAddress;
	private final Timer destinationAddressUpdateTimer;


	public DV3KNetworkController(
		@NonNull final ExecutorService workerExecutor,
		final ThreadUncaughtExceptionListener exceptionListener,
		final EventListener<DV3KControllerEvent> eventListener
	) {
		this(workerExecutor, exceptionListener, eventListener, null);

	}

	public DV3KNetworkController(
		@NonNull final ExecutorService workerExecutor,
		final ThreadUncaughtExceptionListener exceptionListener,
		final EventListener<DV3KControllerEvent> eventListener,
		final SocketIO socketIO
	) {
		super(
			exceptionListener,
			DV3KNetworkController.class,
			socketIO,
			BufferEntry.class,
			HostIdentType.RemoteAddressOnly
		);

		this.workerExecutor = workerExecutor;
		this.eventListener = eventListener;

		setProcessLoopIntervalTimeMillis(100L);

		logHeader = DV3KNetworkController.class.getSimpleName() + " : ";

		rwPacketsLocker = new ReentrantLock();
		writePackets = new LinkedList<DV3KPacket>();
		readPackets = new LinkedList<DV3KPacket>();

		channel = null;

		destinationAddress = null;
		destinationAddressUpdateTimer = new Timer();

		eventQueue = new TaskQueue<>(this.workerExecutor);
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
		if(log.isErrorEnabled()) {
			log.error(
				logHeader +
				"Accepted event received...Protocol=" + protocol +
				"/LocalAddress:" + localAddress + "/RemoteAddress:" + remoteAddress
			);
		}

		return null;
	}

	@Override
	public OperationRequest connectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(log.isErrorEnabled()) {
			log.error(
				logHeader +
				"Connected event received...Protocol=" + protocol +
				"/LocalAddress:" + localAddress + "/RemoteAddress:" + remoteAddress
			);
		}

		return null;
	}


	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(log.isErrorEnabled()) {
			log.error(
				logHeader +
				"Disconnected event received...Protocol=" + protocol +
				"/LocalAddress:" + localAddress + "/RemoteAddress:" + remoteAddress
			);
		}
	}


	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress,
		Exception ex
	) {
		if(log.isErrorEnabled()) {
			log.error(
				logHeader +
				"Error event received...Protocol=" + protocol +
				"/LocalAddress:" + localAddress + "/RemoteAddress:" + remoteAddress
				, ex
			);
		}
	}

	@Override
	public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		wakeupProcessThread();
	}

	@Override
	public String getPortName() {
		return getDV3KServerAddress() + ":" + getDV3KServerPort();
	}

	@Override
	public boolean start() {
		if(isRunning()){
			if(log.isDebugEnabled())
				log.debug(logHeader + "Already running.");

			return true;
		}

		if(
			!super.start(
				new Runnable() {
					@Override
					public void run() {
						channel =
							getSocketIO().registUDP(
								new InetSocketAddress(0), DV3KNetworkController.this.getHandler(),
								DV3KNetworkController.this.getClass().getSimpleName() + "@" +
								getDV3KServerAddress() + ":" + getDV3KServerPort()
							);
					}
				}
			) ||
			channel == null
		) {
			this.stop();

			closeChannels();

			return false;
		}

		return true;
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processThread() {

		//サーバのアドレスを一定時間ごとに解決する
		if(destinationAddress == null || destinationAddressUpdateTimer.isTimeout()) {
			destinationAddressUpdateTimer.setTimeoutTime(1, TimeUnit.MINUTES);
			destinationAddressUpdateTimer.updateTimestamp();

			destinationAddress = new InetSocketAddress(getDV3KServerAddress(), getDV3KServerPort());
		}

		//受信データを解析してキューに追加
		parseDV3KPacket();

		rwPacketsLocker.lock();
		try{
			//送信キューにデータがあれば送信
			for(Iterator<DV3KPacket> it = writePackets.iterator(); it.hasNext();) {
				final DV3KPacket writePacket = it.next();
				it.remove();

				final ByteBuffer buffer = writePacket.assemblePacket();
				if(buffer == null) {
					if(log.isErrorEnabled())
						log.error(logHeader + "Packet asemble error.\n" + writePacket.toString());

					continue;
				}

				if(log.isTraceEnabled()) {
					buffer.rewind();
					log.trace(
						logHeader +
						"Transmit to " + destinationAddress + "\n" + FormatUtil.byteBufferToHexDump(buffer, 4)
					);
					buffer.rewind();
				}

				writeUDPPacket(channel.getKey(), destinationAddress, buffer);
			}

		}finally {rwPacketsLocker.unlock();}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		super.threadFinalize();

		closeChannels();
	}

	@Override
	public boolean open() {
		return start();
	}

	@Override
	public boolean isOpen() {
		return isRunning() && channel != null && channel.getChannel().isOpen();
	}

	@Override
	public void close() {
		stop();
	}

	@Override
	public boolean setProperties(Properties properties) {

		setDV3KServerAddress(
			PropertyUtils.getString(
				properties,
				DV3KServerAddressPropertyName, DV3KServerAddressDefault
			)
		);

		setDV3KServerPort(
			PropertyUtils.getInteger(
				properties,
				DV3KServerPortPropertyName, DV3KServerPortDefault
			)
		);

		return true;
	}

	@Override
	public Properties getProperties(Properties properties) {

		return properties;
	}

	@Override
	public boolean writeDV3KPacket(@NonNull DV3KPacket packet) {
		return addWriteQueue(packet);
	}

	@Override
	public boolean hasReadableDV3KPacket() {
		rwPacketsLocker.lock();
		try {
			for(Iterator<DV3KPacket> it = readPackets.iterator(); it.hasNext();) {
				final DV3KPacket packet = it.next();

				if(
					packet.getSpeechPacketType() != DV3KSpeechPacketType.SpeechData &&
					packet.getChannelPacketType() != DV3KChannelPacketType.ChannelData
				) {
					return true;
				}
			}
		}finally {rwPacketsLocker.unlock();}

		return false;
	}

	@Override
	public DV3KPacket readDV3KPacket() {
		rwPacketsLocker.lock();
		try {
			for(Iterator<DV3KPacket> it = readPackets.iterator(); it.hasNext();) {
				final DV3KPacket packet = it.next();

				if(
					packet.getSpeechPacketType() != DV3KSpeechPacketType.SpeechData &&
					packet.getChannelPacketType() != DV3KChannelPacketType.ChannelData
				) {
					it.remove();

					return packet;
				}
			}
		}finally {rwPacketsLocker.unlock();}

		return null;
	}

	@Override
	public boolean encodePCM2AMBEInput(@NonNull ShortBuffer pcmBuffer) {

		if(pcmBuffer.remaining() < DV3KDefines.DV3K_PCM_BLOCKSIZE) {
			return false;
		}

		SpeechData packet = new SpeechData();

		for(int i = 0; i < DV3KDefines.DV3K_PCM_BLOCKSIZE && pcmBuffer.hasRemaining(); i++) {
			packet.getSpeechData().add((int)pcmBuffer.get());
		}

		return addWriteQueue(packet);
	}

	@Override
	public boolean encodePCM2AMBEOutput(@NonNull ByteBuffer ambeBuffer) {

		boolean available = false;

		rwPacketsLocker.lock();
		try {
			for(Iterator<DV3KPacket> it = readPackets.iterator(); it.hasNext();) {
				final DV3KPacket packet = it.next();

				if(
					packet.getPacketType() == DV3KPacketType.ChannelPacket &&
					packet.getChannelPacketType() == DV3KChannelPacketType.ChannelData
				) {
					final ChannelData channelData = (ChannelData)packet;

					while(!channelData.getChannelData().isEmpty() && ambeBuffer.hasRemaining()) {
						ambeBuffer.put(channelData.getChannelData().poll());
					}
					ambeBuffer.flip();

					it.remove();

					available = true;

					break;
				}
			}
		}finally {
			rwPacketsLocker.unlock();
		}

		return available;
	}

	@Override
	public boolean decodeAMBE2PCMInput(@NonNull ByteBuffer ambeBuffer) {

		boolean available = false;

		while(ambeBuffer.remaining() >= DV3KDefines.DV3K_AMBE_BLOCKSIZE) {
			ChannelData packet = new ChannelData();

			for(int i = 0; i < DV3KDefines.DV3K_AMBE_BLOCKSIZE && ambeBuffer.hasRemaining(); i++) {
				packet.getChannelData().add(ambeBuffer.get());
			}

			if(addWriteQueue(packet))
				available = true;
			else
				return false;
		}

		return available;
	}

	@Override
	public boolean decodeAMBE2PCMOutput(@NonNull ShortBuffer pcmBuffer) {

		boolean available = false;

		rwPacketsLocker.lock();
		try {
			for(Iterator<DV3KPacket> it = readPackets.iterator(); it.hasNext();) {
				final DV3KPacket packet = it.next();

				if(
					packet.getPacketType() == DV3KPacketType.SpeechPacket &&
					packet.getSpeechPacketType() == DV3KSpeechPacketType.SpeechData
				) {
					final SpeechData speechData = (SpeechData)packet;

					while(!speechData.getSpeechData().isEmpty() && pcmBuffer.hasRemaining()) {
						final int sample = speechData.getSpeechData().poll();
						pcmBuffer.put((short)sample);
					}
					pcmBuffer.flip();

					it.remove();

					available = true;

					break;
				}
			}
		}finally {
			rwPacketsLocker.unlock();
		}

		return available;
	}

	private boolean addWriteQueue(@NonNull final DV3KPacket packet) {
		boolean isSuccess = false;

		rwPacketsLocker.lock();
		try {
			while(writePackets.size() >= rwPacketsLimit) {writePackets.poll();}

			isSuccess = writePackets.add(packet);
		}finally {
			rwPacketsLocker.unlock();
		}

		if(isSuccess) {wakeupProcessThread();}

		return isSuccess;
	}

	private boolean parseDV3KPacket() {

		final ProcessResult<Boolean> update = new ProcessResult<>(false);

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			opEntry.ifPresent(new Consumer<BufferEntry>() {
				@Override
				public void accept(BufferEntry buffer) {

					buffer.getLocker().lock();
					try {
						if(!buffer.isUpdate()) {return;}

						buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

						for (Iterator<PacketInfo> itBufferBytes = buffer.getBufferPacketInfo().iterator(); itBufferBytes.hasNext(); ) {
							final PacketInfo packetInfo = itBufferBytes.next();
							final int bufferLength = packetInfo.getPacketBytes();
							itBufferBytes.remove();

							if (bufferLength <= 0) {
								continue;
							}

							ByteBuffer receivePacket = ByteBuffer.allocate(bufferLength);
							for (int i = 0; i < bufferLength; i++) {
								receivePacket.put(buffer.getBuffer().get());
							}
							BufferState.toREAD(receivePacket, BufferState.WRITE);

							if(log.isTraceEnabled()) {
								StringBuilder sb = new StringBuilder(logHeader);
								sb.append(bufferLength);
								sb.append(" bytes received.\n");
								sb.append("    ");
								sb.append("[RemoteHost]:");
								sb.append(buffer.getRemoteAddress());
								sb.append("/");
								sb.append("[LocalHost]:");
								sb.append(buffer.getLocalAddress());
								sb.append("\n");
								sb.append(FormatUtil.byteBufferToHexDump(receivePacket, 4));
								log.trace(sb.toString());

								receivePacket.rewind();
							}

							boolean match = false;

							final int localPort = buffer.getLocalAddress().getPort();
							if(localPort == channel.getLocalAddress().getPort()) {
								match = parseDV3KPacket(buffer, receivePacket);
							}

							if(match) {update.setResult(true);}
						}

						buffer.setUpdate(false);

					}finally{
						buffer.getLocker().unlock();
					}
				}
			});
		}

		return update.getResult();
	}

	private boolean parseDV3KPacket(
		BufferEntry buffer, ByteBuffer receivePacket
	) {

		assert buffer != null && receivePacket != null;

		boolean match = false;
		DV3KPacket parsedCommand = null;
		do {
			if (
				(parsedCommand = dv3kPRODID.parsePacket(receivePacket)) != null ||
				(parsedCommand = dv3kVERSTRING.parsePacket(receivePacket)) != null ||
				(parsedCommand = dv3kRATEP.parsePacket(receivePacket)) != null ||
				(parsedCommand = dv3kSpeechData.parsePacket(receivePacket)) != null ||
				(parsedCommand = dv3kChannelData.parsePacket(receivePacket)) != null
			) {
				parsedCommand.setRemoteAddress(buffer.getRemoteAddress());

				final DV3KPacket copyPacket = parsedCommand.clone();

				rwPacketsLocker.lock();
				try {
					while(readPackets.size() >= rwPacketsLimit) {readPackets.poll();}
					readPackets.add(copyPacket);
				}finally {
					rwPacketsLocker.unlock();
				}

				if(log.isTraceEnabled())
					log.trace(logHeader + "Receive DV3K packet.\n    " + parsedCommand.toString());

				if(eventListener != null) {
					eventQueue.addEventQueue(new Consumer<DV3KControllerEvent>() {
						@Override
						public void accept(DV3KControllerEvent eventType) {
							if(eventType == DV3KControllerEvent.ReceivePacket)
								eventListener.event(DV3KControllerEvent.ReceivePacket, copyPacket);
						}
					}, DV3KControllerEvent.ReceivePacket, getExceptionListener());
				}

				match = true;
			} else {
				match = false;
			}
		} while (match);

		return match;
	}

	private void closeChannels() {
		closeChannel(channel);
	}

	private void closeChannel(SocketIOEntryUDP channel) {
		if(channel != null && channel.getChannel().isOpen()) {
			try {
				channel.getChannel().close();
			}catch(IOException ex) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Error occurred channel close.", ex);
			}
		}
	}
}
