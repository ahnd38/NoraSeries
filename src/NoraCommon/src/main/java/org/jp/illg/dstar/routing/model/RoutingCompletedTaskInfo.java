package org.jp.illg.dstar.routing.model;

import java.util.UUID;

import org.jp.illg.dstar.routing.define.RoutingServiceTasks;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class RoutingCompletedTaskInfo {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private UUID taskID;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private RoutingServiceTasks serviceTask;

	public RoutingCompletedTaskInfo(UUID taskID, RoutingServiceTasks serviceTask) {
		setTaskID(taskID);
		setServiceTask(serviceTask);
	}
}
