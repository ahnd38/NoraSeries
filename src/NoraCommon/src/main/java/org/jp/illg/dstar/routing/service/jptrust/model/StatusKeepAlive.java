package org.jp.illg.dstar.routing.service.jptrust.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;

import lombok.Getter;

public class StatusKeepAlive extends StatusBase {

	@Getter
	private char[] moduleName;

	@Getter
	private char[] callsign;

	@Getter
	private char[] version;

	public StatusKeepAlive() {
		super();

		moduleName =
			Arrays.copyOf(DSTARDefines.EmptyLongCallsign.toCharArray(), DSTARDefines.CallsignFullLength);
		callsign =
			Arrays.copyOf(DSTARDefines.EmptyLongCallsign.toCharArray(), DSTARDefines.CallsignFullLength);
		version = new char[48];
		Arrays.fill(version, ' ');
	}

	@Override
	public StatusKeepAlive clone() {
		final StatusKeepAlive copy = (StatusKeepAlive)super.clone();

		copy.moduleName = Arrays.copyOf(moduleName, moduleName.length);
		copy.callsign = Arrays.copyOf(callsign, callsign.length);
		copy.version = Arrays.copyOf(version, version.length);

		return copy;
	}

	@Override
	public StatusType getStatusType() {
		return StatusType.KeepAlive;
	}

	@Override
	protected boolean assemblePacketData(ByteBuffer buffer) {
		if(buffer == null || buffer.remaining() < getPacketDataSize())
			return false;

		putCallsignLong(buffer, moduleName);
		putCallsignLong(buffer, callsign);
		for(int i = 0; i < 48; i++) {
			if(version != null && i < version.length)
				buffer.put((byte)version[i]);
			else
				buffer.put((byte)' ');
		}

		return true;
	}

	@Override
	protected int getPacketDataSize() {
		return 8 + 8 + 48;
	}

}
