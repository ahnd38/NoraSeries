package org.jp.illg.dstar.reflector.protocol.dextra.model;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.protocol.dextra.DExtraDefines;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionEntry;
import org.jp.illg.util.Timer;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class DExtraReflectorEntry extends ReflectorConnectionEntry<DExtraTransmitPacketEntry> {

	private static final long activityTimeoutMillisDefault = TimeUnit.SECONDS.toMillis(60);

	@Getter
	private final Timer activityTimestamp;

	@Getter
	@Setter
	private long activityTimeoutMillis;

	@Getter
	private final Timer stateTimestamp;

	@Getter
	private final Timer pollTimestamp;

	@Getter
	private final Timer keepAliveTimestamp;

	@Getter
	private final Timer frameSequenceTimestamp;

	@Getter
	@Setter
	private DExtraConnectionInternalStates currentState;

	@Getter
	@Setter
	private DExtraConnectionInternalStates nextState;

	@Getter
	@Setter
	private DExtraConnectionInternalStates callbackState;

	@Getter
	@Setter
	private boolean stateChanged;

	@Getter
	@Setter
	private int retryCount;

	@Getter
	@Setter
	private boolean  unlinkRequest;

	@Getter
	@Setter
	private boolean linkRequest;

	@Getter
	@Setter
	private boolean dongle;

	@Getter
	@Setter
	private char repeaterModule;

	@Getter
	@Setter
	private char reflectorModule;

	@Getter
	@Setter
	private boolean linkFailed;

	@Setter
	@Getter
	private int protocolRevision;


	public DExtraReflectorEntry(
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

		this.activityTimeoutMillis = activityTimeoutMillisDefault;

		activityTimestamp = new Timer();

		stateTimestamp = new Timer();

		pollTimestamp = new Timer();;

		keepAliveTimestamp = new Timer();

		frameSequenceTimestamp = new Timer();

		setCurrentState(DExtraConnectionInternalStates.Unknown);
		setNextState(DExtraConnectionInternalStates.Unknown);
		setCallbackState(DExtraConnectionInternalStates.Unknown);
		setStateChanged(false);
		setRetryCount(0);

		setLinkRequest(false);
		setUnlinkRequest(false);

		setDongle(false);
		setReflectorModule(' ');
		setRepeaterModule(' ');

		setLinkFailed(false);

		setProtocolRevision(0);

		updateActivityTimestamp();
		updateStateTimestamp();
		updatePollTimestamp();
		updateKeepAliveTime();
		updateFrameSequenceTimestamp();
	}

	public DExtraReflectorEntry(
		@NonNull final UUID loopBlockID,
		final int transmitterCacheSize,
		@NonNull final InetSocketAddress remoteAddressPort,
		@NonNull final InetSocketAddress localAddressPort,
		@NonNull final ConnectionDirectionType connectionDirection,
		final long activityTimeoutMillis
	) {
		this(
			loopBlockID,
			transmitterCacheSize,
			remoteAddressPort,
			localAddressPort,
			connectionDirection
		);

		if(activityTimeoutMillis > 0)
			this.activityTimeoutMillis = activityTimeoutMillis;
	}

	public void updateActivityTimestamp() {
		getActivityTimestamp().updateTimestamp();
	}

	public boolean isTimeoutActivity() {
		return getActivityTimestamp().isTimeout(getActivityTimeoutMillis(), TimeUnit.MILLISECONDS);
	}

	public void updateStateTimestamp() {
		getStateTimestamp().updateTimestamp();
		updateActivityTimestamp();
	}

	public boolean isTimeoutState(long timeoutMillis) {
		return getStateTimestamp().isTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
	}

	public void updatePollTimestamp() {
		getPollTimestamp().updateTimestamp();
		updateActivityTimestamp();
	}

	public boolean isTimeoutedPoll() {
		return getPollTimestamp().isTimeout(DExtraDefines.keepAlivePeriodMillis, TimeUnit.MILLISECONDS);
//		return System.currentTimeMillis() > (getPollTimestamp() + DExtraDefines.keepAlivePeriodMillis);
	}

	public void updateFrameSequenceTimestamp() {
		getFrameSequenceTimestamp().updateTimestamp();
		updateActivityTimestamp();
	}

	public boolean isTimeoutedFrameSequence(long timeoutMillis) {
		return getFrameSequenceTimestamp().isTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
	}

	public void updateKeepAliveTime() {
		getKeepAliveTimestamp().updateTimestamp();
		updateActivityTimestamp();
	}

	public boolean isTimedoutKeepAlive() {
		return getKeepAliveTimestamp().isTimeout(DExtraDefines.keepAliveTimeoutMillis, TimeUnit.MILLISECONDS);
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
		sb.append("[RetryCount]:");
		sb.append(getRetryCount());

		sb.append("\n");

		sb.append(indent);
		sb.append("[LinkRequest]:");
		sb.append(isLinkRequest());
		sb.append("/");
		sb.append("[UnlinkRequest]:");
		sb.append(isUnlinkRequest());

		return sb.toString();
	}

	@Override
	public DSTARProtocol getProtocol() {
		return DSTARProtocol.DExtra;
	}
}
