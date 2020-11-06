package org.jp.illg.dstar.routing.service.ircDDB.model;

import java.util.Date;

import lombok.Data;

@Data
public class IRCDDBAppRepeaterUserEntry implements Cloneable {

	private String userCallsign;

	private String areaRepeaterCallsign;

	private Date updateTime;

	public IRCDDBAppRepeaterUserEntry() {
		super();
	}

	public IRCDDBAppRepeaterUserEntry(Date updateTime, String userCallsign, String areaRepeaterCallsign) {
		this();

		setUpdateTime(updateTime);
		setUserCallsign(userCallsign);
		setAreaRepeaterCallsign(areaRepeaterCallsign);
	}

	@Override
	public IRCDDBAppRepeaterUserEntry clone() {
		IRCDDBAppRepeaterUserEntry copy = null;
		try {
			copy = (IRCDDBAppRepeaterUserEntry)super.clone();

			copy.userCallsign = userCallsign;
			copy.areaRepeaterCallsign = areaRepeaterCallsign;
			copy.updateTime = updateTime != null ? (Date)updateTime.clone() : null;

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}
}
