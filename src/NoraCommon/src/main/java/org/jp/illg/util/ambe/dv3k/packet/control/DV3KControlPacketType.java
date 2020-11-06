package org.jp.illg.util.ambe.dv3k.packet.control;

import lombok.Getter;

public enum DV3KControlPacketType {

	CHANNEL0       (0x40, 0, 0),
	ECMODE         (0x05, 2, 0),
	DCMODE         (0x06, 2, 0),
	COMPAND        (0x32, 1, 0),
	RATET          (0x09, 1, 0),
	RATEP          (0x0A, 12, 0),
	INIT           (0x0B, 1, 0),
	LOWPOWER       (0x10, 1, 0),
	CODECCFG       (0x38, -1, 0),
	CODECSTART     (0x2A, 1, 0),
	CODECSTOP      (0x2B, 0, 0),
	CHANFMT        (0x15, 2, 0),
	SPCHFMT        (0x16, 2, 0),
	PRODID         (0x30, 0, -1),
	VERSTRING      (0x31, 0, 48),
	READY          (0x39, 0, 0),
	HALT           (0x35, 0, 0),
	RESET          (0x33, 0, 0),
	RESETSOFTCFG   (0x34, 6, 0),
	GETCFG         (0x36, 0, 3),
	READCFG        (0x37, 0, 3),
	PARITYMODE     (0x3F, 1, 0),
	WRITEI2C       (0x44, -1 ,0),
	CLRCODECRESET  (0x46, 0, 0),
	SETCODECRESET  (0x47, 0, 0),
	DISCARDCODEC   (0x48, 2, 0),
	DELAYNUS       (0x49, 2, 0),
	DELAYNNS       (0x4A, 2, 0),
	RTSTHRESH      (0x4E, 5, 0),
	GAIN           (0x4B, 2, 0),
	;

	@Getter
	private final int value;

	@Getter
	private final int dataLengthRequest;

	@Getter
	private final int dataLengthResponse;

	private DV3KControlPacketType(
		final int value, final int dataLengthRequest, final int dataLengthResponse
	) {
		this.value = value;

		this.dataLengthRequest = dataLengthRequest;
		this.dataLengthResponse = dataLengthResponse;
	}

	public static final DV3KControlPacketType getTypeByValue(final int value) {
		for(DV3KControlPacketType v : values()) {
			if(v.getValue() == value) {return v;}
		}

		return null;
	}
}
