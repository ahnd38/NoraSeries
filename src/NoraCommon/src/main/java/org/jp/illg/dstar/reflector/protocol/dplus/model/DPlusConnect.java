package org.jp.illg.dstar.reflector.protocol.dplus.model;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectTypes;

import lombok.Getter;
import lombok.Setter;

public class DPlusConnect {

	@Getter
	@Setter
	private ReflectorConnectTypes type;

	@Getter
	@Setter
	private String callsign;

	@Getter
	@Setter
	private boolean readonly;


	private DPlusConnect() {
		super();

		setType(ReflectorConnectTypes.UNLINK);
		setCallsign(DSTARDefines.EmptyLongCallsign);
		setReadonly(false);
	}

	public DPlusConnect(ReflectorConnectTypes type) {
		this();

		setType(type);
	}

	@Override
	public DPlusConnect clone() {
		DPlusConnect copy = null;

		try {
			copy = (DPlusConnect)super.clone();

			copy.callsign = this.callsign;

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

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder();

		sb.append(indent);

		sb.append("[Type]:");
		sb.append(getType().toString());

		sb.append("/");

		sb.append("[Callsign]:");
		sb.append(getCallsign());

		return sb.toString();
	}
}
