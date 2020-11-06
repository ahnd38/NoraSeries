package org.jp.illg.dstar.model;

import java.util.UUID;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class ReflectorRemoteUserEntry implements Cloneable{

	@Getter
	private UUID userId;

	@Getter
	private long loginTime;

	@Getter
	@Setter
	private long logoutTime;

	@Getter
	@Setter
	private long updateTime;

	@Getter
	private String userCallsign;

	@Getter
	private String userCallsignShort;

	@Getter
	private DSTARRepeater localRepeater;

	@Getter
	private String remoteCallsign;

	@Getter
	private ConnectionDirectionType linkDirection;


	public ReflectorRemoteUserEntry(
		@NonNull final UUID userId,
		final long loginTime,
		@NonNull final DSTARRepeater localRepeater,
		@NonNull final String remoteCallsign,
		@NonNull final ConnectionDirectionType linkDirection,
		@NonNull final String userCallsign,
		@NonNull final String userCallsignShort
	) {
		super();

		this.userId = userId;
		this.loginTime = loginTime;
		this.localRepeater = localRepeater;
		this.remoteCallsign = remoteCallsign;
		this.linkDirection = linkDirection;
		this.userCallsign = userCallsign;
		this.userCallsignShort = userCallsignShort;

		this.updateTime = System.currentTimeMillis();
		this.logoutTime = -1;
	}

	@Override
	public ReflectorRemoteUserEntry clone() {
		ReflectorRemoteUserEntry copy = null;

		try {
			copy = (ReflectorRemoteUserEntry)super.clone();

			copy.userId = this.userId;
			copy.loginTime = this.loginTime;
			copy.logoutTime = this.logoutTime;
			copy.updateTime = this.updateTime;
			copy.localRepeater = this.localRepeater;
			copy.remoteCallsign = this.remoteCallsign;
			copy.linkDirection = this.linkDirection;
			copy.userCallsign = this.userCallsign;
			copy.userCallsignShort = this.userCallsignShort;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

}
