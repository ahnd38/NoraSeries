package org.jp.illg.dstar.service.web.handler;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.HeardEntry;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.routing.model.RoutingInfo;
import org.jp.illg.dstar.service.reflectorhosts.ReflectorNameService;
import org.jp.illg.dstar.service.repeatername.RepeaterNameService;
import org.jp.illg.dstar.service.web.model.GatewayStatusData;
import org.jp.illg.util.thread.Callback;

import com.annimon.stream.Optional;

public interface WebRemoteControlGatewayHandler extends WebRemoteControlHandler {


	public String getGatewayCallsign();

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

	public List<ReflectorCommunicationService> getReflectorCommunicationServiceAll();
	public Optional<ReflectorHostInfo> findReflectorByCallsign(String reflectorCallsign);
	public Optional<InetAddress> findReflectorAddressByCallsign(String reflectorCallsign);
	public List<ReflectorHostInfo> findReflectorByFullText(String queryText, int resultSizeLimit);

	public UUID findUser(
		RoutingServiceTypes routingServiceType,
		Callback<RoutingInfo> callback,
		String queryUserCallsign
	);

	public UUID findRepeater(
		RoutingServiceTypes routingServiceType,
		Callback<RoutingInfo> callback,
		String queryRepeaterCallsign
	);

	public List<RoutingService> getRoutingServiceAll();

	public List<HeardEntry> requestHeardLog();
	public List<ReflectorHostInfo> getReflectorHosts();

	public GatewayStatusData createStatusData();

	public Class<? extends GatewayStatusData> getStatusDataType();

	public ReflectorLinkManager getReflectorLinkManager();

	public ReflectorNameService getReflectorNameService();
	public RepeaterNameService getRepeaterNameService();
}
