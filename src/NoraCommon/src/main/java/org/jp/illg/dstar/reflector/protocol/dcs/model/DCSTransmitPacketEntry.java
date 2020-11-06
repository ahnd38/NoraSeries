package org.jp.illg.dstar.reflector.protocol.dcs.model;

import java.net.InetAddress;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.util.socketio.SocketIOEntryUDP;

import lombok.Getter;
import lombok.Setter;

public class DCSTransmitPacketEntry implements TransmitterPacket {


	@Setter
	private PacketType packetType;

	@Setter
	private DSTARPacket packet;

	@Setter
	@Getter
	private int longSequence;

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


	private DCSTransmitPacketEntry() {
		super();
	}

	public DCSTransmitPacketEntry(
		final PacketType packetType,
		final DSTARPacket packet, final int longSequence,
		final SocketIOEntryUDP channel,
		final InetAddress destinationAddress, final int destinationPort,
		final FrameSequenceType frameSequenceType
	) {
		this();

		setPacketType(packetType);
		setPacket(packet);
		setLongSequence(longSequence);
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
	public PacketType getPacketType() {
		return packetType;
	}

	@Override
	public DCSTransmitPacketEntry clone() {
		DCSTransmitPacketEntry copy = null;
		try {
			copy = (DCSTransmitPacketEntry)super.clone();

			copy.packetType = packetType;

			if(packet != null) {copy.packet = packet.clone();}
			copy.longSequence = longSequence;
			copy.channel = channel;
			copy.destinationAddress = destinationAddress;
			copy.destinationPort = destinationPort;

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}

}
