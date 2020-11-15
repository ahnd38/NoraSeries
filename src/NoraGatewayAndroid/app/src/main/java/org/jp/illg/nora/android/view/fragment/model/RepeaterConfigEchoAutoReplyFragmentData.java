package org.jp.illg.nora.android.view.fragment.model;

import org.jp.illg.nora.android.view.model.EchoAutoReplyRepeaterConfig;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class RepeaterConfigEchoAutoReplyFragmentData {

	private boolean serviceRunning;

	private EchoAutoReplyRepeaterConfig echoAutoReplyRepeaterConfig;

	public RepeaterConfigEchoAutoReplyFragmentData(){}

	public RepeaterConfigEchoAutoReplyFragmentData(
			boolean serviceRunning,
			EchoAutoReplyRepeaterConfig echoAutoReplyRepeaterConfig
	){
		setServiceRunning(serviceRunning);
		setEchoAutoReplyRepeaterConfig(echoAutoReplyRepeaterConfig);
	}
}
