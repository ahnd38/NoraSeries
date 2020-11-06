package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

import icepick.Bundler;

public class InternalRepeaterConfigBundler implements Bundler<InternalRepeaterConfig>{
	@Override
	public void put(String key, InternalRepeaterConfig item, Bundle bundle) {
		bundle.putParcelable(key, Parcels.wrap(item));
	}

	@Override
	public InternalRepeaterConfig get(String key, Bundle bundle) {
		return Parcels.unwrap(bundle.getParcelable(key));
	}
}
