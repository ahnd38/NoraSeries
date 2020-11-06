package org.jp.illg.dstar.gateway.tool.announce;

public enum AnnounceRepeaterProcessState {
	Initialize,
	QueueAdded,
	ProcessWait,
	WaitBusy,
	Processing,
	Completed,
	;
}
