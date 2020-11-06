package org.jp.illg.nora.vr;

import java.util.List;

import org.jp.illg.nora.vr.protocol.model.NoraVRConfiguration;

public interface NoraVREventListener {

	/**
	 * ログインに失敗した
	 *
	 * @param reason ログインが失敗した理由
	 * @return 再試行を試みる場合にはtrue
	 */
	public boolean loginFailed(String reason);

	/**
	 * ログインが成功しサーバーとの接続が確立した
	 */
	public void loginSuccess(int protocolVersion);

	/**
	 * サーバとの接続が失われた
	 *
	 * @param reason 接続が失われた理由
	 * @return 再接続を試みる場合にはtrue
	 */
	public boolean connectionFailed(String reason);

	/**
	 * サーバに対してクライアント設定の更新が完了した
	 *
	 * @param configuration
	 */
	public void configurationSet(NoraVRConfiguration configuration);

	/**
	 * 音声パケットを受信した
	 */
	public void receiveVoice();

	/**
	 * レピータ情報を通知
	 * @param callsign レピータコールサイン
	 * @param name レピータ名称
	 * @param location レピータ設置場所
	 * @param frequency レピータ周波数
	 * @param serviceRange
	 * @param agl
	 * @param url
	 * @param description1
	 * @param description2
	 */
	public void repeaterInformation(
		String callsign,
		String name,
		String location,
		double frequencyMHz,
		double frequencyOffsetMHz,
		double serviceRangeKm,
		double agl,
		String url,
		String description1,
		String description2
	);

	/**
	 * リンクされているリフレクターコールサインを通知
	 */
	public void reflectorLink(String linkedReflectorCallsign);

	/**
	 * ルーティングサービス名を通知
	 * @param routingServiceName ルーティングサービス名(JapanTrust/GlobalTrust/ircDDB等)
	 */
	public void routingService(String routingServiceName);

	/**
	 * ユーザーリストを通知
	 * @param users ユーザーリスト
	 */
	public void userList(List<NoraVRUser> users);

	/**
	 * アクセスログを通知
	 * @param logs アクセスログ
	 */
	public void accessLog(List<NoraVRAccessLog> logs);

	/**
	 * 送信タイムアウトを通知
	 * @param frameID フレームID
	 */
	public void transmitTimeout(int frameID);
}
