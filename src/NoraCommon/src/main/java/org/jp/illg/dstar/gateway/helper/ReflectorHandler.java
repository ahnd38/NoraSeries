package org.jp.illg.dstar.gateway.helper;

import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.helper.model.GatewayHelperProperties;
import org.jp.illg.dstar.gateway.model.ProcessEntry;
import org.jp.illg.dstar.gateway.model.ProcessModes;
import org.jp.illg.dstar.gateway.model.ProcessStates;
import org.jp.illg.dstar.gateway.tool.announce.AnnounceTool;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.util.CallSignValidator;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorHandler extends GatewayHelperBase {

	private ReflectorHandler() {}

	/**
	 * リフレクターにリンクした事を通知する
	 *
	 * @param repeaterCallsign レピータコールサイン
	 * @param reflectorCallsign リフレクターコールサイン
	 */
	public static void notifyLinkReflector(
		@NonNull final GatewayHelperProperties properties,
		@NonNull final DSTARGateway gateway,
		@NonNull final String repeaterCallsign,
		@NonNull final String reflectorCallsign,
		@NonNull final AnnounceTool announceTool
	) {
		final DSTARRepeater repeater = gateway.getRepeater(repeaterCallsign);
		if(repeater == null) {return;}

		repeater.setLinkedReflectorCallsign(reflectorCallsign);

		if(log.isInfoEnabled())
			log.info("[Reflector Link Established] REF:" + reflectorCallsign + "/RPT:" + repeaterCallsign);

		announceTool.announceReflectorConnected(repeater, properties.getAnnounceCharactor(), reflectorCallsign);

		final WebRemoteControlService webRemoteControlService = gateway.getWebRemoteControlService();
		if(webRemoteControlService != null) {
			gateway.getWebRemoteControlService()
				.notifyRepeaterStatusChanged(repeater.getWebRemoteControlHandler());
		}
	}

	/**
	 * リフレクターから切断した事を通知する
	 *
	 * @param repeaterCallsign レピータコールサイン
	 * @param reflectorCallsign 接続されていたリフレクターコールサイン
	 */
	public static void notifyUnlinkReflector(
		@NonNull final GatewayHelperProperties properties,
		@NonNull final DSTARGateway gateway,
		@NonNull final String repeaterCallsign,
		@NonNull final String reflectorCallsign,
		@NonNull final AnnounceTool announceTool
	) {
		final DSTARRepeater repeater = gateway.getRepeater(repeaterCallsign);
		if(repeater == null) {return;}

		repeater.setLinkedReflectorCallsign(DSTARDefines.EmptyLongCallsign);

		if(log.isInfoEnabled())
			log.info("[Reflector Unlinked] REF:" + reflectorCallsign + "/RPT:" + repeaterCallsign);

		announceTool.announceReflectorDisconnected(repeater, properties.getAnnounceCharactor(), reflectorCallsign);

		final WebRemoteControlService webRemoteControlService = gateway.getWebRemoteControlService();
		if(webRemoteControlService != null) {
			gateway.getWebRemoteControlService()
				.notifyRepeaterStatusChanged(repeater.getWebRemoteControlHandler());
		}
	}

	/**
	 * リフレクターを通信エラーが発生し、切断されたことを通知する
	 *
	 * @param repeaterCallsign レピータコールサイン
	 * @param reflectorCallsign リフレクターコールサイン
	 */
	public static void notifyLinkFailedReflector(
		@NonNull final GatewayHelperProperties properties,
		@NonNull final DSTARGateway gateway,
		@NonNull final String repeaterCallsign,
		@NonNull final String reflectorCallsign,
		@NonNull final AnnounceTool announceTool
	) {
		final DSTARRepeater repeater = gateway.getRepeater(repeaterCallsign);
		if(repeater == null) {return;}

		repeater.setLinkedReflectorCallsign(DSTARDefines.EmptyLongCallsign);

		if(log.isWarnEnabled())
			log.warn("[Reflector Link Failed] REF:" + reflectorCallsign + "/RPT:" + repeaterCallsign);

		announceTool.announceReflectorConnectionError(repeater, properties.getAnnounceCharactor(), reflectorCallsign);

		final WebRemoteControlService webRemoteControlService = gateway.getWebRemoteControlService();
		if(webRemoteControlService != null) {
			gateway.getWebRemoteControlService()
				.notifyRepeaterStatusChanged(repeater.getWebRemoteControlHandler());
		}
	}

	public static void processInputPacketFromReflector(
		@NonNull final DSTARGateway gateway,
		@NonNull final Lock processEntriesLocker,
		@NonNull final Map<Integer, ProcessEntry> processEntries,
		@NonNull final DSTARPacket packet
	) {
		if(log.isTraceEnabled())
			log.trace("Input packet from reflector.\n" + packet.toString(4));

		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		processEntriesLocker.lock();
		try {
			ProcessEntry entry = processEntries.get(frameID);

			if (
				entry == null &&
				packet.getPacketType() == DSTARPacketType.DV &&
				packet.getDVPacket().hasPacketType(PacketType.Header)
			) {
				if (!packet.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL)) {
					if(log.isInfoEnabled())
						log.info("Reject unsupported reflector packet.\n" + packet.toString());

					return;
				}

				if (
					!CallSignValidator.isValidUserCallsign(packet.getRFHeader().getMyCallsign()) ||
					!CallSignValidator.isValidGatewayCallsign(packet.getRFHeader().getRepeater1Callsign()) ||
					!CallSignValidator.isValidRepeaterCallsign(packet.getRFHeader().getRepeater2Callsign()) ||
					!(
						CallSignValidator.isValidUserCallsign(packet.getRFHeader().getYourCallsign()) ||
						CallSignValidator.isValidCQCQCQ(packet.getRFHeader().getYourCallsign())
					)
				) {
					if(log.isInfoEnabled())
						log.info("Reject unknown reflector packet.\n" + packet.toString());

					return;
				}

				final DSTARRepeater repeater =
					gateway.getRepeater(String.valueOf(packet.getRFHeader().getRepeater2Callsign()));
				if (repeater != null) {
					entry = new ProcessEntry(frameID, ProcessModes.ReflectorToRepeater, repeater);
					entry.setProcessState(ProcessStates.Valid);
				} else {
					entry = new ProcessEntry(frameID, ProcessModes.ReflectorToRepeater);
					entry.setProcessState(ProcessStates.Invalid);

					if(log.isInfoEnabled())
						log.info("Unknown destination packet received from reflector and could not distribute packet to repeater...\n" + packet.toString());
				}

				entry.setHeaderPacket(packet);

				entry.getHeardInfo().setHeardHeader(packet.getRFHeader().clone());

				if(log.isDebugEnabled())
					log.debug("Process entry regist by reflector packet.\n" + entry.toString(4));

				processEntries.put(frameID, entry);

				processStatusHeardEntry(
					gateway, frameID, PacketType.Header, packet, packet.getProtocol(), entry, repeater
				);

				if(log.isInfoEnabled()) {
					log.info(
						"[Reflector IN] " +
						(entry.isBusyHeader() ? "[BUSY] " : "") +
						"MY:" + String.valueOf(packet.getRFHeader().getMyCallsign()) + String.valueOf(packet.getRFHeader().getMyCallsignAdd()) +
						"/UR:" + String.valueOf(packet.getRFHeader().getYourCallsign()) +
						"/RPT2:" + String.valueOf(packet.getRFHeader().getRepeater2Callsign()) +
						"/RPT1:" + String.valueOf(packet.getRFHeader().getRepeater1Callsign()) +
						"/From:" + String.valueOf(packet.getRFHeader().getSourceRepeater2Callsign())
					);
				}

			} else if (
				entry != null &&
				packet.getPacketType() == DSTARPacketType.DV &&
				packet.getDVPacket().hasPacketType(PacketType.Voice)
			) {

				entry.getErrorDetector().update(packet.getDVPacket());

				processStatusHeardEntry(gateway, frameID, PacketType.Voice, packet, packet.getProtocol(), entry);
			}
			else {
				return;
			}

			entry.getLocker().lock();
			try {
				if (
					entry.getProcessMode() == ProcessModes.ReflectorToRepeater &&
					entry.getProcessState() == ProcessStates.Valid
				) {
					final DSTARRepeater repeater = entry.getRepeater();
					if (repeater != null) {
						repeater.writePacket(packet);

						if(log.isTraceEnabled()) {log.trace(
							"Write to repeater " + repeater.getRepeaterCallsign() +
							" from reflector\n" + packet.toString(4)
						);}

						if(
							packet.getDVPacket().hasPacketType(PacketType.Voice) &&
							packet.getBackBoneHeader().isMaxSequence() &&
							!packet.isLastFrame()
						) {
							repeater.writePacket(entry.getHeaderPacket());
						}

						writeToReflector(
							gateway, entry, repeater,
							ConnectionDirectionType.INCOMING,
							packet
						);

						if(
							packet.getDVPacket().hasPacketType(PacketType.Voice) &&
							packet.getBackBoneHeader().isMaxSequence() &&
							!packet.isLastFrame()
						) {
							writeToReflector(
								gateway, entry, repeater,
								ConnectionDirectionType.INCOMING,
								entry.getHeaderPacket()
							);
						}
					}
				}

				if (packet.getDVPacket().hasPacketType(PacketType.Voice) && packet.isLastFrame()) {
					if(log.isDebugEnabled())
						log.debug("Process entry remove by reflector packet.\n" + entry.toString(4));

					processEntries.remove(frameID);
				}
				else {
					entry.updateActivityTimestamp();
				}
			}finally {
				entry.getLocker().unlock();
			}

		} finally {
			processEntriesLocker.unlock();
		}
	}
}
