package org.jp.illg.noragateway;

import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.ApplicationInformationGradleMaven;

import lombok.Getter;

public class NoraGateway{
	@Getter
	private static final ApplicationInformation<NoraGateway> applicationInformation =
		new ApplicationInformationGradleMaven<>();
}