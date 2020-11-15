package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

import icepick.Bundler;

public class ExternalHomebrewRepeaterConfigBundler implements Bundler<ExternalHomebrewRepeaterConfig> {

	@Override
	public void put(String key, ExternalHomebrewRepeaterConfig item, Bundle bundle) {
		bundle.putParcelable(key, Parcels.wrap(item));
	}

	@Override
	public ExternalHomebrewRepeaterConfig get(String key, Bundle bundle) {
		return Parcels.unwrap(bundle.getParcelable(key));
	}
}
