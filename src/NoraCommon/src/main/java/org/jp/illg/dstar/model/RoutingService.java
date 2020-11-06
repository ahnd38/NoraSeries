package org.jp.illg.dstar.model;

import java.net.InetAddress;
import java.util.UUID;

import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.dstar.routing.model.PositionUpdateInfo;
import org.jp.illg.dstar.routing.model.RepeaterRoutingInfo;
import org.jp.illg.dstar.routing.model.RoutingCompletedTaskInfo;
import org.jp.illg.dstar.routing.model.RoutingServiceStatusData;
import org.jp.illg.dstar.routing.model.UserRoutingInfo;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRoutingServiceHandler;

import com.annimon.stream.Optional;

import lombok.NonNull;

public interface RoutingService {

	public String getApplicationName();

	public String getApplicationVersion();

	public void setGatewayCallsign(String gatewayCallsign);
	public String getGatewayCallsign();

	public boolean setProperties(RoutingServiceProperties properties);
	public RoutingServiceProperties getProperties(RoutingServiceProperties properties);

	public boolean start();
	public void stop();
	public boolean isRunning();

	public RoutingServiceTypes getServiceType();

	public boolean initializeWebRemoteControl(WebRemoteControlService webRemoteControlService);
	public WebRemoteControlRoutingServiceHandler getWebRemoteControlHandler();

	public RoutingServiceStatusData getServiceStatus();

	public boolean kickWatchdog(String callsign, String statusMessage);

	public boolean sendStatusUpdate(
		int frameID,
		String myCall, String myCallExt,
		String yourCall,
		String repeater1, String repeater2,
		byte flag1, byte flag2, byte flag3,
		String networkDestination,
		String txMessage,
		double latitude,
		double longitude
	);

	public boolean sendStatusAtPTTOn(
		int frameID,
		String myCall, String myCallExt,
		String yourCall,
		String repeater1, String repeater2,
		byte flag1, byte flag2, byte flag3,
		String networkDestination,
		String txMessage,
		double latitude,
		double longitude
	);

	public boolean sendStatusAtPTTOff(
		int frameID,
		String myCall, String myCallExt,
		String yourCall,
		String repeater1, String repeater2,
		byte flag1, byte flag2, byte flag3,
		String networkDestination,
		String txMessage,
		double latitude,
		double longitude,
		int numDvFrames,
		int numDvSlientFrames,
		int numBitErrors
	);

	/**
	 * 最終アクセスレピータ情報を更新する
	 * @param frameID フレームID
	 * @param myCall MYコールサイン
	 * @param myCallExt MYショートコールサイン
	 * @param yourCall URコールサイン
	 * @param repeater1 RPT1コールサイン
	 * @param repeater2 RPT2コールサイン
	 * @param flag1 フラグ1
	 * @param flag2 フラグ2
	 * @param flag3 フラグ3
	 * @return クエリID
	 */
	public UUID positionUpdate(
		int frameID,
		String myCall, String myCallExt,
		String yourCall,
		String repeater1, String repeater2,
		byte flag1, byte flag2, byte flag3
	);

	/**
	 * Heardタスクの完了を取得する
	 * ※再取得不可
	 * @param taskid タスクID
	 * @return 完了していればtrue
	 */
	public PositionUpdateInfo getPositionUpdateCompleted(UUID taskid);

	/**
	 * レピータの検索をリクエストする
	 * @param repeaterCall レピータコールサイン
	 * @param header 受信ヘッダ
	 * @return タスクID(エラー発生時はnull)
	 */
	public UUID findRepeater(String repeaterCall, Header header);

	/**
	 * タスクIDからレピータ情報を取得する
	 * ※再取得不可
	 * @param taskid タスクID
	 * @return レピータ情報(完了していない、もしくはエラー発生時はnull)
	 */
	public RepeaterRoutingInfo getRepeaterInfo(UUID taskid);

	/**
	 * ユーザーの検索をリクエストする
	 * @param userCall ユーザーコールサイン
	 * @param header 受信ヘッダ
	 * @return タスクID(エラー発生時はnull)
	 */
	public UUID findUser(String userCall, Header header);

	/**
	 * タスクIDからユーザー情報を取得する
	 * ※再取得不可
	 * @param taskid タスクID
	 * @return ユーザー情報(完了していない、もしくはエラー発生時はnull)
	 */
	public UserRoutingInfo getUserInfo(UUID taskid);

	/**
	 * 指定されたタスクIDの問い合わせが完了しているか確認する
	 * ※再問い合わせ可能
	 * @param taskid タスクID
	 * @return 指定された問い合わせが完了していればtrue
	 */
	public boolean isServiceTaskCompleted(UUID taskid);

	/**
	 * 指定されたタスクIDの問い合わせが完了しているか確認する
	 * ※再問い合わせ可能
	 * @param taskid タスクID
	 * @return 問い合わせが完了していればタスク情報(他の場合にはnull)
	 */
	public RoutingCompletedTaskInfo getServiceTaskCompleted(UUID taskid);

	/**
	 * 完了しているタスクがあれば取得する
	 * ※再問い合わせ可能
	 *
	 * @return 完了したタスク情報(他の場合にはnull)
	 */
	public RoutingCompletedTaskInfo getServiceTaskCompleted();

	/**
	 * グローバルIPを取得する
	 * @return グローバルIP(emptyの場合もある)
	 */
	public Optional<GlobalIPInfo> getGlobalIPAddress();

	/**
	 * ルーティングサービスのレポートを取得する
	 * @return ルーティングサービスレポート
	 */
	public RoutingServiceStatusReport getRoutingServiceStatusReport();

	/**
	 * 指定されたコールサインに関するキャッシュをアップデートする
	 * @param myCallsign
	 * @param gatewayAddress
	 */
	public void updateCache(@NonNull String myCallsign, @NonNull InetAddress gatewayAddress);
}
