package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

import icepick.Bundler;

public class StatusConfigBundler implements Bundler<StatusConfig> {

	@Override
	public void put(String key, StatusConfig item, Bundle bundle) {
		bundle.putParcelable(key, Parcels.wrap(item));
	}

	@Override
	public StatusConfig get(String key, Bundle bundle) {
		return Parcels.unwrap(bundle.getParcelable(key));
	}
}
