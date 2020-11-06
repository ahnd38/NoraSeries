package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

import icepick.Bundler;

public class VoiceroidAutoReplyRepeaterConfigBundler implements Bundler<VoiceroidAutoReplyRepeaterConfig> {

	@Override
	public void put(String key, VoiceroidAutoReplyRepeaterConfig item, Bundle bundle) {
		bundle.putParcelable(key, Parcels.wrap(item));
	}

	@Override
	public VoiceroidAutoReplyRepeaterConfig get(String key, Bundle bundle) {
		return Parcels.unwrap(bundle.getParcelable(key));
	}
}
