package org.jp.illg.util.ambe.dv3k.packet.channel;


import lombok.Getter;

public enum DV3KChannelPacketType {
	PacketChannel0    ((byte)0x40, 2, 2),
	ChannelData       ((byte)0x01, -1 ,-1),
	ChannelData4      ((byte)0x17, -1, -1),
	Samples           ((byte)0x30, 2, 2),
	CMode             ((byte)0x02, 3, 3),
	Tone              ((byte)0x08, 3, 3),
	;

	@Getter
	private final byte value;

	@Getter
	private final int dataLengthRequest;

	@Getter
	private final int dataLengthResponse;

	private DV3KChannelPacketType(
		final byte value, final int dataLengthRequest, final int dataLengthResponse
	) {
		this.value = value;
		this.dataLengthRequest = dataLengthRequest;
		this.dataLengthResponse = dataLengthResponse;
	}

	public static DV3KChannelPacketType getTypeByValue(final byte value) {
		for(DV3KChannelPacketType type : values()) {
			if(type.getValue() == value) {return type;}
		}

		return null;
	}
}
