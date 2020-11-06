package org.jp.illg.dstar.reflector.model;

import java.net.InetAddress;
import java.util.UUID;

import org.apache.commons.lang3.SerializationUtils;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;

import lombok.Data;

@Data
public class ReflectorLinkInformation implements Cloneable{

	private UUID id;

	private String callsign;

	private DSTARProtocol linkProtocol;

	private DSTARRepeater repeater;

	private ConnectionDirectionType connectionDirection;

	private boolean dongle;

	private boolean linked;

	private InetAddress remoteHostAddress;

	private int remoteHostPort;

	private ReflectorHostInfo outgoingHostInfo;


	public ReflectorLinkInformation(){
		super();
	}

	public ReflectorLinkInformation(
		UUID id, String callsign, DSTARProtocol linkProtocol, DSTARRepeater repeater,
		ConnectionDirectionType connectionDirection,
		boolean dongle, boolean linked,
		InetAddress remoteHostAddress, int remoteHostPort, ReflectorHostInfo outgoingHostInfo
	) {
		setId(id);
		setCallsign(callsign);
		setLinkProtocol(linkProtocol);
		setRepeater(repeater);
		setConnectionDirection(connectionDirection);
		setDongle(dongle);
		setLinked(linked);
		setRemoteHostAddress(remoteHostAddress);
		setRemoteHostPort(remoteHostPort);
		setOutgoingHostInfo(outgoingHostInfo);
	}

	public ReflectorLinkInformation(
		UUID id, String callsign, DSTARProtocol linkProtocol, DSTARRepeater repeater,
		ConnectionDirectionType connectionDirection,
		boolean dongle, boolean linked,
		InetAddress remoteHostAddress, int remoteHostPort
	) {
		this(
			id, callsign, linkProtocol, repeater,
			connectionDirection,
			dongle, linked,
			remoteHostAddress, remoteHostPort, null
		);
	}

	@Override
	public ReflectorLinkInformation clone() {
		ReflectorLinkInformation copy = null;
		try {
			copy = (ReflectorLinkInformation)super.clone();

			copy.id = id;
			copy.callsign = callsign;
			copy.linkProtocol = linkProtocol;
			copy.repeater = repeater;
			copy.connectionDirection = connectionDirection;
			copy.dongle = dongle;
			copy.linked = linked;
			copy.remoteHostAddress = SerializationUtils.clone(remoteHostAddress);
			copy.remoteHostPort = remoteHostPort;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}
}
