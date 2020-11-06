package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.GatewayConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class GatewayConfigFragmentData {

	private boolean serviceRunning;

	private GatewayConfig gatewayConfig;

	public GatewayConfigFragmentData(){}

	public GatewayConfigFragmentData(boolean serviceRunning, GatewayConfig gatewayConfig){
		setServiceRunning(serviceRunning);
		setGatewayConfig(gatewayConfig);
	}
}
