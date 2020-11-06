package org.jp.illg.dstar.gateway.helper;

import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.model.HeardState;
import org.jp.illg.dstar.gateway.model.ProcessEntry;
import org.jp.illg.dstar.gateway.model.ProcessModes;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.HeardEntryState;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.aprs.APRSMessageDecoder;
import org.jp.illg.dstar.util.aprs.APRSMessageDecoder.APRSMessageDecoderResult;
import org.jp.illg.util.SystemUtil;

import com.annimon.stream.function.Consumer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatusFunction {

	public static enum StatusMode {
		On,
		Update,
		Off,
		;
	}

	private StatusFunction() {}

	public static void processStatusHeardEntry(
		@NonNull final DSTARGateway gateway,
		final int frameID,
		final PacketType packetType,
		final DSTARPacket packet,
		final DSTARProtocol protocol,
		final ProcessEntry entry
	) {
		processStatusHeardEntry(gateway, frameID, packetType, packet, protocol, entry, null);
	}

	public static void processStatusHeardEntry(
		@NonNull final DSTARGateway gateway,
		final int frameID,
		final PacketType packetType,
		final DSTARPacket packet,
		final DSTARProtocol protocol,
		final ProcessEntry entry,
		final DSTARRepeater srcRepeater
	) {
		processStatusHeardEntry(gateway, frameID, packetType, packet, protocol, entry, srcRepeater, false);
	}

	public static void processStatusHeardEntryTimeout(
		@NonNull final DSTARGateway gateway,
		final int frameID,
		final ProcessEntry entry
	) {
		processStatusHeardEntry(gateway, frameID, null, null, entry.getHeardInfo().getProtocol(), entry, null, true);
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
		entry.getLocker().lock();
		try {
			if(isTimeout && entry.getHeardInfo().getState() == HeardState.Update) {
				if(entry.getHeardInfo().isStatusTransmit() && entry.getRoutingService() != null) {
					sendStatus(
						entry.getRoutingService(),
						StatusMode.Off,
						entry.getFrameID(),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
						entry.getHeardInfo().getHeardHeader().getFlags()[0],
						entry.getHeardInfo().getHeardHeader().getFlags()[1],
						entry.getHeardInfo().getHeardHeader().getFlags()[2],
						entry.getHeardInfo().getDestination(),
						entry.getHeardInfo().getShortMessage(),
						entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
						entry.getHeardInfo().getPacketCount(), 0, 0
					);
				}

				if(entry.getHeardInfo().isHeardTransmit()) {
					gateway.addHeardEntry(
						HeardEntryState.End,
						entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
						entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
						entry.getHeardInfo().getShortMessage(),
						entry.getHeardInfo().isLocationAvailable(),
						entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
						entry.getErrorDetector().getPacketCount(),
						entry.getErrorDetector().getPacketDropRate(),
						entry.getErrorDetector().getBitErrorRate()
					);
				}

				entry.getHeardInfo().setState(HeardState.End);
			}
			else if(packetType == PacketType.Header) {
				processStatusHeardEntryHeader(gateway, frameID, packet, protocol, entry, repeater);
			}
			else if(packetType == PacketType.Voice) {
				processStatusHeardEntryVoice(gateway, frameID, packet, protocol, entry, repeater);
			}
		}finally {
			entry.getLocker().unlock();
		}
	}

	private static void processStatusHeardEntryHeader(
		final DSTARGateway gateway,
		final int frameID,
		final DSTARPacket packet,
		final DSTARProtocol protocol,
		final ProcessEntry entry,
		final DSTARRepeater repeater
	) {
		if(!packet.getDVPacket().hasPacketType(PacketType.Header)) {return;}

		if(entry.getHeardInfo().getState() == HeardState.Start) {
			if(entry.getSlowdataDecoder() == null)
				entry.setSlowdataDecoder(new DataSegmentDecoder());
			else
				entry.getSlowdataDecoder().reset();

			switch(entry.getProcessMode()) {
			case RepeaterToReflector:
				if(repeater != null && repeater.getLinkedReflectorCallsign() != null)
					entry.getHeardInfo().setDestination(repeater.getLinkedReflectorCallsign());
				else
					entry.getHeardInfo().setDestination(DSTARDefines.EmptyLongCallsign);

				entry.getHeardInfo().setFrom(
					repeater != null ?
						DSTARUtils.convertRepeaterCallToAreaRepeaterCall(repeater.getRepeaterCallsign()) :
						DSTARDefines.EmptyLongCallsign
				);

				if(repeater != null && repeater.getLinkedReflectorCallsign() != null) {
					gateway.findReflectorByCallsign(repeater.getLinkedReflectorCallsign())
					.ifPresentOrElse(new Consumer<ReflectorHostInfo>() {
						@Override
						public void accept(ReflectorHostInfo t) {
							entry.getHeardInfo().setProtocol(t.getReflectorProtocol());
						}
					}, new Runnable() {
						@Override
						public void run() {
							entry.getHeardInfo().setProtocol(protocol);
						}
					});
				}
				else
					entry.getHeardInfo().setProtocol(protocol);

				break;

			case RepeaterToG2:
				entry.getHeardInfo().setDestination(DSTARDefines.EmptyLongCallsign);

				entry.getHeardInfo().setFrom(
					repeater != null ?
						DSTARUtils.convertRepeaterCallToAreaRepeaterCall(repeater.getRepeaterCallsign()) :
						DSTARDefines.EmptyLongCallsign
				);
				entry.getHeardInfo().setProtocol(DSTARProtocol.G123);
				break;

			case RepeaterToCrossband:
				entry.getHeardInfo().setDestination(
					String.valueOf(entry.getHeaderPacket().getRFHeader().getRepeater2Callsign())
				);

				entry.getHeardInfo().setFrom(
					repeater != null ?
						DSTARUtils.convertRepeaterCallToAreaRepeaterCall(repeater.getRepeaterCallsign()) :
						DSTARDefines.EmptyLongCallsign
				);
				entry.getHeardInfo().setProtocol(DSTARProtocol.Internal);
				break;

			case ReflectorToRepeater:
				entry.getHeardInfo().setDestination(
					DSTARUtils.convertRepeaterCallToAreaRepeaterCall(
						String.valueOf(entry.getHeaderPacket().getRFHeader().getRepeater2Callsign())
					)
				);
/*
				entry.getHeardInfo().setFrom(
					repeater != null ?
						repeater.getLinkedReflectorCallsign():
						DStarDefines.EmptyLongCallsign
				);
*/
				entry.getHeardInfo().setFrom(
					DSTARUtils.convertRepeaterCallToAreaRepeaterCall(
						String.valueOf(packet.getRFHeader().getSourceRepeater2Callsign())
					)
				);

				entry.getHeardInfo().setProtocol(protocol);
				break;

			case G2ToRepeater:
				entry.getHeardInfo().setDestination(
					DSTARUtils.convertRepeaterCallToAreaRepeaterCall(
						String.valueOf(entry.getHeaderPacket().getRFHeader().getRepeater2Callsign())
					)
				);
				entry.getHeardInfo().setFrom(
					String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign())
				);

				entry.getHeardInfo().setProtocol(DSTARProtocol.G123);
				break;

			default:
				entry.getHeardInfo().setDestination(DSTARDefines.EmptyLongCallsign);
				entry.getHeardInfo().setFrom(DSTARDefines.EmptyLongCallsign);
				entry.getHeardInfo().setProtocol(protocol);
				break;
			}

			// Status transmit for Routing service
			if(protocol == DSTARProtocol.Internal || protocol == DSTARProtocol.Homeblew) {
				entry.getHeardInfo().setStatusTransmit(
					entry.getRoutingService() != null &&
					(
						entry.getProcessMode() == ProcessModes.RepeaterToReflector ||
						entry.getProcessMode() == ProcessModes.RepeaterToG2 ||
						entry.getProcessMode() == ProcessModes.HeardOnly ||
						entry.getProcessMode() == ProcessModes.RepeaterToCrossband
//						entry.getProcessMode() == ProcessModes.Control
					) &&
					(
						entry.getHeaderPacket().getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL) ||
						entry.getHeaderPacket().getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.AUTO_REPLY)
					)
				);

				if(entry.getHeardInfo().isStatusTransmit() && entry.getRoutingService() != null) {
					sendStatus(
						entry.getRoutingService(),
						StatusMode.On,
						frameID,
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
						entry.getHeardInfo().getHeardHeader().getFlags()[0],
						entry.getHeardInfo().getHeardHeader().getFlags()[1],
						entry.getHeardInfo().getHeardHeader().getFlags()[2],
						entry.getHeardInfo().getDestination(),
						DSTARDefines.EmptyDvShortMessage,
						0, 0,
						entry.getHeardInfo().getPacketCount(), 0, 0
					);
				}
			}

			if(
				entry.getProcessMode() == ProcessModes.Control ||
				entry.getProcessMode() == ProcessModes.HeardOnly ||
				entry.getProcessMode() == ProcessModes.RepeaterToG2 ||
				entry.getProcessMode() == ProcessModes.RepeaterToReflector
			) {
				entry.getHeardInfo().setDirection(ConnectionDirectionType.OUTGOING);
			}
			else if(
				entry.getProcessMode() == ProcessModes.ReflectorToRepeater ||
				entry.getProcessMode() == ProcessModes.G2ToRepeater
			) {
				entry.getHeardInfo().setDirection(ConnectionDirectionType.INCOMING);
			}
			else {
				entry.getHeardInfo().setDirection(ConnectionDirectionType.Unknown);
			}

			entry.getHeardInfo().setHeardTransmit(
				entry.getHeaderPacket().getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL) ||
				entry.getHeaderPacket().getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.AUTO_REPLY)
			);

			if(entry.getHeardInfo().isHeardTransmit()) {
				addHeardEntry(
					gateway,
					HeardEntryState.Start,
					entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
					entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
					DSTARDefines.EmptyDvShortMessage,
					false, 0, 0,
					entry.getErrorDetector().getPacketCount(),
					entry.getErrorDetector().getPacketDropRate(),
					entry.getErrorDetector().getBitErrorRate()
				);
			}

			entry.getHeardInfo().setState(HeardState.Update);
		}
		else if(entry.getHeardInfo().getState() == HeardState.Update) {

			if(entry.getProcessMode() == ProcessModes.RepeaterToG2) {
				entry.getHeardInfo().setDestination(
					String.valueOf(entry.getHeaderPacket().getRFHeader().getRepeater2Callsign())
				);
			}
			else if(entry.getProcessMode() == ProcessModes.RepeaterToCrossband) {
				entry.getHeardInfo().setDestination(
					String.valueOf(entry.getHeaderPacket().getRFHeader().getRepeater2Callsign())
				);
			}

			if(entry.getHeardInfo().isStatusTransmit() && entry.getRoutingService() != null) {
				sendStatus(
					entry.getRoutingService(),
					StatusMode.Update,
					frameID,
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
					entry.getHeardInfo().getHeardHeader().getFlags()[0],
					entry.getHeardInfo().getHeardHeader().getFlags()[1],
					entry.getHeardInfo().getHeardHeader().getFlags()[2],
					entry.getHeardInfo().getDestination(),
					entry.getHeardInfo().getShortMessage(),
					entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
					entry.getHeardInfo().getPacketCount(), 0, 0
				);
			}

			if(entry.getHeardInfo().isHeardTransmit()) {
				addHeardEntry(
					gateway,
					HeardEntryState.Update,
					entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
					String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
					entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
					entry.getHeardInfo().getShortMessage(),
					entry.getHeardInfo().isLocationAvailable(),
					entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
					entry.getErrorDetector().getPacketCount(),
					entry.getErrorDetector().getPacketDropRate(),
					entry.getErrorDetector().getBitErrorRate()
				);
			}
		}

	}

	private static void processStatusHeardEntryVoice(
		final DSTARGateway gateway,
		final int frameID,
		final DSTARPacket packet,
		final DSTARProtocol protocol,
		final ProcessEntry entry,
		final DSTARRepeater repeater
	) {
		if(!packet.getDVPacket().hasPacketType(PacketType.Voice)) {return;}

		if(entry.getHeardInfo().getState() == HeardState.Update) {
			entry.getHeardInfo().setPacketCount(entry.getHeardInfo().getPacketCount() + 1);

			if(entry.getSlowdataDecoder() != null) {
				final DataSegmentDecoderResult decoderResult =
					entry.getSlowdataDecoder().decode(packet.getDVData().getDataSegment());
				switch(decoderResult) {
				case ShortMessage:
					final String decodedShortMessage = entry.getSlowdataDecoder().getShortMessageString();
					if(!decodedShortMessage.equals(entry.getHeardInfo().getShortMessage())) {
						entry.getHeardInfo().setStatusChanged(true);
					}
					entry.getHeardInfo().setShortMessage(decodedShortMessage);
					break;

				case APRS:
					final APRSMessageDecoderResult dprsResult =
						APRSMessageDecoder.decodeDPRS(entry.getSlowdataDecoder().getAprsMessage());
					if(dprsResult != null) {
						if(
							entry.getHeardInfo().getLatitude() != dprsResult.getLatitude() ||
							entry.getHeardInfo().getLongitude() != dprsResult.getLongitude()
						) {
							entry.getHeardInfo().setStatusChanged(true);
						}
						entry.getHeardInfo().setLatitude(dprsResult.getLatitude());
						entry.getHeardInfo().setLongitude(dprsResult.getLongitude());
						entry.getHeardInfo().setLocationAvailable(true);
					}
					break;

				default:
					break;
				}
			}


			if(
				packet.getBackBoneHeader().getSequenceNumber() == 0x0 &&
				entry.getHeardInfo().isStatusChanged()
			) {
				entry.getHeardInfo().setStatusChanged(false);

				if(entry.getHeardInfo().isStatusTransmit() && entry.getRoutingService() != null) {
					sendStatus(
						entry.getRoutingService(),
						StatusMode.Update,
						frameID,
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
						entry.getHeardInfo().getHeardHeader().getFlags()[0],
						entry.getHeardInfo().getHeardHeader().getFlags()[1],
						entry.getHeardInfo().getHeardHeader().getFlags()[2],
						entry.getHeardInfo().getDestination(),
						entry.getHeardInfo().getShortMessage(),
						entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
						entry.getHeardInfo().getPacketCount(), 0, 0
					);
				}
			}

			if(
				(
					SystemUtil.getAvailableProcessors() >= 2 &&
					entry.getHeardInfo().getHeardIntervalTimer().isTimeout(100, TimeUnit.MILLISECONDS)
				) ||
				entry.getHeardInfo().getHeardIntervalTimer().isTimeout(800, TimeUnit.MILLISECONDS)
			) {
				entry.getHeardInfo().getHeardIntervalTimer().updateTimestamp();

				if(entry.getHeardInfo().isHeardTransmit()) {
					addHeardEntry(
						gateway,
						HeardEntryState.Update,
						entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
						entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
						entry.getHeardInfo().getShortMessage(),
						entry.getHeardInfo().isLocationAvailable(),
						entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
						entry.getErrorDetector().getPacketCount(),
						entry.getErrorDetector().getPacketDropRate(),
						entry.getErrorDetector().getBitErrorRate()
					);
				}
			}



			if (packet.isLastFrame()) {
				if(entry.getHeardInfo().isStatusTransmit() && entry.getRoutingService() != null) {
					sendStatus(
						entry.getRoutingService(),
						StatusMode.Off,
						frameID,
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
						entry.getHeardInfo().getHeardHeader().getFlags()[0],
						entry.getHeardInfo().getHeardHeader().getFlags()[1],
						entry.getHeardInfo().getHeardHeader().getFlags()[2],
						entry.getHeardInfo().getDestination(),
						entry.getHeardInfo().getShortMessage(),
						entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
						entry.getHeardInfo().getPacketCount(), 0, 0
					);
				}

				if(entry.getHeardInfo().isHeardTransmit()) {
					addHeardEntry(
						gateway,
						HeardEntryState.End,
						entry.getHeardInfo().getProtocol(), entry.getHeardInfo().getDirection(),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getYourCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater1Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getRepeater2Callsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsign()),
						String.valueOf(entry.getHeardInfo().getHeardHeader().getMyCallsignAdd()),
						entry.getHeardInfo().getDestination(), entry.getHeardInfo().getFrom(),
						entry.getHeardInfo().getShortMessage(),
						entry.getHeardInfo().isLocationAvailable(),
						entry.getHeardInfo().getLatitude(), entry.getHeardInfo().getLongitude(),
						entry.getErrorDetector().getPacketCount(),
						entry.getErrorDetector().getPacketDropRate(),
						entry.getErrorDetector().getBitErrorRate()
					);
				}

				entry.getHeardInfo().setState(HeardState.End);
			}
		}
	}

	private static boolean sendStatus(
		final RoutingService routingService,
		final StatusMode mode,
		final int frameID,
		final String myCallsignLong, final String myCallsignShort,
		final String yourCallsign,
		final String repeater1Callsign, final String repeater2Callsign,
		final byte flag0, final byte flag1, final byte flag2,
		final String destination,
		final String shortMessage,
		final double latitude, final double longitude,
		final int packetCount,
		final int silentFrames, final int bitErrors
	) {
		if(log.isDebugEnabled()) {
			log.debug(
				"Sending status..." +
				"Mode=" + mode +
				"/FrameID=" + String.format("%04X", frameID) +
				"/MY=" + myCallsignLong + ' ' + myCallsignShort +
				"/UR=" + yourCallsign +
				"/RPT1=" + repeater1Callsign +
				"/RPT2=" + repeater2Callsign +
				"/Flags=" + String.format("%02X %02X %02X", flag0, flag1, flag2) +
				"/Destination=" + destination +
				"/ShortMessage=" + shortMessage +
				"/Latitude=" + latitude +
				"/Longitude=" + longitude +
				"/PacketCount=" + packetCount +
				"/SilentFrames=" + silentFrames +
				"/BitErrors=" + bitErrors
			);
		}

		switch(mode) {
		case On:
			return routingService.sendStatusAtPTTOn(
				frameID,
				myCallsignLong, myCallsignShort,
				yourCallsign,
				repeater1Callsign, repeater2Callsign,
				flag0, flag1, flag2,
				destination,
				shortMessage,
				latitude, longitude
			);

		case Update:
			return routingService.sendStatusUpdate(
				frameID,
				myCallsignLong, myCallsignShort,
				yourCallsign,
				repeater1Callsign, repeater2Callsign,
				flag0, flag1, flag2,
				destination,
				shortMessage,
				latitude, longitude
			);

		case Off:
			return routingService.sendStatusAtPTTOff(
				frameID,
				myCallsignLong, myCallsignShort,
				yourCallsign,
				repeater1Callsign, repeater2Callsign,
				flag0, flag1, flag2,
				destination,
				shortMessage,
				latitude, longitude,
				packetCount, silentFrames, bitErrors
			);

		default:
			throw new RuntimeException();
		}
	}

	private static boolean addHeardEntry(
		final DSTARGateway gateway,
		final HeardEntryState state,
		final DSTARProtocol protocol,
		final ConnectionDirectionType dir,
		final String yourCallsign,
		final String repeater1Callsign,
		final String repeater2Callsign,
		final String myCallsignLong,
		final String myCallsignShort,
		final String destination,
		final String from,
		final String shortMessage,
		final boolean isLocationAvailable,
		final double latitude, final double longitude,
		final int packetCount,
		final double packetDropRate,
		final double bitErrorRate
	) {
		if(log.isTraceEnabled()) {
			log.trace(
				"Adding heard entry..." +
				"State=" + state +
				"/Dir=" + dir +
				"/UR=" + yourCallsign +
				"/RPT1=" + repeater1Callsign +
				"/RPT2=" + repeater2Callsign +
				"/MY=" + myCallsignLong + ' ' + myCallsignShort +
				"/Destination=" + destination +
				"/From=" + from +
				"/ShortMessage=" + shortMessage +
				"/isLocationAvailable=" + isLocationAvailable +
				"/Latitude=" + latitude +
				"/Longitude=" + longitude
			);
		}

		return gateway.addHeardEntry(
			state,
			protocol,
			dir,
			yourCallsign,
			repeater1Callsign, repeater2Callsign,
			myCallsignLong, myCallsignShort,
			destination, from,
			shortMessage,
			isLocationAvailable,
			latitude, longitude,
			packetCount,
			packetDropRate,
			bitErrorRate
		);
	}
}
