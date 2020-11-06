package org.jp.illg.dstar.model;

import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.HeardEntryState;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfoKey;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.reporter.model.GatewayStatusReport;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;

import lombok.NonNull;

public interface DSTARGateway extends ThreadUncaughtExceptionListener {

	/**
	 * システムIDを取得する
	 */
	public UUID getSystemID();

	/**
	 * アプリケーションバージョンを取得する
	 * @return アプリケーションバージョン
	 */
	public String getApplicationVersion();

	/**
	 * アプリケーション名を取得する
	 * @return アプリケーション名
	 */
	public String getApplicationName();

	/**
	 * ゲートウェイのスレッドを起動する
	 */
	public void wakeupGatewayWorker();

	/**
	 * ゲートウェイの動作を開始する
	 * @return 開始成功ならtrue
	 */
	public boolean start();

	/**
	 * ゲートウェイの動作を停止する<br>
	 * <br>
	 * ※ゲートウェイのスレッドが停止するまでブロックされます
	 */
	public void stop();

	/**
	 * ゲートウェイが動状態を取得する
	 * @return ゲートウェイが動作中であればtrue
	 */
	public boolean isRunning();

	/**
	 * ゲートウェイのインスタンスを取得する
	 * @return ゲートウェイインスタンス(このインスタンス)
	 */
	public DSTARGateway getGateway();

	/**
	 * レピータを取得する
	 * @param repeaterCallsign レピータコールサイン
	 * @return レピータインスタンス(存在しない場合にはnull)
	 */
	public DSTARRepeater getRepeater(String repeaterCallsign);

	/**
	 * レピータを全て取得する
	 * @return レピータリスト
	 */
	public List<DSTARRepeater> getRepeaters();

	/**
	 * ウェブ遠隔操作サービス(ダッシュボード)
	 * @param webRemoteControlService ウェブ遠隔操作サービス
	 */
	public void setWebRemoteControlService(final WebRemoteControlService webRemoteControlService);

	/**
	 * ウェブ遠隔操作サービス(ダッシュボード)
	 * @return ウェブ遠隔操作サービス
	 */
	public WebRemoteControlService getWebRemoteControlService();

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
	 * ゲートウェイ名を設定する
	 * @param name ゲートウェイ名
	 */
	public void setName(String name);

	/**
	 * ゲートウェイ名を取得する
	 * @return ゲートウェイ名
	 */
	public String getName();

	/**
	 * ゲートウェイ設置場所を設定する
	 * @param location ゲートウェイ設置場所
	 */
	public void setLocation(String location);

	/**
	 * ゲートウェイ設置場所を取得する
	 * @return ゲートウェイ設置場所
	 */
	public String getLocation();

	/**
	 * ダッシュボードURLを設定する
	 * @param dashboardUrl ダッシュボードURL
	 */
	public void setDashboardUrl(String dashboardUrl);

	/**
	 * ダッシュボードURLを取得する
	 * @return ダッシュボードURL
	 */
	public String getDashboardUrl();

	/**
	 * Proxyゲートウェイ有効フラグを設定する
	 * @param useProxy Proxy有効ならtrue
	 */
	public void setUseProxy(boolean useProxy);

	/**
	 * Proxyゲートウェイ有効フラグを取得する
	 * @return Proxy有効ならtrue
	 */
	public boolean isUseProxy();

	/**
	 * Proxyサーバアドレスを設定する
	 * @param proxyServerAddress Proxyサーバアドレス
	 */
	public void setProxyServerAddress(String proxyServerAddress);

	/**
	 * Proxyサーバアドレスを取得する
	 * @return Proxyサーバアドレス
	 */
	public String getProxyServerAddress();

	/**
	 * Proxyサーバポートを設定する
	 * @param proxyServerPort Proxyサーバポート
	 */
	public void setProxyServerPort(int proxyServerPort);

	/**
	 * Proxyサーバポートを取得する
	 * @return Proxyサーバポート
	 */
	public int getProxyServerPort();

	/**
	 * 最終アクセスコールサインを設定する
	 * @param lastHeardCallsign 最終アクセスコールwサイン
	 */
	public void setLastHeardCallsign(String lastHeardCallsign);

	/**
	 * 最終アクセスコールサインを取得する
	 * @return 最終アクセスコールサイン
	 */
	public String getLastHeardCallsign();

	/**
	 * データを送受信しているかを取得する
	 *
	 * @return データ送受信中であればtrue
	 */
	public boolean isDataTransferring();

	/**
	 * レピータイベントリスナを取得する
	 * @return レピータイベントリスナ
	 */
	public EventListener<DStarRepeaterEvent> getOnRepeaterEventListener();

	public Optional<ReflectorCommunicationService> getReflectorCommunicationService(
		DSTARProtocol reflectorProtocol
	);

	@Deprecated
	public Optional<ReflectorCommunicationService> getReflectorCommunicationService(
		String reflectorCallsign
	);
	public List<ReflectorCommunicationService> getReflectorCommunicationServiceAll();

	public RoutingService getRoutingService(RoutingServiceTypes routingServiceType);
	public List<RoutingService> getRoutingServiceAll();

	public boolean setProperties(GatewayProperties properties);
	public GatewayProperties getProperties(GatewayProperties properties);

	public String getGatewayCallsign();

	public boolean writePacketToG123Route(DSTARPacket packet, InetAddress destinationAddress);
	public boolean writePacketToReflectorRoute(
		DSTARRepeater repeater, ConnectionDirectionType direction, DSTARPacket packet
	);

	public UUID positionUpdate(
		DSTARRepeater repeater,
		int frameID,
		String myCall, String myCallExt,
		String yourCall,
		String repeater1, String repeater2,
		byte flag1, byte flag2, byte flag3
	);
	public UUID findRepeater(DSTARRepeater repeater, String repeaterCall, Header header);
	public UUID findUser(DSTARRepeater repeater, String userCall, Header header);

	public Optional<ReflectorHostInfo> findReflectorByCallsign(String reflectorCallsign);
	public Optional<InetAddress> findReflectorAddressByCallsign(String reflectorCallsign);
	public boolean loadReflectorHosts(String filePath, boolean rewriteDataSource);
	public boolean loadReflectorHosts(URL url, boolean rewriteDataSource);
	public boolean loadReflectorHosts(
		Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts,
		final String dataSource,
		final boolean deleteSameDataSource
	);
	public boolean saveReflectorHosts(String filePath);

	public boolean linkReflector(
		DSTARRepeater repeater, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	);
	public void unlinkReflector(DSTARRepeater repeater);

	public void notifyLinkReflector(
		String repeaterCallsign, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	);
	public void notifyUnlinkReflector(
		String repeaterCallsign, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	);
	public void notifyLinkFailedReflector(
		String repeaterCallsign, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	);

	public void kickWatchdogFromRepeater(String repeaterCallsign, String statusMessage);

	public ReflectorLinkManager getReflectorLinkManager();

	public boolean isReflectorLinked(
		@NonNull final DSTARRepeater repeater,
		@NonNull final ConnectionDirectionType dir
	);

	public List<String> getLinkedReflectorCallsign(
		@NonNull final DSTARRepeater repeater,
		@NonNull final ConnectionDirectionType dir
	);

	public String getOutgoingLinkedReflectorCallsign(
		@NonNull final DSTARRepeater repeater
	);

	public List<String> getIncommingLinkedReflectorCallsign(
		@NonNull final DSTARRepeater repeater
	);

	public boolean changeRoutingService(
		DSTARRepeater repeater, RoutingServiceTypes routingServiceType
	);

	public List<String> getRouterStatus();

	public GatewayStatusReport getGatewayStatusReport();

	public List<ReflectorStatusReport> getReflectorStatusReport();

	public List<RoutingServiceStatusReport> getRoutingStatusReport();

	public Optional<InetAddress> getGatewayGlobalIP();

	public boolean addHeardEntry(
		@NonNull final HeardEntryState state,
		@NonNull final DSTARProtocol protocol,
		@NonNull final ConnectionDirectionType direction,
		@NonNull final String yourCallsign,
		@NonNull final String repeater1Callsign,
		@NonNull final String repeater2Callsign,
		@NonNull final String myCallsignLong,
		@NonNull final String myCallsignShort,
		@NonNull final String destination,
		@NonNull final String from,
		@NonNull final String shortMessage,
		final boolean locationAvailable,
		final double latitude,
		final double longitude,
		final int packetCount,
		final double packetDropRate,
		final double bitErrorRate
	);

	public List<HeardEntry> getHeardEntries();

	public void notifyIncomingPacketFromG123Route(
		@NonNull String myCallsign,
		@NonNull InetAddress gatewayAddress
	);

	public void notifyReflectorHostChanged(
		@NonNull List<ReflectorHostInfo> hosts
	);

	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull final DSTARRepeater localRepeater,
		@NonNull final String remoteCallsign,
		@NonNull ConnectionDirectionType connectionDir,
		@NonNull final List<ReflectorRemoteUserEntry> users
	);
}
