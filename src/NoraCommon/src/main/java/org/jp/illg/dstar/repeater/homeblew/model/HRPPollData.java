package org.jp.illg.dstar.repeater.homeblew.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class HRPPollData implements Cloneable{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String data;

	private HRPPollData() {
		super();
	}

	public HRPPollData(String data) {
		this();

		setData(data);
	}

	@Override
	protected HRPPollData clone() {
		HRPPollData copy = null;
		try {
			copy = (HRPPollData)super.clone();

			copy.data = new String(data);

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indent) {
		if(indent < 0) {indent = 0;}

		StringBuilder sb = new StringBuilder();

		for(int count = 0; count < indent; count++)
			sb.append(' ');

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]:");

		sb.append("Data=");
		if(getData() != null)
			sb.append(getData());
		else
			sb.append("null");

		return sb.toString();
	}
}
