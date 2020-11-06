package org.jp.illg.dstar.routing.service.ircDDB.model;

import java.util.Date;

import lombok.Data;

@Data
public class IRCDDBAppRepeaterEntry implements Cloneable {

	private String areaRepeaterCallsign;
	private Date lastChanged;
	private String zoneRepeaterCallsign;

	public IRCDDBAppRepeaterEntry() {
		super();
	}

	public IRCDDBAppRepeaterEntry(Date dt, String repeaterCallsign, String gatewayCallsign) {
		setAreaRepeaterCallsign(repeaterCallsign);
		setLastChanged(dt);
		setZoneRepeaterCallsign(gatewayCallsign);
	}

	@Override
	public IRCDDBAppRepeaterEntry clone() {
		IRCDDBAppRepeaterEntry copy = null;
		try {
			copy = (IRCDDBAppRepeaterEntry)super.clone();

			copy.areaRepeaterCallsign = areaRepeaterCallsign;
			copy.lastChanged = lastChanged != null ? (Date)lastChanged.clone() : null;
			copy.zoneRepeaterCallsign = zoneRepeaterCallsign;

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}
}
