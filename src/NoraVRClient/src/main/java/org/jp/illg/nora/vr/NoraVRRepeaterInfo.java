package org.jp.illg.nora.vr;

import org.jp.illg.dstar.DSTARDefines;

import lombok.Getter;
import lombok.Setter;

public class NoraVRRepeaterInfo implements Cloneable{

	@Getter
	@Setter
	private String callsign;

	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private String location;

	@Getter
	@Setter
	private double frequency;

	@Getter
	@Setter
	private double service_range;

	@Getter
	@Setter
	private double agl;

	@Getter
	@Setter
	private String url;

	@Getter
	@Setter
	private String description1;

	@Getter
	@Setter
	private String description2;


	public NoraVRRepeaterInfo() {
		super();

		clear();
	}

	@Override
	public NoraVRRepeaterInfo clone() {
		NoraVRRepeaterInfo copy = null;

		try {
			copy = (NoraVRRepeaterInfo)super.clone();

			copy.callsign = this.callsign;
			copy.name = this.name;
			copy.location = this.location;
			copy.frequency = this.frequency;
			copy.service_range = this.service_range;
			copy.agl = this.agl;
			copy.url = this.url;
			copy.description1 = this.description1;
			copy.description2 = this.description2;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	public void clear() {
		callsign = DSTARDefines.EmptyLongCallsign;
		name = "";
		location = "";
		frequency = 0.0d;
		service_range = 0.0d;
		agl = 0.0d;
		url = "";
		description1 = "";
		description2 = "";
	}
}
