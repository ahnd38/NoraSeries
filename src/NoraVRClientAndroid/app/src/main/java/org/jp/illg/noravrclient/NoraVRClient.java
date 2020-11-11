package org.jp.illg.noravrclient;

import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.ApplicationInformationGradleMaven;

import lombok.Getter;

public class NoraVRClient {

	@Getter
	private static final ApplicationInformation<?> applicationInformation =
		new ApplicationInformationGradleMaven<>();
}
