package org.jp.illg.dstar.reflector.protocol.dcs.model;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionEntry;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class DCSReflectorEntry extends ReflectorConnectionEntry<DCSTransmitPacketEntry> {

	@Getter
	@Setter
	private DCSLinkInternalState currentState;

	@Getter
	@Setter
	private DCSLinkInternalState nextState;

	@Getter
	@Setter
	private DCSLinkInternalState callbackState;

	@Getter
	@Setter
	private boolean stateChanged;

	@Getter
	@Setter
	private int stateRetryCount;

	@Getter
	@Setter
	private int currentLongFrameSequence;

	@Getter
	@Setter
	private boolean keepAliveReceived;

	@Getter
	@Setter
	private boolean xlxMode;

	@Getter
	@Setter
	private String transmitMessage;

	@Getter
	@Setter
	private long packetCount;


	public DCSReflectorEntry(
		@NonNull final UUID loopBlockID,
		final int transmitterCacheSize,
		@NonNull final InetSocketAddress remoteAddressPort,
		@NonNull final InetSocketAddress localAddressPort,
		@NonNull final ConnectionDirectionType connectionDirection
	) {
		super(
			loopBlockID,
			transmitterCacheSize,
			remoteAddressPort,
			localAddressPort,
			connectionDirection
		);

		setCurrentState(DCSLinkInternalState.Initialize);
		setNextState(DCSLinkInternalState.Initialize);
		setCallbackState(DCSLinkInternalState.Initialize);
		setStateChanged(false);

		setCurrentLongFrameSequence(0x0);

		setKeepAliveReceived(false);

		setXlxMode(false);

		setTransmitMessage(null);

		setPacketCount(0);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		final StringBuilder sb = new StringBuilder();

		sb.append(super.toString(indentLevel));

		sb.append("\n");

		sb.append(indent);
		sb.append("[State]:");
		sb.append(getCurrentState());


		return sb.toString();
	}

	@Override
	public DSTARProtocol getProtocol() {
		return DSTARProtocol.DCS;
	}
}
