package org.jp.illg.dstar.model;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.HeardEntryState;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class HeardEntry {

	@Getter
	@Setter
	private long updateTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long heardTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private HeardEntryState state;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DSTARProtocol protocol;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private ConnectionDirectionType direction;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String yourCallsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String repeater1Callsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String repeater2Callsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String myCallsignLong;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String myCallsignShort;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String destination;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String from;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String shortMessage;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean locationAvailable;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private double latitude;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private double longitude;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int packetCount;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private double packetDropRate;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private double bitErrorRate;


	private HeardEntry() {
		super();

		final long time = System.currentTimeMillis();

		setUpdateTime(time);
		setHeardTime(time);
	}

	public HeardEntry(
		final HeardEntryState state,
		final DSTARProtocol protocol,
		final ConnectionDirectionType direction,
		final String yourCallsign,
		final String repeater1Callsign,
		final String repeater2Callsign,
		final String myCallsignLong,
		final String myCallsignShort,
		final String destination,
		final String from,
		final String shortMessage,
		final boolean locationAvailable,
		final double latitude,
		final double longitude,
		final int packetCount,
		final double packetDropRate,
		final double bitErrorRate
	) {
		this();

		setState(state);
		setProtocol(protocol);
		setDirection(direction);

		setYourCallsign(yourCallsign);
		setRepeater1Callsign(repeater1Callsign);
		setRepeater2Callsign(repeater2Callsign);
		setMyCallsignLong(myCallsignLong);
		setMyCallsignShort(myCallsignShort);

		setDestination(destination);
		setFrom(from);
		setShortMessage(shortMessage);
		setLocationAvailable(locationAvailable);
		setLatitude(latitude);
		setLongitude(longitude);

		setPacketCount(packetCount);
		setPacketDropRate(packetDropRate);
		setBitErrorRate(bitErrorRate);
	}



}
