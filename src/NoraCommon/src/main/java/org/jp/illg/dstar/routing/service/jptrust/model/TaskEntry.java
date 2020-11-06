package org.jp.illg.dstar.routing.service.jptrust.model;

import java.util.UUID;

import org.jp.illg.dstar.model.Header;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class TaskEntry{
	@Getter
	private final UUID id;

	@Getter
	private final long createdTimestamp;

	@Getter
	private TaskStatus taskStatus;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long completedTimestamp;

	@Getter
	@Setter
	private JpTrustCommand requestCommand;

	@Getter
	@Setter
	private JpTrustCommand responseCommand;

	@Getter
	private final Timer activityTimer;

	@Setter
	@Getter
	private Header header;

	@Getter
	@Setter
	private String targetCallsign;


	private TaskEntry() {
		super();

		id = UUID.randomUUID();
		createdTimestamp = System.currentTimeMillis();
		activityTimer = new Timer();
		activityTimer.updateTimestamp();

		setTaskStatus(TaskStatus.Incomplete);
		setCompletedTimestamp(0);
	}

	public TaskEntry(@NonNull final String targetCallsign, @NonNull JpTrustCommand requestCommand) {
		this();

		setRequestCommand(requestCommand);
		setTargetCallsign(targetCallsign);
	}

	public void setTaskStatus(TaskStatus taskStatus) {
		this.taskStatus = taskStatus;
		if(taskStatus == TaskStatus.Complete) {updateCompletedTimestamp();}
	}

	public void updateCompletedTimestamp() {
		this.setCompletedTimestamp(System.currentTimeMillis());
	}
}
