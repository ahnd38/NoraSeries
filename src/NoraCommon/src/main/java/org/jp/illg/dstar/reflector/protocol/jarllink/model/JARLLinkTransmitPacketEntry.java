package org.jp.illg.dstar.reflector.protocol.jarllink.model;

import java.net.InetAddress;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.util.socketio.SocketIOEntryUDP;

import lombok.Getter;
import lombok.Setter;

public class JARLLinkTransmitPacketEntry implements TransmitterPacket {


	@Getter
	@Setter
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
	private FrameSequenceType frameSequenceType;


	private JARLLinkTransmitPacketEntry() {
		super();
	}

	public JARLLinkTransmitPacketEntry(
		final PacketType packetType,
		final DSTARPacket packet, final SocketIOEntryUDP channel,
		final InetAddress destinationAddress, final int destinationPort,
		final FrameSequenceType frameSequenceType
	) {
		this();

		setPacketType(packetType);
		setPacket(packet);
		setChannel(channel);
		setDestinationAddress(destinationAddress);
		setDestinationPort(destinationPort);
		setFrameSequenceType(frameSequenceType);
	}

	@Override
	public DSTARPacket getPacket() {
		return packet;
	}

	@Override
	public JARLLinkTransmitPacketEntry clone() {
		JARLLinkTransmitPacketEntry copy = null;
		try {
			copy = (JARLLinkTransmitPacketEntry)super.clone();

			copy.packetType = packetType;
			copy.packet = packet;
			copy.channel = channel;
			copy.destinationAddress = destinationAddress;
			copy.destinationPort = destinationPort;
			copy.frameSequenceType = frameSequenceType;

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}
}