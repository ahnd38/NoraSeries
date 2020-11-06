package org.jp.illg.dstar.reflector.protocol.dplus.model;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionEntry;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class DPlusReflectorEntry extends ReflectorConnectionEntry<DPlusTransmitPacketEntry>{

	@Getter
	@Setter
	private DPlusConnectionInternalState currentState;

	@Getter
	@Setter
	private DPlusConnectionInternalState nextState;

	@Getter
	@Setter
	private DPlusConnectionInternalState callbackState;

	@Getter
	@Setter
	private boolean stateChanged;

	@Getter
	@Setter
	private int stateRetryCount;

	@Getter
	@Setter
	private int recevingFrameID;

	@Getter
	@Setter
	private byte receivingFrameSequence;

	@Getter
	@Setter
	private DSTARPacket recevingHeader;

//	@Getter
//	private final DataSegmentDecoder slowdataDecoder;

	@Getter
	@Setter
	private boolean keepAliveReceived;

	@Getter
	@Setter
	private boolean readonly;

//	@Getter
//	@Setter
//	private long packetCount;

	@Getter
	private final Map<Integer, DPlusTransmitFrameEntry> transmitterFrameEntries;


	public DPlusReflectorEntry(
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

		setCurrentState(DPlusConnectionInternalState.Initialize);
		setNextState(DPlusConnectionInternalState.Initialize);
		setCallbackState(DPlusConnectionInternalState.Initialize);
		setStateChanged(false);

		setRecevingFrameID(0x0);
		setReceivingFrameSequence((byte)0x0);
//		setCurrentFrameDirection(ConnectionDirectionType.Unknown);


//		cacheTransmitter = new CacheTransmitter<>(10);
//		setCacheTransmitterUndeflow(false);

		setRecevingHeader(null);

//		slowdataDecoder = new DataSegmentDecoder();

		setKeepAliveReceived(false);

		setReadonly(false);
//		setPacketCount(0);

		transmitterFrameEntries = new HashMap<Integer, DPlusTransmitFrameEntry>();
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

		sb.append("\n");

		sb.append(indent);
		sb.append("[RecevingFrameID]:");
		sb.append(String.format("0x%04X", getRecevingFrameID()));
		sb.append("/");
		sb.append("[RecevingSequence]:");
		sb.append(String.format("0x%02X", getReceivingFrameSequence()));

		return sb.toString();
	}

	@Override
	public DSTARProtocol getProtocol() {
		return DSTARProtocol.DPlus;
	}
}

