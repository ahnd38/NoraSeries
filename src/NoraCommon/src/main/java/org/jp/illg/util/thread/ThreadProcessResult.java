package org.jp.illg.util.thread;

public enum ThreadProcessResult{
	Unknown,
	NoErrors,
	FatalError,
	FatalErrorWithInfo,
	NormalTerminate,
	;

/*
	private final int errorCode;
	private final String errorMessage;

	ThreadProcessResult(final int errorCode,final String errorMessage) {
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}

	public int getErrorCode() {
		return this.errorCode;
	}

	public String getErrorMessage() {
		return new String(this.errorMessage);
	}

	public static ThreadProcessResult getTypeByErrorCode(byte errorCode) {
		for(ThreadProcessResult v : values()) {
			if(v.getErrorCode() == errorCode) {return v;}
		}
		return ThreadProcessResult.NoError;
	}
*/
}