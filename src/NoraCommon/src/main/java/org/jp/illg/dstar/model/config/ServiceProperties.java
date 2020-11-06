package org.jp.illg.dstar.model.config;

import lombok.Data;

@Data
public class ServiceProperties {

	private StatusInformationFileOutputServiceProperties statusInformationFileOutputServiceProperties;

	private WebRemoteControlServiceProperties webRemoteControlServiceProperties;

	private ReflectorHostFileDownloadServiceProperties reflectorHostFileDownloadServiceProperties;

	private HelperServiceProperties helperServiceProperties;

	private CrashReportServiceProperties crashReportServiceProperties;

	private ICOMRepeaterCommunicationServiceProperties icomRepeaterCommunicationServiceProperties;

	private RepeaterNameServiceProperties repeaterNameServiceProperties;

	public ServiceProperties() {
		this.statusInformationFileOutputServiceProperties =
			new StatusInformationFileOutputServiceProperties();

		this.webRemoteControlServiceProperties =
			new WebRemoteControlServiceProperties();

		this.reflectorHostFileDownloadServiceProperties =
			new ReflectorHostFileDownloadServiceProperties();

		this.helperServiceProperties = new HelperServiceProperties();

		this.crashReportServiceProperties = new CrashReportServiceProperties();

		this.icomRepeaterCommunicationServiceProperties = new ICOMRepeaterCommunicationServiceProperties();

		this.repeaterNameServiceProperties = new RepeaterNameServiceProperties();
	}

}
