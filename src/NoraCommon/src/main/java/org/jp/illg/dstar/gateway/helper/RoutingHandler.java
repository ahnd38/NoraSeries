package org.jp.illg.dstar.gateway.helper;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.DSTARGatewayImpl;
import org.jp.illg.dstar.gateway.model.ProcessEntry;
import org.jp.illg.dstar.gateway.model.ProcessModes;
import org.jp.illg.dstar.gateway.model.ProcessStates;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.routing.define.RoutingServiceResult;
import org.jp.illg.dstar.routing.model.RepeaterRoutingInfo;
import org.jp.illg.dstar.routing.model.UserRoutingInfo;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RoutingHandler extends GatewayHelperBase {

	private RoutingHandler() {}

	/**
	 * Heard送信が完了したことを通知する
	 *
	 * @param queryID クエリID
	 * @param queryResult クエリ結果
	 */
	public static void completeHeard(
		@NonNull final DSTARGatewayImpl gateway,
		@NonNull final Lock processEntriesLocker,
		@NonNull final Map<Integer, ProcessEntry> processEntries,
		@NonNull UUID queryID,
		@NonNull RoutingServiceResult queryResult
	) {
		processEntriesLocker.lock();
		try {
			for (final Iterator<ProcessEntry> it = processEntries.values().iterator(); it.hasNext();) {
				final ProcessEntry entry = it.next();

				entry.getLocker().lock();
				try {
					if (
						queryID.equals(entry.getRoutingID()) &&
						entry.getProcessState() == ProcessStates.SendHeard
					) {
						// Reflector Route
						if (entry.getProcessMode() == ProcessModes.RepeaterToReflector) {
							entry.setProcessState(ProcessStates.Valid);
						}
						// Heard only
						else if(entry.getProcessMode() == ProcessModes.HeardOnly) {
							entry.setProcessState(ProcessStates.Valid);
						}
						// G2 Route
						else if(entry.getProcessMode() == ProcessModes.RepeaterToG2) {
							if(queryResult != RoutingServiceResult.Success) {
								if(log.isInfoEnabled()) {
									log.info(
										"[Routing System Error(PositionUpdate)] " +
										String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()) +
										"(Request by " + String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()) + ")"
									);
								}

								entry.setProcessState(ProcessStates.Invalid);
							}
							else if(
								!CallSignValidator.isValidAreaRepeaterCallsign(
									entry.getHeaderPacket().getRFHeader().getYourCallsign()
								) &&
								!CallSignValidator.isValidUserCallsign(
									entry.getHeaderPacket().getRFHeader().getYourCallsign()
								)
							){
								entry.setProcessState(ProcessStates.Invalid);
							}
							else if ('/' == entry.getHeaderPacket().getRFHeader().getYourCallsign()[0]) {
								final String repeaterCall =
									DSTARUtils.convertAreaRepeaterCallToRepeaterCall(
										String.valueOf(entry.getHeaderPacket().getRFHeader().getYourCallsign())
									);

								final UUID repeaterQueryID =
									gateway.findRepeater(
										entry.getRepeater(), repeaterCall,
										entry.getHeaderPacket().getRFHeader().clone()
									);

								if(repeaterQueryID != null) {
									entry.setRoutingID(repeaterQueryID);
									entry.setProcessState(ProcessStates.QueryRepeater);
								}
								else {entry.setProcessState(ProcessStates.Invalid);}
							}
							else {
								final UUID userQueryID =
									gateway.findUser(
										entry.getRepeater(),
										String.valueOf(entry.getHeaderPacket().getRFHeader().getYourCallsign()),
										 entry.getHeaderPacket().getRFHeader().clone()
									);

								if(userQueryID != null) {
									entry.setRoutingID(userQueryID);
									entry.setProcessState(ProcessStates.QueryUser);
								}
								else {entry.setProcessState(ProcessStates.Invalid);}
							}
						}
						else {
							entry.setProcessState(ProcessStates.Invalid);
						}

						if(entry.getProcessState() == ProcessStates.Invalid) {
							sendFlagToRepeaterUser(
								gateway,
								entry.getRepeater(),
								String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()),
								RepeaterControlFlag.CANT_REPEAT
							);
						}

						entry.updateActivityTimestamp();
						break;
					}
				}finally {
					entry.getLocker().unlock();
				}
			}
		}finally {
			processEntriesLocker.unlock();
		}
	}

	/**
	 * ユーザークエリの解決を通知する
	 *
	 * @param queryID クエリID
	 * @param queryAnswer クエリ結果
	 */
	public static void completeResolveQueryUser(
		@NonNull final DSTARGatewayImpl gateway,
		@NonNull final Lock processEntriesLocker,
		@NonNull final Map<Integer, ProcessEntry> processEntries,
		@NonNull final UUID queryID,
		@NonNull final UserRoutingInfo queryAnswer
	) {
		processEntriesLocker.lock();
		try {
			for (final ProcessEntry entry : processEntries.values()) {
				entry.getLocker().lock();
				try {
					if (
						queryID.equals(entry.getRoutingID()) &&
						entry.getProcessState() == ProcessStates.QueryUser
					) {
						if(queryAnswer == null) {
							entry.setProcessState(ProcessStates.Invalid);

							continue;
						}

						if (
							queryAnswer.getRoutingResult() == RoutingServiceResult.Success &&
							entry.getHeaderPacket() != null
						) {
							// G2 cross band?
							final DSTARRepeater crossbandDestinationRepeater =
								gateway.getRepeater(queryAnswer.getRepeaterCallsign());
							if(
								crossbandDestinationRepeater != null
							) {
								entry.setProcessState(ProcessStates.Valid);

								final DSTARPacket header = entry.getHeaderPacket();
								header.getRFHeader().setRepeater1Callsign(gateway.getGatewayCallsign().toCharArray());
								header.getRFHeader().setRepeater2Callsign(crossbandDestinationRepeater.getRepeaterCallsign().toCharArray());

								if(entry.getRepeater() != crossbandDestinationRepeater) {
									entry.setProcessMode(ProcessModes.RepeaterToCrossband);

									crossbandDestinationRepeater.writePacket(header);

									writeToReflector(
										gateway, entry, crossbandDestinationRepeater,
										ConnectionDirectionType.INCOMING,
										header
									);

									if(log.isInfoEnabled()) {
										log.info(
											"[G2 X BAND] " +
											"MY:" + String.valueOf(header.getRFHeader().getMyCallsign()) +
											String.valueOf(header.getRFHeader().getMyCallsignAdd()) +
											"/UR:" + String.valueOf(header.getRFHeader().getYourCallsign()) +
											"/RPT2:" + String.valueOf(header.getRFHeader().getRepeater2Callsign()) +
											"/RPT1:" + String.valueOf(header.getRFHeader().getRepeater1Callsign())
										);
									}

									// Send cached voice
									for (final Iterator<DSTARPacket> it = entry.getCachePackets().iterator(); it.hasNext();) {
										final DSTARPacket cachedPacket = it.next();
										it.remove();

										crossbandDestinationRepeater.writePacket(cachedPacket);

										writeToReflector(
											gateway, entry, crossbandDestinationRepeater,
											ConnectionDirectionType.INCOMING,
											cachedPacket
										);
									}
								}
								else {
									entry.setProcessMode(ProcessModes.HeardOnly);

									if(log.isInfoEnabled()) {
										log.info(
											"[G2 LOCAL] " +
											"MY:" + String.valueOf(header.getRFHeader().getMyCallsign()) +
												String.valueOf(header.getRFHeader().getMyCallsignAdd()) +
											"/UR:" + String.valueOf(header.getRFHeader().getYourCallsign()) +
											"/RPT2:" + String.valueOf(header.getRFHeader().getRepeater2Callsign()) +
											"/RPT1:" + String.valueOf(header.getRFHeader().getRepeater1Callsign())
										);
									}
								}

								processStatusHeardEntry(
									gateway, entry.getFrameID(), PacketType.Header, header,
									DSTARProtocol.G123, entry, entry.getRepeater(), false
								);
							}
							// Normal G2 route
							else if(
								CallSignValidator.isValidGatewayCallsign(queryAnswer.getGatewayCallsign()) &&
								CallSignValidator.isValidRepeaterCallsign(queryAnswer.getRepeaterCallsign())
							){
								entry.setProcessState(ProcessStates.Valid);

								final DSTARPacket header = entry.getHeaderPacket();
								entry.setRemoteAddress(queryAnswer.getGatewayAddress());
								header.getRFHeader().setRepeater1Callsign(queryAnswer.getGatewayCallsign().toCharArray());
								header.getRFHeader().setRepeater2Callsign(queryAnswer.getRepeaterCallsign().toCharArray());

								// Send Header
								gateway.writePacketToG123Route(header, entry.getRemoteAddress());

								if(log.isInfoEnabled()) {
									log.info(
										"[G2 OUT] " +
										(entry.isBusyHeader() ? "[BUSY] " : "") +
										"MY:" + String.valueOf(header.getRFHeader().getMyCallsign()) +
										String.valueOf(header.getRFHeader().getMyCallsignAdd()) +
										"/UR:" + String.valueOf(header.getRFHeader().getYourCallsign()) +
										"/RPT2:" + String.valueOf(header.getRFHeader().getRepeater2Callsign()) +
										"/RPT1:" + String.valueOf(header.getRFHeader().getRepeater1Callsign())
//										"/Addr:" + entry.getRemoteAddress()
									);
								}

								// Send cached voice
								if(!entry.isBusyHeader()) {
									for (final Iterator<DSTARPacket> it = entry.getCachePackets().iterator(); it.hasNext();) {
										final DSTARPacket cachedPacket = it.next();

										gateway.writePacketToG123Route(cachedPacket, entry.getRemoteAddress());

										it.remove();
									}
								}

								processStatusHeardEntry(
									gateway, entry.getFrameID(), PacketType.Header, header,
									DSTARProtocol.G123, entry, entry.getRepeater(), false
								);
							}
							else {
								entry.setProcessState(ProcessStates.Invalid);
							}
						}
						else if(queryAnswer.getRoutingResult() == RoutingServiceResult.NotFound) {
							if(log.isInfoEnabled()) {
								log.info(
									"[User Not Found] " + String.valueOf(entry.getHeaderPacket().getRFHeader().getYourCallsign()) +
									"(Request by " + String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()) + ")"
								);
							}

							entry.setProcessState(ProcessStates.Invalid);
						}
						else if(queryAnswer.getRoutingResult() == RoutingServiceResult.Failed) {
							if(log.isInfoEnabled()) {
								log.info(
									"[Routing System Error(UserQuery)] " + String.valueOf(entry.getHeaderPacket().getRFHeader().getYourCallsign()) +
									"(Request by " + String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()) + ")"
								);
							}

							entry.setProcessState(ProcessStates.Invalid);
						}
						else {
							entry.setProcessState(ProcessStates.Invalid);
						}

						if(entry.getProcessState() == ProcessStates.Invalid) {
							sendFlagToRepeaterUser(
								gateway,
								entry.getRepeater(),
								String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()),
								RepeaterControlFlag.CANT_REPEAT
							);
						}

						entry.updateActivityTimestamp();
						break;
					}
				}finally {
					entry.getLocker().unlock();
				}
			}
		}finally {
			processEntriesLocker.unlock();
		}
	}

	/**
	 * レピータクエリの解決を通知する
	 *
	 * @param queryID クエリID
	 * @param queryAnswer クエリ結果
	 */
	public static void completeResolveQueryRepeater(
		@NonNull final DSTARGatewayImpl gateway,
		@NonNull final Lock processEntriesLocker,
		@NonNull final Map<Integer, ProcessEntry> processEntries,
		@NonNull final UUID queryID,
		@NonNull final RepeaterRoutingInfo queryAnswer
	) {
		processEntriesLocker.lock();
		try {
			for (final ProcessEntry entry : processEntries.values()) {
				entry.getLocker().lock();
				try {
					if (
						queryID.equals(entry.getRoutingID()) &&
						entry.getProcessState() == ProcessStates.QueryRepeater
					) {
						if(queryAnswer == null) {
							entry.setProcessState(ProcessStates.Invalid);

							continue;
						}

						if (
							queryAnswer.getRoutingResult() == RoutingServiceResult.Success &&
							entry.getHeaderPacket() != null
						) {
							// G2 cross band?
							final DSTARRepeater crossbandDestinationRepeater =
								gateway.getRepeater(queryAnswer.getRepeaterCallsign());
							if(crossbandDestinationRepeater != null) {
								entry.setProcessState(ProcessStates.Valid);

								final DSTARPacket header = entry.getHeaderPacket();
								header.getRFHeader().setRepeater1Callsign(gateway.getGatewayCallsign().toCharArray());
								header.getRFHeader().setRepeater2Callsign(crossbandDestinationRepeater.getRepeaterCallsign().toCharArray());
								header.getRFHeader().setYourCallsign(DSTARDefines.CQCQCQ.toCharArray());

								if(crossbandDestinationRepeater != entry.getRepeater()) {
									entry.setProcessMode(ProcessModes.RepeaterToCrossband);

									crossbandDestinationRepeater.writePacket(header);

									writeToReflector(
										gateway, entry, crossbandDestinationRepeater,
										ConnectionDirectionType.INCOMING,
										header
									);

									if(log.isInfoEnabled()) {
										log.info(
											"[G2 X BAND] " +
											"MY:" + String.valueOf(header.getRFHeader().getMyCallsign()) +
											String.valueOf(header.getRFHeader().getMyCallsignAdd()) +
											"/UR:" + String.valueOf(header.getRFHeader().getYourCallsign()) +
											"/RPT2:" + String.valueOf(header.getRFHeader().getRepeater2Callsign()) +
											"/RPT1:" + String.valueOf(header.getRFHeader().getRepeater1Callsign())
										);
									}

									// Send cached voice
									for (final Iterator<DSTARPacket> it = entry.getCachePackets().iterator(); it.hasNext();) {
										final DSTARPacket cachedPacket = it.next();
										it.remove();

										crossbandDestinationRepeater.writePacket(cachedPacket);

										writeToReflector(
											gateway, entry, crossbandDestinationRepeater,
											ConnectionDirectionType.INCOMING,
											cachedPacket
										);
									}
								}
								else {
									entry.setProcessMode(ProcessModes.HeardOnly);

									if(log.isInfoEnabled()) {
										log.info(
											"[G2 LOCAL] " +
											"MY:" + String.valueOf(header.getRFHeader().getMyCallsign()) +
											String.valueOf(header.getRFHeader().getMyCallsignAdd()) +
											"/UR:" + String.valueOf(header.getRFHeader().getYourCallsign()) +
											"/RPT2:" + String.valueOf(header.getRFHeader().getRepeater2Callsign()) +
											"/RPT1:" + String.valueOf(header.getRFHeader().getRepeater1Callsign())
										);
									}
								}

								processStatusHeardEntry(
									gateway, entry.getFrameID(), PacketType.Header, header,
									DSTARProtocol.G123, entry, entry.getRepeater(), false
								);
							}
							else if(
								CallSignValidator.isValidGatewayCallsign(queryAnswer.getGatewayCallsign()) &&
								CallSignValidator.isValidRepeaterCallsign(queryAnswer.getRepeaterCallsign())
							){
								entry.setProcessState(ProcessStates.Valid);

								final DSTARPacket header = entry.getHeaderPacket();
								entry.setRemoteAddress(queryAnswer.getGatewayAddress());
								header.getRFHeader().setRepeater1Callsign(queryAnswer.getGatewayCallsign().toCharArray());
								header.getRFHeader().setRepeater2Callsign(queryAnswer.getRepeaterCallsign().toCharArray());
								header.getRFHeader().setYourCallsign(DSTARDefines.CQCQCQ.toCharArray());

								// Send Header
								gateway.writePacketToG123Route(header, entry.getRemoteAddress());

								if(log.isInfoEnabled()) {
									log.info(
										"[G2 OUT] " +
										"MY:" + String.valueOf(header.getRFHeader().getMyCallsign()) +
										String.valueOf(header.getRFHeader().getMyCallsignAdd()) +
										"/UR:" + String.valueOf(header.getRFHeader().getYourCallsign()) +
										"/RPT2:" + String.valueOf(header.getRFHeader().getRepeater2Callsign()) +
										"/RPT1:" + String.valueOf(header.getRFHeader().getRepeater1Callsign())
									);
								}

								// Send cached voice
								for (final Iterator<DSTARPacket> it = entry.getCachePackets().iterator(); it.hasNext();) {
									final DSTARPacket cachedPacket = it.next();
									it.remove();

									gateway.writePacketToG123Route(cachedPacket, entry.getRemoteAddress());
								}

								processStatusHeardEntry(
									gateway, entry.getFrameID(), PacketType.Header, header,
									DSTARProtocol.G123, entry, entry.getRepeater(), false
								);
							}
							else {
								entry.setProcessState(ProcessStates.Invalid);

								sendFlagToRepeaterUser(
									gateway,
									entry.getRepeater(),
									String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()),
									RepeaterControlFlag.CANT_REPEAT
								);
							}
						}
						else if(queryAnswer.getRoutingResult() == RoutingServiceResult.NotFound) {
							if(log.isInfoEnabled()) {
								log.info(
									"[Repeater Not Found] " + String.valueOf(entry.getHeaderPacket().getRFHeader().getYourCallsign()) +
									"(Request by " + String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()) + ")"
								);
							}

							entry.setProcessState(ProcessStates.Invalid);
						}
						else if(queryAnswer.getRoutingResult() == RoutingServiceResult.Failed) {
							if(log.isInfoEnabled()) {
								log.info(
									"[Routing System Error(RepeaterQuery)] " + String.valueOf(entry.getHeaderPacket().getRFHeader().getYourCallsign()) +
									"(Request by " + String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()) + ")"
								);
							}

							entry.setProcessState(ProcessStates.Invalid);
						}
						else {
							entry.setProcessState(ProcessStates.Invalid);
						}

						if(entry.getProcessState() == ProcessStates.Invalid) {
							sendFlagToRepeaterUser(
								gateway,
								entry.getRepeater(),
								String.valueOf(entry.getHeaderPacket().getRFHeader().getMyCallsign()),
								RepeaterControlFlag.CANT_REPEAT
							);
						}

						entry.updateActivityTimestamp();
						break;
					}
				}finally {
					entry.getLocker().unlock();
				}
			}
		}finally {
			processEntriesLocker.unlock();
		}
	}
}
