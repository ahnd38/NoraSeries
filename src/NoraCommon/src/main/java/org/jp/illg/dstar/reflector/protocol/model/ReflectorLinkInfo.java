package org.jp.illg.dstar.reflector.protocol.model;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;

import lombok.Data;

@Data
public class ReflectorLinkInfo implements Cloneable{

	private String callsign;

	private DSTARProtocol protocol;

	private boolean linked;

	private ConnectionDirectionType direction;

	private boolean dongle;

	public ReflectorLinkInfo() {
		super();
	}

	public ReflectorLinkInfo(
			String callsign, DSTARProtocol protocol, boolean linked, ConnectionDirectionType direction, boolean dongle
	) {
		this();

		setCallsign(callsign);
		setProtocol(protocol);
		setLinked(linked);
		setDirection(direction);
		setDongle(dongle);
	}

	@Override
	public ReflectorLinkInfo clone() {
		ReflectorLinkInfo copy = null;
		try {
			copy = (ReflectorLinkInfo)super.clone();

			copy.callsign = callsign;

			copy.protocol = protocol;

			copy.linked = linked;

			copy.direction = direction;

			copy.dongle = dongle;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

}
