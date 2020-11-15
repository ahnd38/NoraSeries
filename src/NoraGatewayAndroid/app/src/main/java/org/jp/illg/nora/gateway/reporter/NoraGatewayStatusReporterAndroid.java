package org.jp.illg.nora.gateway.reporter;

import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import java.util.UUID;

import lombok.NonNull;

public class NoraGatewayStatusReporterAndroid extends NoraGatewayStatusReporterBase {

	public NoraGatewayStatusReporterAndroid(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation
	){
		super(
			systemID,
			exceptionListener,
			applicationInformation
		);
	}

	public NoraGatewayStatusReporterAndroid(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation,
		@NonNull final NoraGatewayStatusReportListener listener
	){
		this(
			systemID,
			exceptionListener,
			applicationInformation
		);

		addListener(listener);
	}

	public NoraGatewayStatusReporterAndroid(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation,
		final NoraGatewayStatusReportListener listener,
		final long reportIntervalTimeMillis
	){
		this(
			systemID,
			exceptionListener,
			applicationInformation,
			listener
		);

		setReportIntervalTimeMillis(reportIntervalTimeMillis);
	}

	public NoraGatewayStatusReporterAndroid(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ApplicationInformation<?> applicationInformation,
		final long reportIntervalTimeMillis
	){
		this(
			systemID,
			exceptionListener,
			applicationInformation
		);

		setReportIntervalTimeMillis(reportIntervalTimeMillis);
	}

	@Override
	protected void processReportInternal(BasicStatusInformation info) {

	}
}
