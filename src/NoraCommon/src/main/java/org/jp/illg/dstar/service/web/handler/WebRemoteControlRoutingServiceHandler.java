package org.jp.illg.dstar.service.web.handler;

import java.util.List;

import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.model.QueryCallback;
import org.jp.illg.dstar.routing.model.QueryRepeaterResult;
import org.jp.illg.dstar.routing.model.QueryUserResult;
import org.jp.illg.dstar.service.web.model.RoutingServiceStatusData;

import lombok.NonNull;

public interface WebRemoteControlRoutingServiceHandler extends WebRemoteControlHandler {

	public RoutingServiceTypes getServiceType();

	public RoutingServiceStatusData createStatusData();

	public Class<? extends RoutingServiceStatusData> getStatusDataType();

	public int getCountUserRecords();
	public int getCountRepeaterRecords();

	public boolean findUserRecord(
		@NonNull String userCallsign,
		@NonNull QueryCallback<List<QueryUserResult>> callback
	);

	public boolean findRepeaterRecord(
		@NonNull String areaRepeaterCallsign,
		@NonNull QueryCallback<List<QueryRepeaterResult>> callback
	);
}
