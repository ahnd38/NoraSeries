package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

import icepick.Bundler;

public class ModemMMDVMConfigBundler implements Bundler<ModemMMDVMConfig> {
	
	@Override
	public void put(String key, ModemMMDVMConfig item, Bundle bundle) {
		bundle.putParcelable(key, Parcels.wrap(item));
	}
	
	@Override
	public ModemMMDVMConfig get(String key, Bundle bundle) {
		return Parcels.unwrap(bundle.getParcelable(key));
	}
}
