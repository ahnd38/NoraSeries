package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

public class RepeaterConfigBundler implements icepick.Bundler<RepeaterConfig> {
	@Override
	public void put(String key, RepeaterConfig item, Bundle bundle) {
		bundle.putParcelable(key, Parcels.wrap(item));
	}

	@Override
	public RepeaterConfig get(String key, Bundle bundle) {
		return Parcels.unwrap(bundle.getParcelable(key));
	}
}
