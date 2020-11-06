package org.jp.illg.dstar.gateway.model;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.util.Timer;

import lombok.Data;

@Data
public class HeardInfo {

	private HeardState state;

	private Header heardHeader;

	private ConnectionDirectionType direction;

	private DSTARProtocol protocol;

	private String destination;

	private String from;

	private String shortMessage;

	private boolean locationAvailable;

	private double latitude;

	private double longitude;

	private boolean statusChanged;

	private boolean statusTransmit;

	private boolean heardTransmit;

	private int packetCount;

	private Timer heardIntervalTimer;

	public HeardInfo() {
		super();

		setState(HeardState.Start);
		setHeardHeader(null);
		setDirection(ConnectionDirectionType.Unknown);
		setDestination(DSTARDefines.EmptyLongCallsign);
		setFrom(DSTARDefines.EmptyLongCallsign);
		setShortMessage("");
		setLocationAvailable(false);
		setLatitude(0);
		setLongitude(0);
		setStatusChanged(false);
		setStatusTransmit(false);
		setHeardTransmit(false);
		setPacketCount(0);
		setHeardIntervalTimer(new Timer());

		getHeardIntervalTimer().updateTimestamp();
	}

	public String toString(final int indentLevel) {
		int indent = indentLevel;
		if(indent < 0) {indent = 0;}

		final StringBuffer sb = new StringBuffer();

		for(int count = 0; count < indent; count++) {sb.append(' ');}

		sb.append(this.toString());

		return sb.toString();
	}
}
