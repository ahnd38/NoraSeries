package org.jp.illg.dstar.reflector.model.events;

import java.util.UUID;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionStates;

import lombok.AccessLevel;
import lombok.Setter;


public class ReflectorConnectionStateChangeEvent extends ReflectorEventImpl {

	@Setter(AccessLevel.PRIVATE)
	private ReflectorConnectionStates connectionState;


	public ReflectorConnectionStateChangeEvent(
		UUID connectionId,
		ConnectionDirectionType connectionDirection,
		String repeaterCallsign, String reflectorCallsign,
		ReflectorConnectionStates connectionState,
		ReflectorHostInfo reflectorHostInfo
	) {
		super(
			connectionId,
			ReflectorEventTypes.ConnectionStateChange,
			repeaterCallsign, reflectorCallsign,
			connectionDirection,
			reflectorHostInfo
		);

		setConnectionState(connectionState);
	}

	@Override
	public ReflectorConnectionStates getConnectionState() {
		return this.connectionState;
	}
}
