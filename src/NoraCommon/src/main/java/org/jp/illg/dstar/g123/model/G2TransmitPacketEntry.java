package org.jp.illg.dstar.g123.model;

import java.net.InetAddress;

import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacketImpl;

import lombok.Getter;
import lombok.Setter;

public class G2TransmitPacketEntry extends TransmitterPacketImpl implements TransmitterPacket {

	@Setter
	@Getter
	private InetAddress destinationAddress;

	@Setter
	@Getter
	private int destinationPort;

	public G2TransmitPacketEntry(
		final PacketType packetType,
		final G2Packet packet,
		final InetAddress destinationAddress,
		final int destinationPort,
		final FrameSequenceType frameSequenceType
	) {
		super(packetType, packet, frameSequenceType);

		setDestinationAddress(destinationAddress);
		setDestinationPort(destinationPort);
	}

	public G2Packet getG2Packet() {
		return (G2Packet)getPacket();
	}

	@Override
	public G2TransmitPacketEntry clone() {
		final G2TransmitPacketEntry cloneInstance =
			(G2TransmitPacketEntry)super.clone();

		cloneInstance.destinationAddress = destinationAddress;
		cloneInstance.destinationPort = destinationPort;

		return cloneInstance;
	}
}
