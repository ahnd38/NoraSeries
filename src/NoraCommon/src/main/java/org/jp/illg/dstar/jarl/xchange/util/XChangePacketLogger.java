package org.jp.illg.dstar.jarl.xchange.util;

import org.jp.illg.dstar.jarl.xchange.model.XChangePacket;
import org.jp.illg.dstar.jarl.xchange.model.XChangePacketType;
import org.jp.illg.dstar.model.defines.PacketType;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XChangePacketLogger {

	private XChangePacketLogger() {}

	public static void log(
		@NonNull final XChangePacket packet,
		@NonNull final String headerMessage
	) {
		if(packet.getType() == XChangePacketType.Voice && packet.getDvPacket() != null) {
			if(packet.getDvPacket().hasPacketType(PacketType.Header)) {
				if(log.isDebugEnabled())
					log.debug("[HEADER] " + headerMessage + "\n" + packet.toString(4));
			}

			if(packet.getDvPacket().hasPacketType(PacketType.Voice)) {
				if(packet.getDvPacket().isEndVoicePacket()) {
					if(log.isDebugEnabled())
						log.debug("[VOICE END] " + headerMessage + "\n" + packet.toString(4));
				}
				else {
					if(log.isTraceEnabled())
						log.debug("[VOICE] " + headerMessage + "\n" + packet.toString(4));
				}
			}
		}
		else {
			if(log.isTraceEnabled())
				log.trace("[OTHER] " + headerMessage + "\n" + packet.toString(4));
		}
	}
}
