package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

import icepick.Bundler;

public class RepeaterModuleConfigBundler implements Bundler<RepeaterModuleConfig> {

	@Override
	public void put(String key, RepeaterModuleConfig item, Bundle bundle) {
		bundle.putParcelable(key, Parcels.wrap(item));
	}

	@Override
	public RepeaterModuleConfig get(String key, Bundle bundle) {
		return Parcels.unwrap(bundle.getParcelable(key));
	}
}
