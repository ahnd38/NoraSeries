package org.jp.illg.dstar.model;

import org.jp.illg.util.ToStringWithIndent;

import lombok.Getter;
import lombok.Setter;

public class HeardPacket implements Cloneable, ToStringWithIndent {

	@Getter
	@Setter
	private String terminalCallsign;

	@Getter
	@Setter
	private String areaRepeaterCallsign;


	public HeardPacket(
		final String terminalCallsign,
		final String areaRepeaterCallsign
	) {
		super();

		setTerminalCallsign(terminalCallsign);
		setAreaRepeaterCallsign(areaRepeaterCallsign);
	}

	@Override
	public HeardPacket clone() {
		HeardPacket copy = null;

		try {
			copy = (HeardPacket)super.clone();

			clone(copy, this);

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException();
		}
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(final int indentLevel) {
		final int lvl = indentLevel > 0 ? indentLevel : 0;

		final StringBuilder sb = new StringBuilder();
		for(int c = 0; c < lvl; c++) {sb.append(' ');}

		sb.append("[TerminalCallsign]:");
		sb.append(terminalCallsign);

		sb.append('/');

		sb.append("[AreaRepeaterCallsign]:");
		sb.append(areaRepeaterCallsign);

		return sb.toString();
	}

	private static void clone(final HeardPacket dst, final HeardPacket src) {
		dst.terminalCallsign = src.terminalCallsign;
		dst.areaRepeaterCallsign = src.areaRepeaterCallsign;
	}
}
