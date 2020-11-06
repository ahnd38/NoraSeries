package org.jp.illg.dstar.gateway.helper;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.DSTARGatewayImpl;
import org.jp.illg.dstar.gateway.helper.model.GatewayHelperProperties;
import org.jp.illg.dstar.gateway.model.ProcessEntry;
import org.jp.illg.dstar.gateway.model.ProcessModes;
import org.jp.illg.dstar.gateway.model.ProcessStates;
import org.jp.illg.dstar.gateway.tool.announce.AnnounceTool;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterHandler extends GatewayHelperBase {

	private static final String logTag = RepeaterHandler.class.getSimpleName() + " : ";

	private static final Pattern controlCommandPattern =
		Pattern.compile("^((([ ]|[_]){4}[G][2][R][A-Z])|(([ ]|[_]){7}([D]|[I]|[R]))|(([ ]|[_]){2}[R][L][M][A][C]([E]|[D])))$");

	private static final Pattern reflectorLinkPattern =
		Pattern.compile(
			"^(((([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z ]*[A-FH-RT-Z][L])|"
			+ "((([X][R][F])|([X][L][X])|([D][C][S])|([R][E][F]))[0-9]{3}[A-Z][L ]))$"
		);

	private static final Pattern reflectorCommandPattern =
		Pattern.compile("^([ ]{7}([E]|[U]|[I]))$");


	private RepeaterHandler() {
		super();
	}

	/**
	 * レピータからのパケットを処理する
	 */
	public static void processInputPacketFromRepeaters(
		@NonNull final GatewayHelperProperties properties,
		@NonNull final DSTARGatewayImpl gateway,
		@NonNull final Lock processEntriesLocker,
		@NonNull final Map<Integer, ProcessEntry> processEntries,
		@NonNull final AnnounceTool announceTool
	) {
		final List<DSTARRepeater> repeaters = gateway.getRepeaters();

		for (final DSTARRepeater repeater : repeaters) {
			while (repeater.hasReadPacket()) {
				final DSTARPacket packet = repeater.readPacket();
				if (packet == null) {continue;}

				if(log.isTraceEnabled()) {
					log.trace(
						logTag +
						"Input packet from repeater " + repeater.getRepeaterCallsign() + ".\n" +
						packet.toString(4)
					);
				}

				if(packet.getPacketType() != DSTARPacketType.DV) {
					continue;
				}

				final int frameID = packet.getBackBoneHeader().getFrameIDNumber();
				if(frameID <= 0x0) {continue;}

				processEntriesLocker.lock();
				try {
					ProcessEntry entry = processEntries.get(frameID);

					/*
					 * Header packet
					 */
					if (
						entry == null &&
						packet.getDVPacket().hasPacketType(PacketType.Header)
					) {
						//規定外の文字入力を置換
						packet.getRFHeader().replaceCallsignsIllegalCharToSpace();

						//ヘッダチェック
						if(!checkHeader(gateway, packet, repeaters)) {continue;}

						gateway.setLastHeardCallsign(
							String.valueOf(packet.getRFHeader().getMyCallsign())
						);

						final Header heardHeader = packet.getRFHeader().clone();

						//ルート判断
						if(
							(entry = processControlCommand(
								properties, gateway, repeater, packet, heardHeader, announceTool)
							) == null &&
							(entry = processCrossbandRoute(gateway, repeater, packet, heardHeader)) == null &&
							(entry = processReflectorRoute(gateway, packet, repeater, heardHeader)) == null &&
							(entry = processG123Route(
								properties, gateway, repeater, packet, heardHeader,
								processEntriesLocker, processEntries, announceTool
							)) == null &&
							(entry = processHeardOnlyRoute(gateway, repeater, packet, heardHeader)) == null &&
							(entry = processUnknownRoute(gateway, repeater, packet, heardHeader)) == null
						) {
							if(log.isWarnEnabled())
								log.warn("Could not routing unknown repeater packet...\n" + packet.toString(4));

							continue;
						}
						else if(
							entry.getProcessMode() == ProcessModes.RepeaterToReflector ||
							entry.getProcessMode() == ProcessModes.RepeaterToG2 ||
							entry.getProcessMode() == ProcessModes.HeardOnly
						) {
							if(
								!repeater.isUseRoutingService() &&
								(
									entry.getProcessMode() == ProcessModes.RepeaterToG2 ||
									entry.getProcessMode() == ProcessModes.HeardOnly
								)
							) {
								if(log.isWarnEnabled()) {
									log.warn(
										"Repeater " + repeater.getRepeaterCallsign() +
										" can not use routing service, because useRoutingService = false."
									);
								}
							}

							// リフレクタールートでリンクされたリフレクターがJARLLinkであれば
							// テーブル書き換えを強制する
							boolean forceSendHeardJARLLink = false;
							if(entry.getProcessMode() == ProcessModes.RepeaterToReflector) {
								final ReflectorCommunicationService service =
									gateway.getReflectorLinkManager()
										.getOutgoingLinkedReflectorCommunicationService(repeater);

								forceSendHeardJARLLink =
									service != null ?
										service.getProcessorType() == ReflectorProtocolProcessorTypes.JARLLink : false;
							}

							// リフレクタルート以外では、Heardを送信する
							// リフレクタルートでは、Heard有効時のみHeardを送信する
							if(
								repeater.getRoutingService() != null &&
								repeater.isUseRoutingService() &&
								(
									entry.getProcessMode() != ProcessModes.RepeaterToReflector ||
									!properties.isDisableHeardAtReflector() ||
									(entry.getProcessMode() == ProcessModes.RepeaterToReflector && forceSendHeardJARLLink)
								)
							) {
								boolean isValidCallsign = false;

								final String myCallsign =
									String.valueOf(packet.getRFHeader().getMyCallsign());

								switch(repeater.getRoutingService().getServiceType()) {
								case JapanTrust:
									isValidCallsign =
										CallSignValidator.isValidUserCallsign(myCallsign);
									break;

								case GlobalTrust:
									isValidCallsign =
										CallSignValidator.isValidUserCallsign(myCallsign);
									break;

								case ircDDB:
									isValidCallsign =
										CallSignValidator.isValidUserCallsign(myCallsign);
									break;

								default:
									isValidCallsign =
										CallSignValidator.isValidUserCallsign(myCallsign);
									break;
								}

								if(isValidCallsign) {
									final UUID heardID = gateway.positionUpdate(
										entry.getRepeater(),
										entry.getFrameID(),
										String.valueOf(packet.getRFHeader().getMyCallsign()),
										String.valueOf(packet.getRFHeader().getMyCallsignAdd()),
										String.valueOf(packet.getRFHeader().getYourCallsign()),
										String.valueOf(packet.getRFHeader().getRepeater1Callsign()),	// Repeater Call
										String.valueOf(packet.getRFHeader().getRepeater2Callsign()),	// Gateway Call
										packet.getRFHeader().getFlags()[0],
										packet.getRFHeader().getFlags()[1],
										packet.getRFHeader().getFlags()[2]
									);
									if (heardID == null) {continue;}

									entry.setRoutingID(heardID);
									entry.setProcessState(ProcessStates.SendHeard);

									if(log.isInfoEnabled()) {
										log.info(
											"[G/W] " +
											"MY:" + String.valueOf(packet.getRFHeader().getMyCallsign()) + "/" +
											//"UR:" + String.valueOf(packet.getRFHeader().getYourCallsign()) + "/" +
											"RPT1:" + String.valueOf(packet.getRFHeader().getRepeater1Callsign()) + "/" +
											"RPT2:" + String.valueOf(packet.getRFHeader().getRepeater2Callsign())
										);
									}
								}
								else {
									if(log.isWarnEnabled()) {
										log.warn(
											"Bad callsign " + myCallsign +
											" for " + repeater.getRoutingService().getServiceType() + "."
										);
									}
								}
							}
						}
						else if(entry.getProcessMode() == ProcessModes.RepeaterToCrossband){

						}
						else {
							entry.setProcessState(ProcessStates.Invalid);
						}

						if(
							entry.getProcessState() == ProcessStates.Valid ||
							entry.getProcessState() == ProcessStates.SendHeard
						) {
							if(entry.getProcessMode() == ProcessModes.RepeaterToReflector) {
								final DSTARPacket clonePacket = packet.clone();
								if(updateToReflectorHeader(clonePacket, repeater)) {
									gateway.writePacketToReflectorRoute(
										repeater, ConnectionDirectionType.OUTGOING,
										clonePacket
									);
								}
							}
							else if(entry.getProcessMode() == ProcessModes.RepeaterToCrossband) {
								final DSTARRepeater destinationRepeater =
									gateway.getRepeater(
										String.valueOf(packet.getRFHeader().getRepeater2Callsign())
									);

								if(destinationRepeater != null) {destinationRepeater.writePacket(packet);}
							}
						}

						//リフレクターのIncomingLinkへ送信
						writeToReflectorIncomingLink(gateway, entry, repeater, packet);

						entry.setHeaderPacket(packet.clone());
						// entry.getCachePackets().add(packet);

						entry.getHeardInfo().setHeardHeader(heardHeader);

						processStatusHeardEntry(gateway, frameID, PacketType.Header, packet, packet.getProtocol(), entry, repeater);

						entry.updateActivityTimestamp();

						processEntries.put(frameID, entry);

						if(log.isDebugEnabled()) {
							log.debug("Process entry regist.\n" + entry.toString(4));
						}
					}
					/*
					 * Voice packet
					 */
					else if (
						entry != null &&
						packet.getDVPacket().hasPacketType(PacketType.Voice)
					) {
						entry.getLocker().lock();
						try {
							if(
								entry.getProcessState() == ProcessStates.Valid ||
								entry.getProcessState() == ProcessStates.SendHeard
							) {
								if (entry.getProcessMode() == ProcessModes.RepeaterToReflector) {
									writeToReflector(
										gateway, entry, repeater,
										ConnectionDirectionType.OUTGOING,
										packet
									);
									if(!packet.isLastFrame() && packet.getBackBoneHeader().isMaxSequence()) {
										writeToReflector(
											gateway, entry, repeater,
											ConnectionDirectionType.OUTGOING,
											entry.getHeaderPacket()
										);
									}
								}
								else if(entry.getProcessMode() == ProcessModes.RepeaterToCrossband) {
									final String destinationRepeaterCallsign =
										String.valueOf(entry.getHeaderPacket().getRFHeader().getRepeater2Callsign());

									final DSTARRepeater destinationRepeater =
										gateway.getRepeater(destinationRepeaterCallsign);

									if(destinationRepeater != null) {
										if (!entry.getCachePackets().isEmpty()) {
											for (final Iterator<DSTARPacket> it = entry.getCachePackets().iterator(); it.hasNext();) {
												final DSTARPacket cachedPacket = it.next();
												it.remove();

												destinationRepeater.writePacket(cachedPacket);

												writeToReflector(
													gateway, entry, destinationRepeater,
													ConnectionDirectionType.INCOMING,
													cachedPacket
												);
											}
										}

										destinationRepeater.writePacket(packet);

										writeToReflector(
											gateway, entry, destinationRepeater,
											ConnectionDirectionType.INCOMING,
											packet
										);
										if(!packet.isLastFrame() && packet.getBackBoneHeader().isMaxSequence()) {
											writeToReflector(
												gateway, entry, destinationRepeater,
												ConnectionDirectionType.INCOMING,
												entry.getHeaderPacket()
											);
										}
									}
								}
							}

							if (entry.getProcessState() == ProcessStates.Valid) {
								if (entry.getProcessMode() == ProcessModes.RepeaterToG2) {
									if (!entry.getCachePackets().isEmpty()) {
										for (final Iterator<DSTARPacket> it = entry.getCachePackets().iterator(); it.hasNext();) {
											final DSTARPacket cachedPacket = it.next();
											it.remove();

											gateway.writePacketToG123Route(cachedPacket, entry.getRemoteAddress());
										}
									}
									gateway.writePacketToG123Route(packet, entry.getRemoteAddress());

									if(!packet.isLastFrame() && packet.getBackBoneHeader().isMaxSequence())
										gateway.writePacketToG123Route(entry.getHeaderPacket(), entry.getRemoteAddress());
								}
							} else {
								//ルートが有効でない時にはパケットをある程度キャッシュしておく
								addCachePacket(entry, packet);
							}

							//リフレクターのIncomingLinkへ送信
							writeToReflectorIncomingLink(gateway, entry, repeater, packet);
							if(!packet.isLastFrame() && packet.getBackBoneHeader().isMaxSequence())
								writeToReflectorIncomingLink(gateway, entry, repeater, entry.getHeaderPacket());

							entry.getErrorDetector().update(packet.getDVPacket());

							processStatusHeardEntry(gateway, frameID, PacketType.Voice, packet, packet.getProtocol(), entry, repeater);

							if (packet.isLastFrame()) {
								processEntries.remove(frameID);

								if(log.isTraceEnabled()) {
									log.trace("Process entry removed.\n    " + entry.toString());
								}
							} else {
								entry.updateActivityTimestamp();
							}
						}finally {
							entry.getLocker().unlock();
						}
					} else {
						continue;
					}
				}finally {
					processEntriesLocker.unlock();
				}
			}
		}
	}

	private static ProcessEntry processControlCommand(
		final GatewayHelperProperties properties,
		final DSTARGatewayImpl gateway,
		final DSTARRepeater repeater,
		final DSTARPacket packet,
		final Header heardHeader,
		final AnnounceTool announceTool
	) {
		boolean isControlCommand = false;
		boolean isReflectorLinkCommand = false;
		boolean isReflectorCommand = false;
		if(
			packet.getPacketType() != DSTARPacketType.DV ||
			!packet.getDVPacket().hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRFHeader().getMyCallsign()) ||
			(
				!(isControlCommand =
					controlCommandPattern.matcher(String.valueOf(packet.getRFHeader().getYourCallsign())).matches()
				) &&
				!(isReflectorLinkCommand =
					reflectorLinkPattern.matcher(String.valueOf(packet.getRFHeader().getYourCallsign())).matches()
				) &&
				!(isReflectorCommand =
					reflectorCommandPattern.matcher(String.valueOf(packet.getRFHeader().getYourCallsign())).matches()
				)
			) ||
			!gateway.getGatewayCallsign().equals(String.valueOf(packet.getRFHeader().getRepeater2Callsign()))
		) {
			return null;
		}

		ProcessEntry entry = null;
		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		boolean success = false;
		final String yourCallsign = String.valueOf(packet.getRFHeader().getYourCallsign());

		if(isReflectorCommand) {
			final char command = packet.getRFHeader().getYourCallsign()[DSTARDefines.CallsignFullLength - 1];
			switch(command) {
			case 'U':
				if(log.isInfoEnabled()) {
					log.info(
						"[Reflector Unlink Request] " +
						"MY:" + String.valueOf(packet.getRFHeader().getMyCallsign()) +
							String.valueOf(packet.getRFHeader().getMyCallsignAdd()) +
						"/UR:" + String.valueOf(packet.getRFHeader().getYourCallsign()) +
						"/RPT2:" + String.valueOf(packet.getRFHeader().getRepeater2Callsign()) +
						"/RPT1:" + String.valueOf(packet.getRFHeader().getRepeater1Callsign())
					);
				}
				gateway.unlinkReflector(repeater);
				success = true;
				break;

			case 'E':
				break;

			case 'I':
				break;

			default:
				break;
			}
		}
		else if(isReflectorLinkCommand) {
			// Link to Reflector
			final String reflectorCallsign =
				yourCallsign.substring(0, DSTARDefines.CallsignFullLength - 2) + ' ' +
				yourCallsign.charAt(DSTARDefines.CallsignFullLength - 2);

			//既に希望するリフレクターへリンクしている場合には、リフレクターCQへ置き換え
			if(
				properties.isAutoReplaceCQFromReflectorLinkCommand() &&
				reflectorCallsign.equals(gateway.getOutgoingLinkedReflectorCallsign(repeater))
			) {
				packet.getRFHeader().setYourCallsign(DSTARDefines.CQCQCQ);
				heardHeader.setYourCallsign(DSTARDefines.CQCQCQ);

				if(log.isDebugEnabled()) {
					log.debug(
						"Replace to reflector CQCQCQ from reflector link command, " +
						reflectorCallsign + " is already linked."
					);
				}

				return null;
			}

			final Optional<ReflectorHostInfo> opReflectorHostInfo =
				gateway.findReflectorByCallsign(reflectorCallsign);

			if(opReflectorHostInfo.isPresent()) {
				final ReflectorHostInfo host = opReflectorHostInfo.get();

				if(log.isInfoEnabled()) {
					log.info(
						"[Reflector Link Request(" + host.getReflectorProtocol() + ")] " +
						"DEST:" + reflectorCallsign +
							(!reflectorCallsign.equals(host.getReflectorCallsign()) ? "*" + host.getReflectorCallsign() : "") +
							(!"".equals(host.getName()) ? "(" + host.getName() + ")" : "") +
						"/MY:" + String.valueOf(packet.getRFHeader().getMyCallsign()) + " " +
							String.valueOf(packet.getRFHeader().getMyCallsignAdd()) +
						"/RPT:" + String.valueOf(packet.getRFHeader().getRepeater1Callsign()) +
						"/ADDR:" + host.getReflectorAddress() + ":" + host.getReflectorPort() +
						"/DS:" + host.getDataSource()
					);
				}

				success = gateway.linkReflector(repeater, reflectorCallsign, host);
			}
			else {
				if(log.isInfoEnabled()) {
					log.info(
						"[Reflector Link Request(Failed)] " +
						"Reflector host " + reflectorCallsign +
						" information not found, ignore request.\n" + packet.getRFHeader().toString(4)
					);
				}
			}
		}
		else if(isControlCommand) {

			// G2 Route Configulation
			if(
				yourCallsign.length() >= DSTARDefines.CallsignFullLength &&
				yourCallsign.substring(4, 7).startsWith("G2R"))
			{
				final char routingServiceChar = yourCallsign.charAt(DSTARDefines.CallsignFullLength - 1);

				RoutingServiceTypes targetRoutingServiceType = null;
				switch(routingServiceChar) {
				case 'J':	// Change routing service to JapanTrust
					targetRoutingServiceType = RoutingServiceTypes.JapanTrust;
					break;

				case 'G':	// Change routing service to GlobalTrust
					targetRoutingServiceType = RoutingServiceTypes.GlobalTrust;
					break;

				case 'I':	// Change routing service to IrcDDB
					targetRoutingServiceType = RoutingServiceTypes.ircDDB;
					break;

				default:
					targetRoutingServiceType = null;
					break;
				}

				if(targetRoutingServiceType != null) {
					success = gateway.changeRoutingService(repeater, targetRoutingServiceType);

					if(success) {
						if(log.isInfoEnabled()) {
							log.info(
								"Changed routing service to " + targetRoutingServiceType.getTypeName() +
								". [Repeater:" + repeater.getRepeaterCallsign() + "]"
							);
						}

						success = announceTool.announceCurrentRoutingService(repeater);
					}
					else {
						if(log.isInfoEnabled()) {
							log.info(
								"Failed change routing service to " + targetRoutingServiceType.getTypeName() +
								". [Repeater:" + repeater.getRepeaterCallsign() + "]"
							);
						}
					}
				}
				else {
					if(log.isInfoEnabled())
						log.info("Unknown G2 route change request received.\n" + packet.toString());
				}
			}
			else if(	// Reflector Link Manager Configulation
				yourCallsign.length() >= DSTARDefines.CallsignFullLength &&
				yourCallsign.substring(2, 7).startsWith("RLMAC"))
			{
				final char controlChar = yourCallsign.charAt(DSTARDefines.CallsignFullLength - 1);

				switch(controlChar) {
				case 'E':
					success =
						gateway.getReflectorLinkManager().setAutoControlEnable(repeater, true);
					break;

				case 'D':
					success =
						gateway.getReflectorLinkManager().setAutoControlEnable(repeater, false);
					break;

				default:
					if(log.isInfoEnabled())
						log.info("Unknown ReflectorLinkManager configulation request received.\n" + packet.toString());
					break;
				}
			}
			else {
				final char command =
					packet.getRFHeader().getYourCallsign()[DSTARDefines.CallsignFullLength - 1];

				switch(command) {
				case 'D':	//debug
					success = true;
					break;

				case 'I':	// Repeater Information
					success = announceTool.announceInformation(
						repeater, properties.getAnnounceCharactor(), repeater.getLinkedReflectorCallsign()
					);
					break;

				case 'R':
					success = announceTool.announceCurrentRoutingService(repeater);
					break;

				case 'T':	// Toggle Transparent Mode
					//TODO
					break;

				default:
					if(log.isInfoEnabled())
						log.info("Unknown gateway control command received.\n" + packet.toString());
					break;
				}
			}
		}

		if(success) {
			entry = new ProcessEntry(frameID, ProcessModes.Control, repeater);
			entry.setProcessState(ProcessStates.Valid);
		}
		else {
			sendFlagToRepeaterUser(

				gateway,
				repeater,
				packet.getRFHeader().getMyCallsignString(),
				RepeaterControlFlag.CANT_REPEAT
			);
			entry = new ProcessEntry(frameID, ProcessModes.RepeaterToNull, repeater);
			entry.setProcessState(ProcessStates.Invalid);
		}

		return entry;
	}

	private static ProcessEntry processCrossbandRoute(
		final DSTARGatewayImpl gateway,
		final DSTARRepeater repeater,
		final DSTARPacket packet,
		final Header heardHeader
	) {
		DSTARRepeater destinationRepeater = null;
		if(
			packet.getPacketType() != DSTARPacketType.DV ||
			!packet.getDVPacket().hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRFHeader().getMyCallsign()) ||
			!CallSignValidator.isValidAreaRepeaterCallsign(packet.getRFHeader().getYourCallsign()) ||
			!gateway.getGatewayCallsign().equals(String.valueOf(packet.getRFHeader().getRepeater2Callsign())) ||
			(
				destinationRepeater = gateway.getRepeater(
					DSTARUtils.convertAreaRepeaterCallToRepeaterCall(
							String.valueOf(packet.getRFHeader().getYourCallsign())
					))
			) == null
		) {
			return null;
		}

		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		if(log.isInfoEnabled()) {
			log.info(
				"[Local X Band] " +
				"MY:" + String.valueOf(packet.getRFHeader().getMyCallsign()) + String.valueOf(packet.getRFHeader().getMyCallsignAdd()) +
				"/UR:" + String.valueOf(packet.getRFHeader().getYourCallsign()) +
				"/RPT2:" + String.valueOf(packet.getRFHeader().getRepeater2Callsign()) +
				"/RPT1:" + String.valueOf(packet.getRFHeader().getRepeater1Callsign())
				);
		}

		ProcessEntry entry = null;
		if(repeater == destinationRepeater) {
			entry = new ProcessEntry(frameID, ProcessModes.HeardOnly, repeater);
			entry.setProcessState(ProcessStates.Valid);
		}
		else {
			packet.getRFHeader().setYourCallsign(DSTARDefines.CQCQCQ.toCharArray());
			packet.getRFHeader().setRepeater1Callsign(gateway.getGatewayCallsign().toCharArray());
			packet.getRFHeader().setRepeater2Callsign(destinationRepeater.getRepeaterCallsign().toCharArray());

			entry = new ProcessEntry(frameID, ProcessModes.RepeaterToCrossband, repeater);
			entry.setProcessState(ProcessStates.Valid);

			destinationRepeater.writePacket(packet);
		}

		return entry;
	}

	private static ProcessEntry processReflectorRoute(
		final DSTARGatewayImpl gateway,
		final DSTARPacket packet, final DSTARRepeater repeater,
		final Header heardHeader
	) {
		if(
			packet.getPacketType() != DSTARPacketType.DV ||
			!packet.getDVPacket().hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRFHeader().getMyCallsign()) ||
			!CallSignValidator.isValidCQCQCQ(packet.getRFHeader().getYourCallsign()) ||
			!gateway.getGatewayCallsign().equals(String.valueOf(packet.getRFHeader().getRepeater2Callsign()))
		) {
			return null;
		}

		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		boolean valid = false;

		final ProcessEntry entry = new ProcessEntry(frameID, ProcessModes.RepeaterToReflector, repeater);

		final String yourCallsign = String.valueOf(packet.getRFHeader().getYourCallsign());

		if(CallSignValidator.isValidCQCQCQ(yourCallsign)) {
			if(log.isInfoEnabled()) {
				log.info(
					"[Reflector OUT] " +
					"MY:" + String.valueOf(packet.getRFHeader().getMyCallsign()) + String.valueOf(packet.getRFHeader().getMyCallsignAdd()) +
					"/UR:" + String.valueOf(packet.getRFHeader().getYourCallsign()) +
					"/RPT2:" + String.valueOf(packet.getRFHeader().getRepeater2Callsign()) +
					"/RPT1:" + String.valueOf(packet.getRFHeader().getRepeater1Callsign())
				);
			}

			valid = true;
		}

		if(valid) {
			entry.setProcessMode(ProcessModes.RepeaterToReflector);
			entry.setProcessState(ProcessStates.Valid);

			//指定のレピータがリフレクターへリンクしていない場合にはRPT?を返す
			if(!gateway.getReflectorLinkManager().isReflectorLinked(repeater, ConnectionDirectionType.BIDIRECTIONAL)){
				sendFlagToRepeaterUser(
					gateway,
					repeater,
					String.valueOf(packet.getRFHeader().getMyCallsign()),
					RepeaterControlFlag.CANT_REPEAT
				);
			}
		}
		else {
			entry.setProcessMode(ProcessModes.RepeaterToNull);
			entry.setProcessState(ProcessStates.Invalid);

			sendFlagToRepeaterUser(
				gateway,
				repeater,
				String.valueOf(packet.getRFHeader().getMyCallsign()),
				RepeaterControlFlag.CANT_REPEAT
			);
		}

		return entry;
	}

	public static ProcessEntry processG123Route(
		@NonNull final GatewayHelperProperties properties,
		@NonNull final DSTARGatewayImpl gateway,
		@NonNull final DSTARRepeater repeater,
		@NonNull final DSTARPacket packet,
		@NonNull final Header heardHeader,
		@NonNull final Lock processEntriesLocker,
		@NonNull final Map<Integer, ProcessEntry> processEntries,
		@NonNull final AnnounceTool announceTool
	) {
		if(
			packet.getPacketType() != DSTARPacketType.DV ||
			!packet.getDVPacket().hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRFHeader().getMyCallsign()) ||
			(
				!CallSignValidator.isValidUserCallsign(packet.getRFHeader().getYourCallsign()) &&
				!CallSignValidator.isValidAreaRepeaterCallsign(packet.getRFHeader().getYourCallsign())
			) ||
			!gateway.getGatewayCallsign().equals(String.valueOf(packet.getRFHeader().getRepeater2Callsign()))
		) {
			return null;
		}

		if(!repeater.isUseRoutingService()) {
			if(log.isWarnEnabled()) {
				log.warn(
					"Repeater " + repeater.getRepeaterCallsign() +
					" is not useRoutingService enabled, ignore header.\n" + packet.toString(4)
				);
			}
		}

		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		boolean busyCondition = false;
		processEntriesLocker.lock();
		try {
			busyCondition =
				Stream.of(processEntries)
				.filter(new Predicate<Map.Entry<Integer,ProcessEntry>>() {
					@Override
					public boolean test(Map.Entry<Integer,ProcessEntry> value) {
						return packet.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.CANT_REPEAT) &&
							String.valueOf(
								value.getValue().getHeaderPacket().getRFHeader().getMyCallsign()
							).equals(String.valueOf(packet.getRFHeader().getYourCallsign())) &&
							value.getValue().getProcessMode() == ProcessModes.G2ToRepeater;
					}
				})
				.max(ComparatorCompat.comparingLong(new ToLongFunction<Map.Entry<Integer,ProcessEntry>>() {
					@Override
					public long applyAsLong(Entry<Integer, ProcessEntry> t) {
						return t.getValue().getActivityTimekeeper().getTimestampMilis();
					}
				}))
				.isPresent();
		}finally {
			processEntriesLocker.unlock();
		}

		ProcessEntry entry = null;

		if(
			repeater.isUseRoutingService() &&
			(
				busyCondition ||	// Busyヘッダを送信する条件に合致するか？
				// 通常と自動応答はそのまま送る
				packet.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.NOTHING_NULL) ||
				packet.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.AUTO_REPLY)
			)
		) {
			if(busyCondition) {
				if(log.isDebugEnabled())
					log.debug("Busy header received from repeater " + repeater.getRepeaterCallsign() + ".");
			}

			entry = new ProcessEntry(frameID, ProcessModes.RepeaterToG2, repeater);
			entry.setProcessState(ProcessStates.Unknown);

			if(!busyCondition) {
				autoDisconnectFromReflectorOnTxG2Route(
					gateway,
					packet.getRFHeader().getRepeaterControlFlag(),
					String.valueOf(packet.getRFHeader().getRepeater1Callsign())
				);
			}

		}
		else {
			entry = new ProcessEntry(frameID, ProcessModes.RepeaterToNull, repeater);
			entry.setProcessState(ProcessStates.Invalid);
		}

		return entry;
	}

	private static ProcessEntry processHeardOnlyRoute(
		final DSTARGatewayImpl gateway,
		final DSTARRepeater repeater,
		final DSTARPacket packet,
		final Header heardHeader
	) {
		if(
			packet.getPacketType() != DSTARPacketType.DV ||
			!packet.getDVPacket().hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRFHeader().getMyCallsign()) ||
			!CallSignValidator.isValidRepeaterCallsign(packet.getRFHeader().getRepeater1Callsign()) ||
			!CallSignValidator.isValidRepeaterCallsign(packet.getRFHeader().getRepeater2Callsign()) ||
			!Arrays.equals(packet.getRFHeader().getRepeater1Callsign(), packet.getRFHeader().getRepeater2Callsign()) ||
			gateway.getRepeater(String.valueOf(packet.getRFHeader().getRepeater1Callsign())) == null
		) {return null;}

		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		//URをCQCQCQに書き換え
		heardHeader.setYourCallsign(DSTARDefines.CQCQCQ);
		packet.getRFHeader().setYourCallsign(DSTARDefines.CQCQCQ.toCharArray());

		ProcessEntry entry = null;
/*
		if(!repeater.isUseRoutingService()) {
			if(log.isWarnEnabled()) {
				log.warn(
					"Repeater " + repeater.getRepeaterCallsign() + " is not useRoutingService enabled, ignore header.\n" +
					packet.toString(4)
				);
			}
		}
*/
		if(repeater.isUseRoutingService()) {
			//RPT2をゲートウェイコールに設定
			packet.getRFHeader().setRepeater2Callsign(gateway.getGatewayCallsign().toCharArray());

			entry = new ProcessEntry(frameID, ProcessModes.HeardOnly, repeater);
			entry.setProcessState(ProcessStates.Valid);
		}
		else {
			entry = new ProcessEntry(frameID, ProcessModes.RepeaterToNull, repeater);
			entry.setProcessState(ProcessStates.Invalid);
		}

		return entry;
	}

	private static ProcessEntry processUnknownRoute(
		final DSTARGatewayImpl gateway,
		final DSTARRepeater repeater,
		final DSTARPacket packet,
		final Header heardHeader
	) {
		if(
			packet.getPacketType() != DSTARPacketType.DV ||
			!packet.getDVPacket().hasPacketType(PacketType.Header) ||
			!CallSignValidator.isValidUserCallsign(packet.getRFHeader().getMyCallsign())
		) {
			return null;
		}

		final int frameID = packet.getBackBoneHeader().getFrameIDNumber();

		// Return RPT? flag
		sendFlagToRepeaterUser(
			gateway,
			repeater,
			String.valueOf(packet.getRFHeader().getMyCallsign()),
			RepeaterControlFlag.CANT_REPEAT
		);
		final ProcessEntry entry = new ProcessEntry(frameID, ProcessModes.RepeaterToNull, repeater);
		entry.setProcessState(ProcessStates.Invalid);

		if(log.isInfoEnabled()) {
			log.info("Could not determine packet route, ignore unknown route packet.\n" + packet.toString(4));
		}

		return entry;
	}
}
