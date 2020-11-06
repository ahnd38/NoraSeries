package org.jp.illg.dstar.reflector.model.events;

import java.util.UUID;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionStates;

public interface ReflectorEvent {

	public static enum ReflectorEventTypes{
		ConnectionStateChange,
		;
	}

	public long getCreatedTimestamp();

	public ReflectorEventTypes getEventType();

	public UUID getConnectionId();

	public String getRepeaterCallsign();

	public String getReflectorCallsign();

	public ConnectionDirectionType getConnectionDirection();

	public ReflectorConnectionStates getConnectionState();
	
	public ReflectorHostInfo getReflectorHostInfo();

}
