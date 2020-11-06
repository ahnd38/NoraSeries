package org.jp.illg.dstar.reflector.protocol.dplus.model;

import java.net.InetAddress;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.util.socketio.SocketIOEntryUDP;

import lombok.Getter;
import lombok.Setter;

public class DPlusTransmitPacketEntry implements TransmitterPacket {

	@Setter
	@Getter
	private PacketType packetType;

	@Setter
	private DSTARPacket packet;

	@Getter
	@Setter
	private SocketIOEntryUDP channel;

	@Getter
	@Setter
	private InetAddress destinationAddress;

	@Getter
	@Setter
	private int destinationPort;

	@Getter
	@Setter
	private boolean oneShot;

	@Getter
	@Setter
	private FrameSequenceType frameSequenceType;


	private DPlusTransmitPacketEntry() {
		super();
	}

	public DPlusTransmitPacketEntry(
		final PacketType packetType,
		final DSTARPacket packet,
		final SocketIOEntryUDP channel, final InetAddress destinationAddress, final int destinationPort,
		final boolean oneShot, final FrameSequenceType frameSequenceType
	) {
		this();

		setPacketType(packetType);
		setPacket(packet);
		setChannel(channel);
		setDestinationAddress(destinationAddress);
		setDestinationPort(destinationPort);
		setOneShot(oneShot);
		setFrameSequenceType(frameSequenceType);
	}

	@Override
	public DSTARPacket getPacket() {
		return packet;
	}

	@Override
	public DPlusTransmitPacketEntry clone() {
		DPlusTransmitPacketEntry copy = null;
		try {
			copy = (DPlusTransmitPacketEntry)super.clone();

			copy.packetType = packetType;

			if(packet != null)
				copy.packet = packet.clone();

			copy.channel = channel;
			copy.destinationAddress = destinationAddress;
			copy.destinationPort = destinationPort;
			copy.oneShot = oneShot;
			copy.frameSequenceType = frameSequenceType;

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}
}

