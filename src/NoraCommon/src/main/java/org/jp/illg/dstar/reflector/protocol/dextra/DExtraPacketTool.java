package org.jp.illg.dstar.reflector.protocol.dextra;

import com.annimon.stream.Optional;

import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraPacket;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraPacketImpl;

import java.nio.ByteBuffer;

public class DExtraPacketTool {

	public static Optional<DExtraPacket> isValidConnectInfoPacket(ByteBuffer buffer) {
		return DExtraPacketImpl.isValidConnectInfoPacket(buffer);
	}

	public static Optional<byte[]> assembleConnectInfoPacket(DExtraPacket packet){
		return DExtraPacketImpl.assembleConnectInfoPacket(packet);
	}

	public static Optional<DExtraPacket> isValidPollPacket(ByteBuffer buffer) {
		return DExtraPacketImpl.isValidPollPacket(buffer);
	}

	public static Optional<byte[]> assemblePollPacket(DExtraPacket packet){
		return DExtraPacketImpl.assemblePollPacket(packet);
	}

	public static Optional<DExtraPacket> isValidHeaderPacket(ByteBuffer buffer){
		return DExtraPacketImpl.isValidHeaderPacket(buffer);
	}

	public static Optional<byte[]> assembleHeaderPacket(DExtraPacket packet){
		return DExtraPacketImpl.assembleHeaderPacket(packet);
	}

	public static Optional<DExtraPacket> isValidVoicePacket(ByteBuffer buffer){
		return DExtraPacketImpl.isValidVoicePacket(buffer);
	}

	public static Optional<byte[]> assembleVoicePacket(DExtraPacket packet){
		return DExtraPacketImpl.assembleVoicePacket(packet);
	}
}
