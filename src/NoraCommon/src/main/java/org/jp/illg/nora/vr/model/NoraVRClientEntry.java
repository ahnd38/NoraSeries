package org.jp.illg.nora.vr.model;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.nora.vr.protocol.model.NoraVRConfiguration;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class NoraVRClientEntry {

	@Getter
	private final ReentrantLock locker;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long clientID;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String loginCallsign;

	@Getter
	@Setter
	private NoraVRClientState clientState;

	@Getter
	@Setter
	private NoraVRConfiguration configuration;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private InetSocketAddress remoteHostAddress;

	@Getter
	@Setter
	private NoraVRCodecType downlinkCodec;

	@Getter
	@Setter
	private long loginChallengeCode;

	@Getter
	@Setter
	private int protocolVersion;

	@Getter
	private final Timer keepaliveTimeKeeper;

	@Getter
	private final Timer transmitKeepaliveTimeKeeper;

	@Getter
	@Setter
	private boolean connectionFailed;

	@Getter
	@Setter
	private String applicationName;

	@Getter
	@Setter
	private String applicationVersion;


	private NoraVRClientEntry(
		final long keepaliveLimit, @NonNull final TimeUnit keepaliveTimeLimitUnit
	) {
		super();

		locker = new ReentrantLock();

		setCreateTime(System.currentTimeMillis());

		keepaliveTimeKeeper =
			new Timer(keepaliveLimit, keepaliveTimeLimitUnit);

		transmitKeepaliveTimeKeeper = new Timer();

		setLoginChallengeCode(0x0);

		setProtocolVersion(1);

		setConnectionFailed(false);

		setApplicationName("");
		setApplicationVersion("");
	}

	public NoraVRClientEntry(
		final long clientID, @NonNull NoraVRClientState clientState,
		@NonNull final String loginCallsign,
		@NonNull final InetSocketAddress remoteHostAddress,
		@NonNull final NoraVRConfiguration configuration,
		final String applicationName,
		final String applicationVersion,
		final long keepaliveLimit, @NonNull final TimeUnit keepaliveTimeLimitUnit
	) {
		this(keepaliveLimit, keepaliveTimeLimitUnit);

		setClientID(clientID);
		setClientState(clientState);
		setLoginCallsign(loginCallsign);
		setRemoteHostAddress(remoteHostAddress);
		setConfiguration(configuration);
		setApplicationName(applicationName != null ? applicationName : "");
		setApplicationVersion(applicationVersion != null ? applicationVersion : "");
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder();

		sb.append(indent);

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]:");

		sb.append("LoginCallsign:");
		sb.append(getLoginCallsign());
		sb.append("/");
		sb.append("ClientID:");
		sb.append(String.format("%04X", getClientID()));
		sb.append("/");
		sb.append("ClientState:");
		sb.append(getClientState());
		sb.append("/");
		sb.append("RemoteHostAddress:");
		sb.append(getRemoteHostAddress());
		sb.append("/");
		sb.append("Configuration:");
		sb.append(getConfiguration());

		return sb.toString();
	}
}
