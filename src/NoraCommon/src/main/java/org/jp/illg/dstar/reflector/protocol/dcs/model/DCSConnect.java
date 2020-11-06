package org.jp.illg.dstar.reflector.protocol.dcs.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.reflector.protocol.dcs.DCSPacketTool;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectTypes;
import org.jp.illg.util.FormatUtil;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class DCSConnect implements Cloneable{

	@Getter
	@Setter
	private ReflectorConnectTypes type;

	@Getter
	@Setter
	private String reflectorCallsign;

	@Getter
	@Setter
	private String repeaterCallsign;

	@Getter
	@Setter
	private String applicationName;

	@Getter
	@Setter
	private String applicationVersion;


	public DCSConnect() {
		super();

		setType(ReflectorConnectTypes.NAK);

		setReflectorCallsign(DSTARDefines.EmptyLongCallsign);
		setRepeaterCallsign(DSTARDefines.EmptyLongCallsign);

		setApplicationName("");
		setApplicationVersion("");
	}

	@Override
	public DCSConnect clone() {
		DCSConnect copy = null;

		try {
			copy = (DCSConnect)super.clone();

			copy.type = type;
			copy.reflectorCallsign = this.reflectorCallsign;
			copy.repeaterCallsign = this.repeaterCallsign;

			copy.applicationName = this.applicationName;
			copy.applicationVersion = this.applicationVersion;

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

		sb.append("Type=");
		sb.append(getType().toString());

		sb.append('/');

		sb.append("ReflectorCallsign=");
		sb.append(getReflectorCallsign());

		sb.append('/');

		sb.append("RepeaterCallsign=");
		sb.append(getRepeaterCallsign());

		sb.append('/');

		sb.append("ApplicationName=");
		sb.append(getApplicationName());

		sb.append('/');

		sb.append("ApplicationVersion=");
		sb.append(getApplicationVersion());


		return sb.toString();
	}

	public static Optional<DCSPacket> validPacket(ByteBuffer buffer) {
		return DCSPacketTool.isValidConnectPacket(buffer);
	}

	public static Optional<byte[]> assemblePacket(DCSPacket packet) {
		return DCSPacketTool.assembleConnectPacket(packet);
	}
}
