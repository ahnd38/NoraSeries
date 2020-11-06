package org.jp.illg.dstar.service.web.model;

import lombok.Getter;

public enum WebRemoteControlErrorCode {
	SystemError             ( -1, "System error"),
	NoError                 (  0, "No error"),
	NotConnected            (  1, "Client is not connected"),
	RequestLimitExceed      (  2, "Request limit exceed"),
	ConnectionLimitExceed   (  3, "Connection limit exceed"),
	IllegalState            (  4, "Illegal state"),
	EmptyUserName           (  5, "User name is empty"),
	UserNotFound            (  6, "User is not found"),
	TokenExpired            (  7, "Token expired"),
	TokenInvalid            (  8, "Token invalid"),
	UserOrPasswordInvalid   (  9, "Username or password is invalid"),
	AuthorizedPermission    ( 10, "User is not have authorized permission"),
	;

	@Getter
	private int errorCode;

	@Getter
	private String message;

	private WebRemoteControlErrorCode(final int errorCode, final String message) {
		this.errorCode = errorCode;
		this.message = message;
	}

	public final String getTypeName() {
		return this.toString();
	}
}
