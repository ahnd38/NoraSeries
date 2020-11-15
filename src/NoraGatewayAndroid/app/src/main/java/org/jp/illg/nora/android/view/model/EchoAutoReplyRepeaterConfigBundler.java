package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.parceler.Parcels;

import icepick.Bundler;

public class EchoAutoReplyRepeaterConfigBundler implements Bundler<EchoAutoReplyRepeaterConfig>{

		@Override
		public void put(String key, EchoAutoReplyRepeaterConfig item, Bundle bundle) {
			bundle.putParcelable(key, Parcels.wrap(item));
		}

		@Override
		public EchoAutoReplyRepeaterConfig get(String key, Bundle bundle) {
			return Parcels.unwrap(bundle.getParcelable(key));
		}
}
