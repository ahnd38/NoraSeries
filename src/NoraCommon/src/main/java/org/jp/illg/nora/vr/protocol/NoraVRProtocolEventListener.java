package org.jp.illg.nora.vr.protocol;

import org.jp.illg.nora.vr.model.NoraVRClientEntry;

public interface NoraVRProtocolEventListener {
	/**
	 * クライントログインイベント
	 * @param client クライアントエントリ
	 */
	void onClientLoginEvent(NoraVRClientEntry client);
	
	/**
	 * クライアントログアウトイベント
	 * @param client クライアントエントリ
	 */
	void onClientLogoutEvent(NoraVRClientEntry client);
}
