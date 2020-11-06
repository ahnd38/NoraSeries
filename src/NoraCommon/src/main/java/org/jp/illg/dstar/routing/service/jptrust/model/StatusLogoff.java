package org.jp.illg.dstar.routing.service.jptrust.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;

import lombok.Getter;

public class StatusLogoff extends StatusBase {

	@Getter
	private char[] callsign;

	public StatusLogoff() {
		super();

		callsign =
			Arrays.copyOf(DSTARDefines.EmptyLongCallsign.toCharArray(), DSTARDefines.CallsignFullLength);
	}

	@Override
	public StatusLogoff clone() {
		final StatusLogoff copy = (StatusLogoff)super.clone();

		copy.callsign = Arrays.copyOf(callsign, callsign.length);

		return copy;
	}

	@Override
	public StatusType getStatusType() {
		return StatusType.Logoff;
	}

	@Override
	protected boolean assemblePacketData(ByteBuffer buffer) {
		if(buffer == null || buffer.remaining() < getPacketDataSize())
			return false;

		putCallsignLong(buffer, callsign);
		for(int i = 0; i < 56; i++) {
			buffer.put((byte)0x00);
		}

		return true;
	}

	@Override
	protected int getPacketDataSize() {
		return 8 + 56;
	}

}
