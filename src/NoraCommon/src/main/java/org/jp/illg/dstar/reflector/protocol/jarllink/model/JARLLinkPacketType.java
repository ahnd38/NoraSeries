package org.jp.illg.dstar.reflector.protocol.jarllink.model;

import lombok.Getter;

public enum JARLLinkPacketType {
	Dummy                     ((byte)0x00),
	Reserved01                ((byte)0x01),
	Reserved10                ((byte)0x10),
	DDPacket                  ((byte)0x11),
	DVPacket                  ((byte)0x12),
	Reserved20                ((byte)0x20),
	UpdateTerminalLocation    ((byte)0x21),
	;

	@Getter
	private final byte value;

	private JARLLinkPacketType(final byte value) {
		this.value = value;
	}

	public static byte getMask() {
		return (byte)0x33;
	}

	public static JARLLinkPacketType getTypeByValue(final byte value) {
		for(final JARLLinkPacketType t : values()) {
			if(t.getValue() == (value & getMask())) {return t;}
		}

		return null;
	}
}
