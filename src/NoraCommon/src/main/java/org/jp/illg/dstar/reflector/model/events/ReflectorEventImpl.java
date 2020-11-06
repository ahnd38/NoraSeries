package org.jp.illg.dstar.reflector.model.events;

import java.util.UUID;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionStates;

import lombok.AccessLevel;
import lombok.Setter;

public abstract class ReflectorEventImpl implements ReflectorEvent {

	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	@Setter(AccessLevel.PROTECTED)
	private ReflectorEventTypes eventType;

	@Setter(AccessLevel.PROTECTED)
	private String repeaterCallsign;

	@Setter(AccessLevel.PROTECTED)
	private String reflectorCallsign;

	@Setter(AccessLevel.PROTECTED)
	private ConnectionDirectionType connectionDirection;

	@Setter(AccessLevel.PROTECTED)
	private UUID connectionId;
	
	@Setter(AccessLevel.PRIVATE)
	private ReflectorHostInfo reflectorHostInfo;
	

	private ReflectorEventImpl() {
		super();

		setCreatedTimestamp(System.currentTimeMillis());
		setConnectionDirection(ConnectionDirectionType.Unknown);
	}

	public ReflectorEventImpl(
		UUID connectionId,
		ReflectorEventTypes eventType,
		String repeaterCallsign, String reflectorCallsign,
		ConnectionDirectionType connectionDirection
	) {
		this();

		setConnectionId(connectionId);
		setEventType(eventType);
		setRepeaterCallsign(repeaterCallsign);
		setReflectorCallsign(reflectorCallsign);
		setConnectionDirection(connectionDirection);
	}
	
	public ReflectorEventImpl(
		UUID connectionId,
		ReflectorEventTypes eventType,
		String repeaterCallsign, String reflectorCallsign,
		ConnectionDirectionType connectionDirection,
		ReflectorHostInfo reflectorHostInfo
	) {
		this(
			connectionId, eventType,
			repeaterCallsign, reflectorCallsign, connectionDirection
		);

		setReflectorHostInfo(reflectorHostInfo);
	}

	@Override
	public long getCreatedTimestamp() {
		return this.createdTimestamp;
	}

	@Override
	public ReflectorEventTypes getEventType() {
		return this.eventType;
	}

	@Override
	public String getRepeaterCallsign() {
		return this.repeaterCallsign;
	}

	@Override
	public String getReflectorCallsign() {
		return this.reflectorCallsign;
	}

	@Override
	public ConnectionDirectionType getConnectionDirection() {
		return this.connectionDirection;
	}

	@Override
	public UUID getConnectionId() {
		return this.connectionId;
	}

	@Override
	public abstract ReflectorConnectionStates getConnectionState();
	
	@Override
	public ReflectorHostInfo getReflectorHostInfo() {
		return this.reflectorHostInfo;
	}
}
