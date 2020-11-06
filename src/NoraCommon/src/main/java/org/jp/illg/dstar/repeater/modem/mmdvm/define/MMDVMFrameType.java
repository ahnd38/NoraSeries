package org.jp.illg.dstar.repeater.modem.mmdvm.define;

import lombok.Getter;

public enum MMDVMFrameType {
	Unknown(      (byte) 0xFF),

//	FRAME_START(  (byte) 0xE0),

	GET_VERSION(  (byte) 0x00),
	GET_STATUS(   (byte) 0x01),
	SET_CONFIG(   (byte) 0x02),
	SET_MODE(     (byte) 0x03),
	SET_FREQ(     (byte) 0x04),

	SEND_CWID(    (byte) 0x0A),

	DSTAR_HEADER( (byte) 0x10),
	DSTAR_DATA(   (byte) 0x11),
	DSTAR_LOST(   (byte) 0x12),
	DSTAR_EOT(    (byte) 0x13),

	DMR_DATA1(    (byte) 0x18),
	DMR_LOST1(    (byte) 0x19),
	DMR_DATA2(    (byte) 0x1A),
	DMR_LOST2(    (byte) 0x1B),
	DMR_SHORTLC(  (byte) 0x1C),
	DMR_START(    (byte) 0x1D),
	DMR_ABORT(    (byte) 0x1E),

	YSF_DATA(     (byte) 0x20),
	YSF_LOST(     (byte) 0x21),

	P25_HDR(      (byte) 0x30),
	P25_LDU(      (byte) 0x31),
	P25_LOST(     (byte) 0x32),

	NXDN_DATA(    (byte) 0x40),
	NXDN_LOST(    (byte) 0x41),

	POCSAG_DATA(  (byte) 0x50),

	ACK(          (byte) 0x70),
	NAK(          (byte) 0x7F),

	SERIAL(       (byte) 0x80),

	TRANSPARENT(  (byte) 0x90),

	DEBUG1(       (byte) 0xF1),
	DEBUG2(       (byte) 0xF2),
	DEBUG3(       (byte) 0xF3),
	DEBUG4(       (byte) 0xF4),
	DEBUG5(       (byte) 0xF5),
	;

	@Getter
	private final byte typeCode;

	private MMDVMFrameType(final byte typeCode) {
		this.typeCode = typeCode;
	}

	public static MMDVMFrameType getTypeByTypeCode(final byte typeCode) {
		for(MMDVMFrameType t : values()) {
			if(t.getTypeCode() == typeCode) {return t;}
		}

		return Unknown;
	}
}
