package org.jp.illg.dstar.repeater.homeblew.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class HRPRegisterData implements Cloneable{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String name;

	private HRPRegisterData() {
		super();
	}

	public HRPRegisterData(String name) {
		this();

		setName(name);
	}

	@Override
	protected HRPRegisterData clone() {
		HRPRegisterData copy = null;
		try {
			copy = (HRPRegisterData)super.clone();

			copy.name = new String(name);

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

		sb.append("Name=");
		if(getName() != null)
			sb.append(getName());
		else
			sb.append("null");

		return sb.toString();
	}
}
