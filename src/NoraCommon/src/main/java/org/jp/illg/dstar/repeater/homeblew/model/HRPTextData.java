package org.jp.illg.dstar.repeater.homeblew.model;

import org.jp.illg.dstar.DSTARDefines;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class HRPTextData implements Cloneable{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private HomebrewReflectorLinkStatus reflectorLinkStatus;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String reflectorCallsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String text;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean temporary;

	private HRPTextData() {
		super();
	}

	public HRPTextData(
			HomebrewReflectorLinkStatus reflectorLinkStatus, String reflectorCallsign,
			String text
	) {
		this();

		setReflectorLinkStatus(reflectorLinkStatus);
		setReflectorCallsign(reflectorCallsign);
		setText(text);
		setTemporary(false);
	}

	public HRPTextData(String text) {
		this();

		setReflectorLinkStatus(HomebrewReflectorLinkStatus.LS_NONE);
		setReflectorCallsign(DSTARDefines.EmptyLongCallsign);
		setText(text);
		setTemporary(true);
	}

	@Override
	protected HRPTextData clone() {
		HRPTextData copy = null;
		try {
			copy = (HRPTextData)super.clone();

			copy.reflectorLinkStatus = reflectorLinkStatus;
			copy.reflectorCallsign = new String(reflectorCallsign);
			copy.text = new String(text);
			copy.temporary = temporary;

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

		sb.append("Temporary=");
		sb.append(isTemporary());

		sb.append(" / ");

		sb.append("Text=");
		if(getText() != null)
			sb.append(getText());
		else
			sb.append("null");

		sb.append(" / ");

		sb.append("ReflectorCallsign=");
		if(getReflectorCallsign() != null)
			sb.append(getReflectorCallsign());
		else
			sb.append("null");

		sb.append(" / ");

		sb.append("ReflectorLinkStatus=");
		if(getReflectorLinkStatus() != null)
			sb.append(getReflectorLinkStatus().toString());
		else
			sb.append("null");

		return sb.toString();
	}
}
