package org.jp.illg.dstar.gateway.helper;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.DSTARGatewayImpl;
import org.jp.illg.dstar.gateway.model.ProcessEntry;
import org.jp.illg.dstar.gateway.model.ProcessModes;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.thread.RunnableTask;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayHelperBase {

	@Getter(AccessLevel.PROTECTED)
	private static final int packetCacheLimitDefault = 100;

	private static final String logTag = GatewayHelperBase.class.getSimpleName() + " : ";

	protected GatewayHelperBase() {}

	protected static int getPacketCacheLimit() {
		return packetCacheLimitDefault;
	}

	protected static boolean checkHeader(
		@NonNull final DSTARGatewayImpl gateway,
		@NonNull final DSTARPacket header,
		@NonNull final List<DSTARRepeater> repeaters
	) {
		if(header.getPacketType() != DSTARPacketType.DD && header.getPacketType() != DSTARPacketType.DV)
			return false;

		if(!header.getRFHeader().isSetRepeaterRouteFlag(RepeaterRoute.TO_TERMINAL)) {
			log.warn(logTag + "Non terminal packet received, ignore header packet.\n" + header.toString(4));

			return false;
		}

		final RepeaterControlFlag repeaterControlFlag = header.getRFHeader().getRepeaterControlFlag();
		if (
			repeaterControlFlag == null ||
			(
				repeaterControlFlag != RepeaterControlFlag.NOTHING_NULL &&
				repeaterControlFlag != RepeaterControlFlag.CANT_REPEAT &&
				repeaterControlFlag != RepeaterControlFlag.AUTO_REPLY
			)
		) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Illegal control flag received, ignore.\n" + header.toString(4));

			return false;
		}

		final String repeater1Callsign = header.getRFHeader().getRepeater1CallsignString();
		final String repeater2Callsign = header.getRFHeader().getRepeater2CallsignString();

		//RPT1,RPT2チェック
		final boolean repeater1CallsignValid =
			gateway.getGatewayCallsign().equals(repeater1Callsign) ||
			Stream.of(repeaters)
			.anyMatch(new Predicate<DSTARRepeater>() {
				@Override
				public boolean test(DSTARRepeater repeater) {
					return repeater.getRepeaterCallsign().equals(repeater1Callsign);
				}
			});

		final boolean repeater2CallsignValid =
			gateway.getGatewayCallsign().equals(repeater2Callsign) ||
			Stream.of(repeaters)
			.anyMatch(new Predicate<DSTARRepeater>() {
				@Override
				public boolean test(DSTARRepeater repeater) {
					return repeater.getRepeaterCallsign().equals(repeater2Callsign);
				}
			});

		if(!repeater1CallsignValid || !repeater2CallsignValid) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Unknown route packet received.\n" + header.toString(4));

			return false;
		}
		else if(
			!CallSignValidator.isValidUserCallsign(header.getRFHeader().getMyCallsign())
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"G/W:" + gateway.getGatewayCallsign() +
					" received invalid my callsign header.\n" + header.toString(4)
				);
			}

			return false;
		}
		else if(
			DSTARDefines.EmptyLongCallsign.equals(String.valueOf(header.getRFHeader().getYourCallsign()))
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"G/W:" + gateway.getGatewayCallsign() +
					" received invalid empty your callsign header from repeater...\n" + header.toString(4)
				);
			}

			return false;
		}

		return true;
	}

	/**
	 * 各リフレクターのIncomingLinkへ送信する
	 *
	 * @param gateway DSTARゲートウェイ
	 * @param entry フレームプロセスエントリ
	 * @param targetRepeater 対象レピータ(from)
	 * @param packet DVパケット
	 * @return 正常に書き込みが終了ならtrue
	 */
	protected static boolean writeToReflectorIncomingLink(
		@NonNull final DSTARGatewayImpl gateway,
		@NonNull final ProcessEntry entry,
		@NonNull final DSTARRepeater targetRepeater,
		@NonNull final DSTARPacket packet
	) {
		if(
			entry.getProcessMode() == ProcessModes.RepeaterToCrossband ||
			entry.getProcessMode() == ProcessModes.RepeaterToG2 ||
			entry.getProcessMode() == ProcessModes.RepeaterToReflector ||
			entry.getProcessMode() == ProcessModes.HeardOnly
		) {
			final DSTARPacket clonePacket = packet.clone();
			if(updateToReflectorHeader(clonePacket, targetRepeater)) {
				return gateway.writePacketToReflectorRoute(
					targetRepeater, ConnectionDirectionType.INCOMING,
					clonePacket
				);
			}
		}

		return false;
	}

	/**
	 * 各リフレクターへ送信する
	 *
	 * @param gateway DSTARゲートウェイ
	 * @param entry フレームプロセスエントリ
	 * @param targetRepeater 対象レピータ(from)
	 * @param dir リフレクターリンク方向
	 * @param packet パケット
	 * @return 正常に書き込みが終了ならtrue
	 */
	protected static boolean writeToReflector(
		@NonNull final DSTARGateway gateway,
		@NonNull final ProcessEntry entry,
		@NonNull final DSTARRepeater targetRepeater,
		@NonNull final ConnectionDirectionType dir,
		@NonNull final DSTARPacket packet
	) {
		final DSTARPacket clonePacket = packet.clone();

		return updateToReflectorHeader(clonePacket, targetRepeater) &&
			gateway.writePacketToReflectorRoute(
				targetRepeater, ConnectionDirectionType.OUTGOING, clonePacket
			);
	}

	/**
	 * 制御フラグ付きヘッダを指定されたレピータへ送信する
	 *
	 * @param repeater 送信先レピータ
	 * @param userCallsign 対象ユーザーコールサイン
	 * @param flag 制御フラグ
	 * @return 送信が正常に行われればtrue
	 */
	protected static boolean sendFlagToRepeaterUser(
		@NonNull DSTARGatewayImpl gateway,
		@NonNull DSTARRepeater repeater,
		@NonNull String userCallsign,
		@NonNull RepeaterControlFlag flag
	) {
		final Queue<DSTARPacket> packets = DSTARUtils.createReplyPacketsSingle(
			DSTARUtils.generateFrameID(),
			flag,
			gateway.getGatewayCallsign(),
			userCallsign,
			gateway.getGatewayCallsign(),
			repeater.getRepeaterCallsign()
		);

		try {
			gateway.getWorkerExecutor().submit(new RunnableTask(gateway) {
				@Override
				public void task() {
					DSTARPacket packet = null;
					while((packet = packets.poll()) != null) {
						repeater.writePacket(packet);
					}
				}
			});
		}catch(RejectedExecutionException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Worker schedule error", ex);

			return false;
		}

		return true;
	}

	protected static boolean addCachePacket(
		@NonNull final ProcessEntry entry,
		@NonNull final DSTARPacket packet
	) {
		while (entry.getCachePackets().size() >= getPacketCacheLimit()) {
			entry.getCachePackets().poll();
		}
		return entry.getCachePackets().add(packet);
	}

	protected static boolean updateToReflectorHeader(
		@NonNull final DSTARPacket packet,
		@NonNull final DSTARRepeater repeater
	) {
		if(packet.getDVPacket().hasPacketType(PacketType.Header)) {
			packet.getRFHeader().setRepeater1Callsign(repeater.getRepeaterCallsign().toCharArray());
			packet.getRFHeader().setRepeater2Callsign(repeater.getLinkedReflectorCallsign().toCharArray());

			return true;
		}
		else {
			return false;
		}
	}

	protected static boolean autoDisconnectFromReflectorOnTxG2Route(
		@NonNull final DSTARGatewayImpl gateway,
		@NonNull final RepeaterControlFlag repeaterFlag,
		@NonNull final String repeaterCallsign
	) {
		if(repeaterFlag == RepeaterControlFlag.NOTHING_NULL) {
			final DSTARRepeater repeater = gateway.getRepeater(repeaterCallsign);
			if(repeater == null) {return false;}

			if(!repeater.isAutoDisconnectFromReflectorOnTxToG2Route()) {return true;}

			final String linkedReflector =
				gateway.getOutgoingLinkedReflectorCallsign(repeater);

			if(
				linkedReflector != null &&
				!DSTARDefines.EmptyLongCallsign.equals(linkedReflector) &&
				!"".equals(linkedReflector)
			) {
				gateway.unlinkReflector(repeater);
			}
		}

		return true;
	}

	protected static void processStatusHeardEntry(
		@NonNull final DSTARGateway gateway,
		final int frameID,
		final PacketType packetType,
		@NonNull final DSTARPacket packet,
		@NonNull final DSTARProtocol protocol,
		@NonNull final ProcessEntry entry
	) {
		StatusFunction.processStatusHeardEntry(
			gateway, frameID, packetType, packet, protocol, entry, null
		);
	}

	protected static void processStatusHeardEntry(
		@NonNull final DSTARGateway gateway,
		final int frameID,
		final PacketType packetType,
		@NonNull final DSTARPacket packet,
		@NonNull final DSTARProtocol protocol,
		@NonNull final ProcessEntry entry,
		@NonNull final DSTARRepeater srcRepeater
	) {
		StatusFunction.processStatusHeardEntry(
			gateway, frameID, packetType, packet, protocol, entry, srcRepeater, false
		);
	}

	protected void processStatusHeardEntryTimeout(
		@NonNull final DSTARGateway gateway,
		final int frameID,
		@NonNull final ProcessEntry entry
	) {
		StatusFunction.processStatusHeardEntry(
			gateway, frameID, null, null, entry.getHeardInfo().getProtocol(), entry, null, true
		);
	}

	public static void processStatusHeardEntry(
		final DSTARGateway gateway,
		final int frameID,
		final PacketType packetType,
		final DSTARPacket packet,
		final DSTARProtocol protocol,
		final ProcessEntry entry,
		final DSTARRepeater repeater,
		final boolean isTimeout
	) {
		StatusFunction.processStatusHeardEntry(
			gateway, frameID, packetType, packet, protocol, entry, repeater, isTimeout
		);
	}
}
