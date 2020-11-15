package org.jp.illg.nora.gateway.reporter;

import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;

public interface NoraGatewayStatusReporter extends AutoCloseable{

	boolean start();
	void stop();
	boolean isRunning();

	boolean addListener(NoraGatewayStatusReportListener listener);
	boolean removeListener(NoraGatewayStatusReportListener listener);
	void removeListenerAll();

	void close();
}
