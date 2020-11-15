package org.jp.illg.nora.gateway.service.norausers.model;

public enum NoraUsersStatusReporterState {
	Initialize,
	CheckVersionConnect,
	CheckVersion,
	RequestIDConnect,
	RequestID,
	WaitStatusSendInterval,
	SendStatusConnect,
	SendStatus,
	Wait,
	;
}
