package org.jp.illg.nora.gateway.reporter.model;

import org.jp.illg.dstar.reporter.model.BasicStatusInformation;

public interface NoraGatewayStatusReportListener {
	void listenerProcess();
	void report(BasicStatusInformation info);
}
