package org.jp.illg.dstar.repeater.homeblew.protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.repeater.homeblew.model.HRPPacket;
import org.jp.illg.dstar.repeater.homeblew.model.HRPPacketImpl;
import org.jp.illg.dstar.repeater.homeblew.model.HRPPollData;
import org.jp.illg.dstar.repeater.homeblew.model.HRPRegisterData;
import org.jp.illg.dstar.repeater.homeblew.model.HRPStatusData;
import org.jp.illg.dstar.repeater.homeblew.model.HRPTextData;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
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

import com.annimon.stream.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HomebrewRepeaterProtocolProcessor
extends SocketIOHandlerWithThread<BufferEntry> {

	private String logHeader;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int localPort;

	private SocketIOEntryUDP hbChannel;

	private final DSTARRepeater repeater;


	private final Map<Integer, HomebrewRepeaterProtocolFrameEntry> frameEntries;

	private final Queue<HRPPacket> fromGatewayPackets;
	private final Queue<HRPPacket> toGatewayPackets;
	private final Queue<HRPPacket> fromRepeaterPackets;


	public HomebrewRepeaterProtocolProcessor(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARRepeater repeater,
		int localPort
	) {
		this(exceptionListener, repeater, localPort, null);

		setProcessLoopIntervalTimeMillis(1000L);
	}

	public HomebrewRepeaterProtocolProcessor(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARRepeater repeater,
		final int localPort,
		SocketIO socketIO
	) {
		super(
			exceptionListener,
			HomebrewRepeaterProtocolProcessor.class,
			socketIO, BufferEntry.class, HostIdentType.RemoteAddressOnly
		);

		logHeader =
			HomebrewRepeaterProtocolProcessor.class.getSimpleName() +
			"(LocalPort:" + String.valueOf(localPort) + ") : ";

		this.repeater = repeater;

		frameEntries = new HashMap<>();

		fromGatewayPackets = new LinkedList<>();
		toGatewayPackets = new LinkedList<>();
		fromRepeaterPackets = new LinkedList<>();

		setLocalPort(localPort);
	}

	@Override
	public boolean start() {
		if(isRunning()){
			if(log.isDebugEnabled())
				log.debug(logHeader + "Already running.");

			return true;
		}

		if(getLocalPort() < 1024) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not set localPort = " + getLocalPort() + ".");

			return false;
		}

		if(
			!super.start(
				new Runnable() {
					@Override
					public void run() {
						hbChannel =
							getSocketIO().registUDP(
								new InetSocketAddress(getLocalPort()),
								HomebrewRepeaterProtocolProcessor.this.getHandler(),
								HomebrewRepeaterProtocolProcessor.this.getClass().getSimpleName() + "@" + getLocalPort()
							);
					}
				}
			) ||
			hbChannel == null
		){
			this.stop();

			closeHbChannel();

			return false;
		}

		return true;
	}

	public void stop() {
		super.stop();

		closeHbChannel();
	}

	@Override
	public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		super.wakeupProcessThread();
	}

	@Override
	public OperationRequest readEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest acceptedEvent(
			SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress,
			InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest connectedEvent(
			SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress,
			InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public void disconnectedEvent(
			SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress,
			InetSocketAddress remoteAddress
	) {
		return;
	}

	@Override
	public void errorEvent(
			SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress,
			InetSocketAddress remoteAddress, Exception ex
	) {
		return;
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		super.threadFinalize();

		closeHbChannel();

		frameEntries.clear();

		fromGatewayPackets.clear();
		toGatewayPackets.clear();
		fromRepeaterPackets.clear();
	}

	@Override
	protected ThreadProcessResult processThread() {

		fromRepeaterPackets.clear();
		parsePacket(fromRepeaterPackets);

		for(Iterator<HRPPacket> it = fromRepeaterPackets.iterator(); it.hasNext();) {
			HRPPacket packet = it.next();
			it.remove();

			if(log.isTraceEnabled())
				log.trace(logHeader + "Homebrew repeater packet received\n" + packet.toString());

			switch(packet.getHrpPacketType()) {
				case Text:
				case TempText:
				case Status:
				case Poll:
				case Register:
					synchronized(toGatewayPackets) {
						toGatewayPackets.add(packet);
					}
					repeater.wakeupRepeaterWorker();
					break;

				case Header:
				case BusyHeader:
					onReceiveHeader(packet);
					break;

				case AMBE:
				case BusyAMBE:
					onReceiveAMBE(packet);
					break;

				default:
					break;
			}
		}
		for(Iterator<HomebrewRepeaterProtocolFrameEntry> it = frameEntries.values().iterator(); it.hasNext();) {
			HomebrewRepeaterProtocolFrameEntry entry = it.next();
			if(entry.isTimeoutActivityTimestamp(TimeUnit.SECONDS.toMillis(30))) {it.remove();}
		}


		synchronized(fromGatewayPackets) {
			for(Iterator<HRPPacket> it = fromGatewayPackets.iterator(); it.hasNext();) {
				HRPPacket packet = it.next();
				it.remove();

				sendPacket(packet, packet.getRemoteAddress(), packet.getRemotePort());
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	public boolean writeHeader(
		@NonNull final InetAddress destinationAddress, final int destinationPort,
		@NonNull final DSTARPacket packet
	) {
		return writePacket(PacketType.Header, destinationAddress, destinationPort, packet, false);
	}

	public boolean writeHeaderBusy(
		@NonNull final InetAddress destinationAddress, final int destinationPort,
		@NonNull final DSTARPacket packet
	) {
		return writePacket(PacketType.Header, destinationAddress, destinationPort, packet, true);
	}

	public boolean writeAMBE(
		@NonNull final InetAddress destinationAddress, final int destinationPort,
		@NonNull final DSTARPacket packet
	) {
		return writePacket(PacketType.Voice, destinationAddress, destinationPort, packet, false);
	}

	public boolean writeAMBEBusy(
		@NonNull final InetAddress destinationAddress, final int destinationPort,
		@NonNull final DSTARPacket packet
	) {
		return writePacket(PacketType.Voice, destinationAddress, destinationPort, packet, true);
	}

	public boolean writeText(
		@NonNull final InetAddress destinationAddress, final int destinationPort,
		@NonNull final HRPTextData text
	) {
		if(
			text == null ||
			destinationAddress == null || destinationPort < 0
		) {return false;}

		HRPPacket hrpPacket = new HRPPacketImpl(text);
		hrpPacket.setRemoteAddress(destinationAddress);
		hrpPacket.setRemotePort(destinationPort);

		synchronized(fromGatewayPackets) {
			return fromGatewayPackets.add(hrpPacket);
		}
	}

	public boolean writeStatus(
		@NonNull final InetAddress destinationAddress, final int destinationPort,
		@NonNull final HRPStatusData status
	) {
		if(
			status == null ||
			destinationAddress == null || destinationPort < 0
		) {return false;}

		HRPPacket hrpPacket = new HRPPacketImpl(status);
		hrpPacket.setRemoteAddress(destinationAddress);
		hrpPacket.setRemotePort(destinationPort);

		synchronized(fromGatewayPackets) {
			return fromGatewayPackets.add(hrpPacket);
		}
	}

	public boolean writePoll(InetAddress destinationAddress, int destinationPort, HRPPollData poll) {
		if(
			poll == null ||
			destinationAddress == null || destinationPort < 0
		) {return false;}

		HRPPacket hrpPacket = new HRPPacketImpl(poll);
		hrpPacket.setRemoteAddress(destinationAddress);
		hrpPacket.setRemotePort(destinationPort);

		synchronized(fromGatewayPackets) {
			return fromGatewayPackets.add(hrpPacket);
		}
	}

	public boolean writeRegister(InetAddress destinationAddress, int destinationPort, HRPRegisterData register) {
		if(
			register == null ||
			destinationAddress == null || destinationPort < 0
		) {return false;}

		HRPPacket hrpPacket = new HRPPacketImpl(register);
		hrpPacket.setRemoteAddress(destinationAddress);
		hrpPacket.setRemotePort(destinationPort);

		synchronized(fromGatewayPackets) {
			return fromGatewayPackets.add(hrpPacket);
		}
	}


	private boolean parsePacket(Queue<HRPPacket> receivePackets) {
		assert receivePackets != null;

		boolean update = false;

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			final BufferEntry buffer = opEntry.get();

			buffer.getLocker().lock();
			try {
				if(!buffer.isUpdate()) {continue;}

				buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

				for (
					final Iterator<PacketInfo> itBufferBytes = buffer.getBufferPacketInfo().iterator();
					itBufferBytes.hasNext();
				) {
					final PacketInfo packetInfo = itBufferBytes.next();
					int bufferLength = packetInfo.getPacketBytes();
					itBufferBytes.remove();

					if(bufferLength <= 0) {continue;}

					ByteBuffer receivePacket = ByteBuffer.allocate(bufferLength);
					for(int i = 0; i < bufferLength; i++) {receivePacket.put(buffer.getBuffer().get());}
					BufferState.toREAD(receivePacket, BufferState.WRITE);

					if(log.isTraceEnabled()) {
						log.trace(logHeader + "Receive data from repeater.\n    " + FormatUtil.byteBufferToHex(receivePacket));

						receivePacket.rewind();
					}

					boolean match = false;
					HRPPacket validPacket = null;
					do {
						if(
							(validPacket = HRPPacketTool.isValidText(receivePacket)) != null ||
							(validPacket = HRPPacketTool.isValidStatus(receivePacket)) != null ||
							(validPacket = HRPPacketTool.isValidPoll(receivePacket)) != null ||
							(validPacket = HRPPacketTool.isValidRegister(receivePacket)) != null ||
							(validPacket = HRPPacketTool.isValidHeader(receivePacket)) != null ||
							(validPacket = HRPPacketTool.isValidAMBE(receivePacket)) != null
						) {
							validPacket.setRemoteAddress(buffer.getRemoteAddress().getAddress());
							validPacket.setRemotePort(buffer.getRemoteAddress().getPort());
							receivePackets.add(validPacket.clone());
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

	public HRPPacket readPacket() {
		synchronized(toGatewayPackets) {
			return toGatewayPackets.poll();
		}
	}

	private void onReceiveHeader(HRPPacket packet) {
		final int frameID = packet.getDVPacket().getBackBone().getFrameIDNumber();

		HomebrewRepeaterProtocolFrameEntry entry = frameEntries.get(frameID);

		if(entry != null) {
			entry.updateActivityTimestamp();

			return;
		}
		else {
			entry = new HomebrewRepeaterProtocolFrameEntry(frameID, packet);
			frameEntries.put(frameID, entry);
		}

		synchronized(toGatewayPackets) {
			toGatewayPackets.add(packet);
		}

		return;
	}

	private void onReceiveAMBE(HRPPacket packet) {
		final int frameID = packet.getDVPacket().getBackBone().getFrameIDNumber();

		final HomebrewRepeaterProtocolFrameEntry entry = frameEntries.get(frameID);

		if(entry == null) {return;}

		entry.updateActivityTimestamp();

		synchronized(toGatewayPackets) {
			toGatewayPackets.add(packet);
		}

		return;
	}

	private boolean writePacket(
		final PacketType packetType,
		@NonNull final InetAddress destinationAddress, final int destinationPort,
		@NonNull final DSTARPacket packet,
		final boolean isBusy
	) {
		if(
			packet.getPacketType() != DSTARPacketType.DV ||
			(
				(
					packetType == PacketType.Header &&
					!packet.getDVPacket().hasPacketType(PacketType.Header)
				) ||
				(
					packetType == PacketType.Voice &&
					!packet.getDVPacket().hasPacketType(PacketType.Voice)
				)
			) ||
			destinationAddress == null || destinationPort < 0
		) {return false;}

		final HRPPacket hrpPacket =
			new HRPPacketImpl(packetType, packet.getDVPacket(), isBusy, destinationAddress, destinationPort);

		boolean success = false;
		synchronized(fromGatewayPackets) {
			success = fromGatewayPackets.add(hrpPacket);
		}

		if(success) {wakeupProcessThread();}

		return success;
	}

	private boolean sendPacket(HRPPacket packet, InetAddress destinationAddress, int destinationPort) {
		assert packet != null && destinationAddress != null && destinationPort > 0;

		byte[] buffer = null;
		int txPackets = 0;

		switch(packet.getHrpPacketType()) {
		case Text:
		case TempText:
			buffer = HRPPacketTool.assembleText(packet);
			txPackets = 1;
			break;

		case Status:
			buffer = HRPPacketTool.assembleStatus(packet);
			txPackets = 1;
			break;

		case Poll:
			buffer = HRPPacketTool.assemblePoll(packet);
			txPackets = 1;
			break;

		case Register:
			buffer = HRPPacketTool.assembleRegister(packet);
			txPackets = 1;
			break;

		case Header:
			buffer = HRPPacketTool.assembleHeader(packet);
			txPackets = 2;
			break;

		case BusyHeader:
			buffer = HRPPacketTool.assembleHeader(packet);
			txPackets = 1;
			break;

		case AMBE:
		case BusyAMBE:
			//範囲外のシーケンス番号を送信するとdstarrepeaterd+DVAPがハングアップするようなので、
			//クリップさせる
			if(packet.getBackBone().getSequenceNumber() > DSTARDefines.MaxSequenceNumber)
				packet.getBackBone().setSequenceNumber(DSTARDefines.MaxSequenceNumber);
			else if(packet.getBackBone().getSequenceNumber() < DSTARDefines.MinSequenceNumber)
				packet.getBackBone().setSequenceNumber(DSTARDefines.MinSequenceNumber);

			buffer = HRPPacketTool.assembleAMBE(packet);
			txPackets = 1;
			break;

		default:
			return false;
		}

		if(buffer == null) {return false;}

		if(log.isTraceEnabled())
			log.trace(logHeader + "Homebrew repeater packet send.\n" + packet.toString());

		boolean result = true;
		for(int cnt = 0; cnt < txPackets; cnt++) {
			if(
				!super.writeUDPPacket(
					this.hbChannel.getKey(),
					packet.getRemoteAddress() != null ?
							new InetSocketAddress(packet.getRemoteAddress(), packet.getRemotePort()):
							new InetSocketAddress(destinationAddress, destinationPort),
					ByteBuffer.wrap(buffer)
				)
			) {result = false;}
		}

		return result;
	}

	private void closeHbChannel(){
		if(this.hbChannel != null && this.hbChannel.getChannel().isOpen()) {
			try {
				this.hbChannel.getChannel().close();
				this.hbChannel = null;
			}catch(IOException ex) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Error occurred at channel close.", ex);
			}
		}
	}
}
