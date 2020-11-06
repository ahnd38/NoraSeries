package org.jp.illg.dstar.gateway.helper;

import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.jp.illg.dstar.gateway.model.ProcessEntry;
import org.jp.illg.dstar.gateway.model.ProcessModes;
import org.jp.illg.dstar.gateway.model.ProcessStates;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.util.CallSignValidator;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class G123Handler extends GatewayHelperBase {

	private static final String logTag = G123Handler.class.getSimpleName() + " : ";

	private G123Handler() {}

	public static void processInputPacketFromG123(
		@NonNull final DSTARGateway gateway,
		@NonNull final Lock processEntriesLocker,
		@NonNull final Map<Integer, ProcessEntry> processEntries,
		@NonNull final DSTARPacket packet
	) {
		if(log.isTraceEnabled())
			log.trace(logTag + "Input packet from G123 route.\n" + packet.toString(4));

		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		processEntriesLocker.lock();
		try {
			ProcessEntry entry = processEntries.get(frameID);

			if (
				entry == null &&
				packet.getPacketType() == DSTARPacketType.DV &&
				packet.getDVPacket().hasPacketType(PacketType.Header)
			) {

				if (
					!packet.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.CANT_REPEAT) &&
					!packet.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.AUTO_REPLY)
				) {
					if(log.isInfoEnabled())
						log.info("Reject unsupported G2 packet.\n" + packet.toString());

					return;
				}
				else if (
					!CallSignValidator.isValidUserCallsign(packet.getRFHeader().getMyCallsign()) ||
					!CallSignValidator.isValidGatewayCallsign(packet.getRFHeader().getRepeater1Callsign()) ||
					!CallSignValidator.isValidRepeaterCallsign(packet.getRFHeader().getRepeater2Callsign()) ||
					!(
						CallSignValidator.isValidUserCallsign(packet.getRFHeader().getYourCallsign()) ||
						CallSignValidator.isValidCQCQCQ(packet.getRFHeader().getYourCallsign())
					)
				) {
					if(log.isInfoEnabled())
						log.info("Reject unknown G2 packet.\n" + packet.toString());

					return;
				}

				final DSTARRepeater repeater =
					gateway.getRepeater(String.valueOf(packet.getRFHeader().getRepeater2Callsign()));
				if (repeater != null) {
					entry = new ProcessEntry(frameID, ProcessModes.G2ToRepeater, repeater);
					entry.setProcessState(ProcessStates.Valid);
				} else {
					entry = new ProcessEntry(frameID, ProcessModes.G2ToRepeater);
					entry.setProcessState(ProcessStates.Invalid);

					if(log.isInfoEnabled())
						log.info("Unknown destination packet received and could not distribute packet to repeater...\n" + packet.toString());
				}

				entry.setHeaderPacket(packet);

				entry.getHeardInfo().setHeardHeader(packet.getRFHeader().clone());

				if(log.isDebugEnabled())
					log.debug("Process entry regist by G123 packet.\n" + entry.toString(4));

				processEntries.put(frameID, entry);

				processStatusHeardEntry(gateway, frameID, PacketType.Header, packet, DSTARProtocol.G123, entry, repeater);

				if(log.isInfoEnabled()) {
					log.info(
						"[G2 IN] " +
						(entry.isBusyHeader() ? "[BUSY] " : "") +
						"MY:" + String.valueOf(packet.getRFHeader().getMyCallsign()) + String.valueOf(packet.getRFHeader().getMyCallsignAdd()) +
						"/UR:" + String.valueOf(packet.getRFHeader().getYourCallsign()) +
						"/RPT2:" + String.valueOf(packet.getRFHeader().getRepeater2Callsign()) +
						"/RPT1:" + String.valueOf(packet.getRFHeader().getRepeater1Callsign())
					);
				}

			} else if (
				entry != null &&
				packet.getPacketType() == DSTARPacketType.DV &&
				packet.getDVPacket().hasPacketType(PacketType.Voice)
			) {

				entry.getErrorDetector().update(packet.getDVPacket());

				processStatusHeardEntry(gateway, frameID, PacketType.Voice, packet, DSTARProtocol.G123, entry);
			}
			else if(entry == null){
				if(log.isDebugEnabled())
					log.debug(logTag + "Dispose G123 route packet...\n" + packet.toString(4));

				return;
			}
			else {
				return;
			}

			final boolean isWriteHeader =
				packet.getDVPacket().hasPacketType(PacketType.Voice) &&
				packet.getBackBoneHeader().isMaxSequence() &&
				!packet.isLastFrame();

			entry.getLocker().lock();
			try {
				if (
					entry.getProcessMode() == ProcessModes.G2ToRepeater &&
					entry.getProcessState() == ProcessStates.Valid
				) {
					final DSTARRepeater repeater = entry.getRepeater();
					if (repeater != null) {
						repeater.writePacket(packet);

						if(isWriteHeader)
							repeater.writePacket(entry.getHeaderPacket());

						writeToReflector(
							gateway, entry, repeater,
							ConnectionDirectionType.INCOMING,
							packet
						);

						if(isWriteHeader) {
							writeToReflector(
								gateway, entry, repeater,
								ConnectionDirectionType.INCOMING,
								entry.getHeaderPacket()
							);
						}
					}
				}

				if (
					packet.getDVPacket().hasPacketType(PacketType.Voice) &&
					packet.isLastFrame()
				) {
					if(log.isDebugEnabled())
						log.debug("Process entry remove by G123 packet.\n" + entry.toString(4));

					processEntries.remove(frameID);
				}
				else {
					entry.updateActivityTimestamp();
				}
			}finally {
				entry.getLocker().unlock();
			}

		}finally {
			processEntriesLocker.unlock();
		}

	}

}
