package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

import icepick.Bundler;

public class ModemNewAccessPointBluetoothConfigBundler implements Bundler<ModemNewAccessPointBluetoothConfig> {
	
	@Override
	public void put(String key, ModemNewAccessPointBluetoothConfig item, Bundle bundle) {
		bundle.putParcelable(key, Parcels.wrap(item));
	}
	
	@Override
	public ModemNewAccessPointBluetoothConfig get(String key, Bundle bundle) {
		return Parcels.unwrap(bundle.getParcelable(key));
	}
}
