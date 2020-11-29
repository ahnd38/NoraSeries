package org.jp.illg.dstar.model.config;

import lombok.Data;

@Data
public class ServiceProperties {

	private StatusInformationFileOutputServiceProperties statusInformationFileOutputServiceProperties;

	private WebRemoteControlServiceProperties webRemoteControlServiceProperties;

	private HelperServiceProperties helperServiceProperties;

	private CrashReportServiceProperties crashReportServiceProperties;

	private ICOMRepeaterCommunicationServiceProperties icomRepeaterCommunicationServiceProperties;

	private RepeaterNameServiceProperties repeaterNameServiceProperties;

	private ReflectorNameServiceProperties reflectorNameServiceProperties;

	public ServiceProperties() {
		this.statusInformationFileOutputServiceProperties =
			new StatusInformationFileOutputServiceProperties();

		this.webRemoteControlServiceProperties =
			new WebRemoteControlServiceProperties();

		this.helperServiceProperties = new HelperServiceProperties();

		this.crashReportServiceProperties = new CrashReportServiceProperties();

		this.icomRepeaterCommunicationServiceProperties = new ICOMRepeaterCommunicationServiceProperties();

		this.repeaterNameServiceProperties = new RepeaterNameServiceProperties();

		this.reflectorNameServiceProperties = new ReflectorNameServiceProperties();
	}

}
