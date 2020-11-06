package org.jp.illg.dstar.reflector.protocol.dcs.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.reflector.protocol.dcs.DCSPacketTool;
import org.jp.illg.util.FormatUtil;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class DCSPoll implements Cloneable{

	@Getter
	@Setter
	private String reflectorCallsign;

	@Getter
	@Setter
	private String repeaterCallsign;

	@Getter
	@Setter
	private ConnectionDirectionType direction;

	public DCSPoll() {
		super();
	}

	public DCSPoll(String reflectorCallsign, String repeaterCallsign, ConnectionDirectionType direction) {
		this();

		setReflectorCallsign(reflectorCallsign);
		setRepeaterCallsign(repeaterCallsign);
		setDirection(direction);
	}

	public DCSPoll clone() {
		DCSPoll copy = null;

		try {
			copy = (DCSPoll)super.clone();

			copy.reflectorCallsign = this.reflectorCallsign;
			copy.repeaterCallsign = this.repeaterCallsign;

			copy.direction = this.direction;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		final StringBuilder sb = new StringBuilder();

		FormatUtil.addIndent(sb, indentLevel);

		sb.append("ReflectorCallsign=");
		sb.append(getReflectorCallsign());

		sb.append('/');

		sb.append("RepeaterCallsign=");
		sb.append(getRepeaterCallsign());

		sb.append('/');

		sb.append("Direction=");
		sb.append(getDirection());

		return sb.toString();
	}

	public static Optional<DCSPacket> validPacket(ByteBuffer buffer) {
		return DCSPacketTool.isValidPollPacket(buffer);
	}

	public static Optional<byte[]> assemblePacket(DCSPacket packet) {
		return DCSPacketTool.assemblePollPacket(packet);
	}
}
