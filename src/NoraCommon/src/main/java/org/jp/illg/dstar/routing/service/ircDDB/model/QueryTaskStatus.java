package org.jp.illg.dstar.routing.service.ircDDB.model;

import java.util.UUID;

import org.jp.illg.dstar.routing.service.ircDDB.IrcDDBClient;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class QueryTaskStatus {

	@Getter
	private UUID queryID;

	@Getter
	private IrcDDBClient ddbClient;

	@Getter
	@Setter
	private IRCDDBQueryTask result;

	@Getter
	@Setter
	private boolean complete;

	public QueryTaskStatus(
		@NonNull final UUID queryID,
		@NonNull final IrcDDBClient ddbClient
	) {
		super();

		this.queryID = queryID;
		this.ddbClient = ddbClient;

		setResult(null);
		setComplete(false);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(final int indentLevel) {
		int lvl = indentLevel;
		if(lvl < 0) {lvl = 0;}

		final StringBuffer sb = new StringBuffer();

		for(int c = 0; c < lvl; c++) {sb.append(' ');}

		if(isComplete()) {sb.append("[*]");}
		sb.append("ID:");
		sb.append(getQueryID());
		sb.append("/");
		sb.append("DDBClient:");
		sb.append(getDdbClient().getMyNick());
		sb.append("/");
		sb.append("Result:");
		if(getResult() != null) {
			sb.append(getResult().getQueryType());
			sb.append("+");
			sb.append(getResult().getQueryResult());
		}
		else {sb.append("NULL");}

		return sb.toString();
	}
}
