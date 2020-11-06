package org.jp.illg.dstar.routing.service.ircDDB.model;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jp.illg.util.Timer;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class QueryTask {

	@Getter
	private UUID queryID;

	@Getter
	private IRCDDBQueryType queryType;

	@Getter
	private final Map<ServerEntry, QueryTaskStatus> queries;

	@Getter
	private final IRCDDBQueryTask databaseResult;

	@Getter
	private Timer queryTimer;

	@Getter
	@Setter
	private boolean complete;


	private QueryTask(
		@NonNull final UUID queryID,
		@NonNull final IRCDDBQueryType queryType,
		final Map<ServerEntry, QueryTaskStatus> queries,
		final IRCDDBQueryTask databaseResult,
		final boolean isComplete
	) {
		super();

		this.queryID = queryID;
		this.queryType = queryType;
		if(queries != null)
			this.queries = queries;
		else
			this.queries = Collections.emptyMap();

		this.queryTimer = new Timer();
		this.queryTimer.updateTimestamp();
		this.databaseResult = databaseResult;


		setComplete(isComplete);
	}

	public QueryTask(
		@NonNull final UUID queryID,
		@NonNull final IRCDDBQueryType queryType,
		@NonNull Map<ServerEntry, QueryTaskStatus> queries
	) {
		this(queryID, queryType, queries, null, false);
	}

	public QueryTask(
		@NonNull final UUID queryID,
		@NonNull final IRCDDBQueryType queryType,
		@NonNull final IRCDDBQueryTask databaseResult
	) {
		this(queryID, queryType, null, databaseResult, true);
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

		if(isComplete()) {
			sb.append("[*]");
		}
		sb.append("Type:");
		sb.append(getQueryType());
		sb.append('/');
		sb.append("ID:");
		sb.append(getQueryID());

		if(getQueries() != null && !getQueries().isEmpty()) {
			sb.append('\n');

			for(Iterator<QueryTaskStatus> it = getQueries().values().iterator(); it.hasNext();) {
				final QueryTaskStatus status = it.next();

				sb.append(status.toString(lvl + 4));

				if(it.hasNext()) {sb.append('\n');}
			}
		}

		return sb.toString();
	}
}
