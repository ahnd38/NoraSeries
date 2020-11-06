package org.jp.illg.dstar.repeater.homeblew.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class HRPStatusData implements Cloneable{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int statusNumber;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String statusText;

	private  HRPStatusData() {
		super();
	}

	public HRPStatusData(final int statusNumber, final String statusText) {
		this();

		setStatusNumber(statusNumber);
		setStatusText(statusText);
	}

	@Override
	protected HRPStatusData clone() {
		HRPStatusData copy = null;
		try {
			copy = (HRPStatusData)super.clone();

			copy.statusNumber = statusNumber;
			copy.statusText = new String(statusText);

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

		sb.append("StatusNumber=");
		sb.append(getStatusNumber());

		sb.append(" / ");

		sb.append("StatusText=");
		if(getStatusText() != null)
			sb.append(getStatusText());
		else
			sb.append("null");

		return sb.toString();
	}
}
