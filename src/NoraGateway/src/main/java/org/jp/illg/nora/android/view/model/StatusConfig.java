package org.jp.illg.nora.android.view.model;

import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class StatusConfig {

	private boolean disableDisplaySleep;

	public StatusConfig(){
		setDisableDisplaySleep(false);
	}
}
