package org.jp.illg.nora.android.view.model;

import android.os.AsyncTask;

import org.parceler.Parcel;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class ApplicationConfig {

	private GatewayConfig gatewayConfig;

	private RepeaterConfig repeaterConfig;

	private StatusConfig statusConfig;

	public ApplicationConfig(){
		super();

		setGatewayConfig(new GatewayConfig());
		setRepeaterConfig(new RepeaterConfig());
		setStatusConfig(new StatusConfig());
	}

	public ApplicationConfig(GatewayConfig gatewayConfig, RepeaterConfig repeaterConfig, StatusConfig statusConfig){
		this();

		setGatewayConfig(gatewayConfig);
		setRepeaterConfig(repeaterConfig);
		setStatusConfig(statusConfig);
	}
}
