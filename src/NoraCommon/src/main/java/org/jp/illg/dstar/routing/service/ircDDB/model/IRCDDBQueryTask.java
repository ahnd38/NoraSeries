package org.jp.illg.dstar.routing.service.ircDDB.model;

import java.util.Date;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class IRCDDBQueryTask {

	@Getter
	@Setter
	private UUID taskid;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTime;

	@Getter
	@Setter
	private IRCDDBQueryType queryType;

	@Getter
	@Setter
	private IRCDDBQueryState queryState;

	@Getter
	@Setter
	private IRCDDBQueryResult queryResult;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long activityTime;

	@Getter
	@Setter
	private Date dataTimestamp;

	@Getter
	@Setter
	private String queryCallsign;

	@Getter
	@Setter
	private String repeaterCallsign;

	@Getter
	@Setter
	private String gatewayCallsign;

	@Getter
	@Setter
	private String yourCallsign;

	@Getter
	@Setter
	private String myCallsign;

	@Getter
	@Setter
	private String myCallsignAdd;

	@Getter
	@Setter
	private byte flag1;

	@Getter
	@Setter
	private byte flag2;

	@Getter
	@Setter
	private byte flag3;

	@Getter
	@Setter
	private String destination;

	@Getter
	@Setter
	private String txMessage;

	@Getter
	@Setter
	private String txStatus;

	@Getter
	@Setter
	private String gatewayAddress;



	private IRCDDBQueryTask() {
		super();

		setTaskid(UUID.randomUUID());
		setCreatedTime(System.currentTimeMillis());
		setQueryType(IRCDDBQueryType.Unknown);
		setQueryState(IRCDDBQueryState.Unknown);
		setQueryResult(IRCDDBQueryResult.Unknown);
		updateActivityTime();
	}

	public IRCDDBQueryTask(IRCDDBQueryType queryType) {
		this();

		setQueryType(queryType);
	}

	public void updateActivityTime() {
		setActivityTime(System.currentTimeMillis());
	}

	public boolean isTimeoutActivityTime(long timeoutMillis) {
		return System.currentTimeMillis() > (timeoutMillis + getActivityTime());
	}

}
