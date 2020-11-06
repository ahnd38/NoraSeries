package org.jp.illg.dstar.model.config;

import lombok.Data;

@Data
public class WebRemoteControlServiceProperties {

	/**
	 * サービスの有効フラグ
	 */
	private boolean enable = false;

	/**
	 * socket.ioサーバのポート番号
	 */
	private int port = portDefault;
	public static final int portDefault = 3000;

	/**
	 * socket.ioサーバのコンテキスト名<br>
	 * (Web proxyサーバ側と合わせる必要がある)
	 */
	private String context = "/socket.io";

	/**
	 * ログインユーザーリストファイル
	 */
	private String userListFile;
	public static final String userListFileDefault = "./config/WebRemoteControlUsers.xml";
}
