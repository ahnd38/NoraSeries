package org.jp.illg.dstar.model;

import java.util.List;
import java.util.UUID;

import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRepeaterHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;

public interface DSTARRepeater extends ThreadUncaughtExceptionListener {

	/**
	 * システムIDを取得する
	 * @return システムID
	 */
	public UUID getSystemID();

	/**
	 * 待機中のレピータのスレッドを起動する
	 */
	public void wakeupRepeaterWorker();

	/**
	 * レピータをスタートする
	 * @return 正常にスタートした場合にはtrue
	 */
	public boolean start();

	/**
	 * レピータをストップする
	 */
	public void stop();

	/**
	 * レピータが動作状態を取得する
	 * @return 動作している場合にはtrue
	 */
	public boolean isRunning();

	/**
	 * レピータタイプを取得する
	 * @return レピータタイプ
	 */
	public RepeaterTypes getRepeaterType();

	/**
	 * レピータプロパティを設定する
	 * @param properties レピータプロパティ
	 * @return 正常にプロパティを適用できた場合にはtrue
	 */
	public boolean setProperties(RepeaterProperties properties);

	/**
	 * レピータプロパティを取得する
	 * @param properties レピータプロパティ
	 * @return 正常にプロパティを取得できた場合にはtrue
	 */
	public RepeaterProperties getProperties(RepeaterProperties properties);

	/**
	 * レピータからのパケットを読み込む
	 * @return パケット
	 */
	public DSTARPacket readPacket();

	/**
	 * レピータからの読み込めるパケットがあるか確認する
	 * @return 読み込めるパケットがある場合にはtrue
	 */
	public boolean hasReadPacket();

	/**
	 * レピータへパケットを書き込む
	 * @param packet パケット
	 * @return 正常にレピータへパケットを書き込めればtrue
	 */
	public boolean writePacket(DSTARPacket packet);

	/**
	 * レピータがビジーであるか確認する
	 * @return レピータがビジーであればtrue
	 */
	public boolean isBusy();

	/**
	 * レピータコールサインを取得する
	 * @return
	 */
	public String getRepeaterCallsign();

	/**
	 * レピータがリフレクターリンクをサポートしているか取得する
	 * @return レピータがリフレクターリンクをサポートしてればtrue
	 */
	public boolean isReflectorLinkSupport();

	/**
	 * レピータにリンクしているリフレクターコールサインを取得する
	 * @return リンクしているリフレクターコールサイン
	 */
	public String getLinkedReflectorCallsign();

	/**
	 * レピータにリンクしているリフレクターコールサインを設定する
	 * @param linkedReflectorCallsign リフレクターコールサイン
	 */
	public void setLinkedReflectorCallsign(String linkedReflectorCallsign);

	/**
	 * レピータが使用しているルーティングサービスを取得する
	 * @return レピータが使用しているルーティングサービス
	 */
	public RoutingService getRoutingService();

	/**
	 * レピータが使用するルーティングサービスを設定する
	 * @param routingService ルーティングサービス
	 */
	public void setRoutingService(RoutingService routingService);

	/**
	 * レピータが使用するルーティングサービスを固定するか
	 * @return 固定する場合にはtrue
	 */
	public boolean isRoutingServiceFixed();

	/**
	 * レピータが使用するルーティングサービスを固定する
	 * @param routingServiceFixed 固定する場合にはtrue
	 */
	public void setRoutingServiceFixed(boolean routingServiceFixed);

	/**
	 * ゲートウェイ透過モードを取得する
	 * @return ゲートウェイ透過モードに設定されていればtrue
	 */
	public boolean isTransparentMode();

	/**
	 * ゲートウェイ透過モードを設定する
	 * @param transparentMode このレピータをゲートウェイ透過モードに設定する場合にはtrue
	 */
	public void setTransparentMode(boolean transparentMode);

	/**
	 * Outgoing接続を許可するか否かを取得する
	 * @return Outgoing接続を許可する場合にはtrue
	 */
	public boolean isAllowOutgoingConnection();

	/**
	 * Outgoing接続を許可するか否かを設定する
	 * @param allowOutgoingConndction Outgoing接続を許可するか場合にはtrue
	 */
	public void setAllowOutgoingConnection(boolean allowOutgoingConnection);

	/**
	 * Incoming接続を許可するか否かを取得する
	 * @return Incoming接続を許可する場合にはtrue
	 */
	public boolean isAllowIncomingConnection();

	/**
	 * Incoming接続を許可するか否かを設定する
	 * @param allowIncomingConndction Incoming接続を許可する場合はtrue
	 */
	public void setAllowIncomingConnection(boolean allowIncomingConnection);

	/**
	 * このレピータがルーティングサービスを使用するか否かを設定する
	 * @return ルーティングの使用を希望する場合にはtrue
	 */
	public boolean isUseRoutingService();

	/**
	 * このレピータからゲート超えをした際にリフレクタに接続していた場合は切断を希望するフラグを取得する
	 * @return このレピータからゲート超えをした際にリフレクタに接続していた場合には切断を希望する場合にはtrue
	 */
	public boolean isAutoDisconnectFromReflectorOnTxToG2Route();

	/**
	 * リフレクターへのOutgoing接続がこの時間未使用だった場合に自動切断する時間(分)
	 * @return 自動切断までの時間(0であれば無効)
	 */
	public int getAutoDisconnectFromReflectorOutgoingUnusedMinutes();

	/**
	 * 公開範囲を設定する
	 */
	public void setScope(AccessScope scope);

	/**
	 * 公開範囲を設定する
	 */
	public AccessScope getScope();

	/**
	 * 緯度(宣言)を設定する
	 */
	public void setLatitude(double latitude);

	/**
	 * 緯度(宣言)を取得する
	 */
	public double getLatitude();

	/**
	 * 経度(宣言)を設定する
	 */
	public void setLongitude(double longitude);

	/**
	 * 経度(宣言)を取得する
	 */
	public double getLongitude();

	/**
	 * アンテナ地上高(m)を設定する
	 */
	public void setAgl(double agl);

	/**
	 * アンテナ地上高(m)を取得する
	 */
	public double getAgl();

	/**
	 * 備考1を設定する
	 */
	public void setDescription1(String description1);

	/**
	 * 備考1を取得する
	 */
	public String getDescription1();

	/**
	 * 備考2を設定する
	 */
	public void setDescription2(String description1);

	/**
	 * 備考2を取得する
	 */
	public String getDescription2();

	/**
	 * URLを設定する
	 */
	public void setUrl(String url);

	/**
	 * URLを取得する
	 */
	public String getUrl();

	/**
	 * レピータ名を設定する
	 * @param name レピータ名
	 */
	public void setName(String name);

	/**
	 * レピータ名を取得する
	 * @return レピータ名
	 */
	public String getName();

	/**
	 * レピータ設置場所を設定する
	 * @param location レピータ設置場所
	 */
	public void setLocation(String location);

	/**
	 * レピータ設置場所を取得する
	 * @return レピータ設置場所
	 */
	public String getLocation();

	/**
	 * 物理アクセス(サービス)範囲(m)を設定する
	 */
	public void setRange(double range);

	/**
	 * 物理アクセス(サービス)範囲(m)を取得する
	 */
	public double getRange();

	/**
	 * 周波数(Hz)を設定する
	 */
	public void setFrequency(double frequency);

	/**
	 * 周波数(Hz)を取得する
	 */
	public double getFrequency();

	/**
	 * 周波数オフセット(Hz)を設定する
	 */
	public void setFrequencyOffset(double frequencyOffset);

	/**
	 * 周波数オフセット(Hz)を取得する
	 */
	public double getFrequencyOffset();

	/**
	 * レピータステータスレポート情報を取得する
	 * @return ステータスレポート
	 */
	public List<String> getRouterStatus();

	/**
	 * レピータステータスレポート情報を取得する
	 * @return ステータスレポート
	 */
	public RepeaterStatusReport getRepeaterStatusReport();

	/**
	 * レピータに接続されているモデムを取得する
	 * @return レピータリスト
	 */
	public List<RepeaterModem> getModems();

	/**
	 * ウェブ遠隔操作サービス(ダッシュボード)を設定する
	 * @param webRemoteControlService ウェブ遠隔操作サービス
	 */
	public void setWebRemoteControlService(final WebRemoteControlService webRemoteControlService);

	/**
	 * ウェブ遠隔操作サービス(ダッシュボード)を取得する
	 * @return ウェブ遠隔操作サービス
	 */
	public WebRemoteControlService getWebRemoteControlService();

	/**
	 * ウェブ遠隔操作レピータハンドラを取得する
	 * @return ウェブ遠隔操作レピータハンドラ
	 */
	public WebRemoteControlRepeaterHandler getWebRemoteControlHandler();

	/**
	 * リフレクターログインユーザーを通知する
	 * @param remoteCallsign リフレクターコールサイン
	 * @param users
	 */
	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	);

	/**
	 * データを送受信しているかを取得する
	 *
	 * @return データ送受信中であればtrue
	 */
	public boolean isDataTransferring();
}
