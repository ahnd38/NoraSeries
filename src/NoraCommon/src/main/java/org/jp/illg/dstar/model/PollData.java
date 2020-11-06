package org.jp.illg.dstar.model;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.util.ArrayUtil;

public class PollData implements Cloneable {

	private char[] callsign;
	private String callsignString;

	public PollData() {
		super();

		this.callsign = new char[DSTARDefines.CallsignFullLength];
		this.callsignString = DSTARDefines.EmptyLongCallsign;
	}

	@Override
	protected PollData clone() throws CloneNotSupportedException {
		PollData copy = null;

		try {
			copy = (PollData)super.clone();

			ArrayUtil.copyOf(copy.callsign, this.callsign);
			copy.callsignString = new String(this.callsignString);

//			copy.localPort = this.localPort;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	/**
	 * @return callsign
	 */
	public String getCallsign() {
		return this.callsignString;
	}

	/**
	 * @param callsign セットする callsign
	 */
	public void setCallsign(String callsign) {
		if(callsign == null || callsign.length() != DSTARDefines.CallsignFullLength){return;}

		this.callsignString = callsign;

		ArrayUtil.copyOf(this.callsign, this.callsignString.toCharArray());
	}


	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}


		StringBuilder sb = new StringBuilder();

		sb.append(indent);
		sb.append("[" + this.getClass().getSimpleName() + "]");

		sb.append("[Callsign]:");
		sb.append(getCallsign());

//		sb.append("/");

//		sb.append("[LocalPort]:");
//		sb.append(getLocalPort());

		return sb.toString();
	}
}
