package org.jp.illg.dstar.reflector.protocol.dplus.model;

public class DPlusPoll {

	public DPlusPoll() {
		super();
	}

	@Override
	public DPlusPoll clone() {
		DPlusPoll copy = null;

		try {
			copy = (DPlusPoll)super.clone();

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

//		String indent = "";
//		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder();

		return sb.toString();
	}
}
