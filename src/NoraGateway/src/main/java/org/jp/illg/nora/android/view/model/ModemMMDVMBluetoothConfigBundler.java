package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

import icepick.Bundler;

public class ModemMMDVMBluetoothConfigBundler implements Bundler<ModemMMDVMBluetoothConfig> {
	
	@Override
	public void put(String key, ModemMMDVMBluetoothConfig item, Bundle bundle) {
		bundle.putParcelable(key, Parcels.wrap(item));
	}
	
	@Override
	public ModemMMDVMBluetoothConfig get(String key, Bundle bundle) {
		return Parcels.unwrap(bundle.getParcelable(key));
	}
}

