package org.jp.illg.dstar.repeater.homeblew.protocol;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.homeblew.model.HRPPacket;
import org.jp.illg.dstar.repeater.homeblew.model.HRPPacketImpl;

public class HRPPacketTool {

	public static HRPPacket isValidHeader(ByteBuffer buffer) {
		return HRPPacketImpl.isValidHeader(buffer);
	}

	public static HRPPacket isValidAMBE(ByteBuffer buffer) {
		return HRPPacketImpl.isValidAMBE(buffer);
	}

	public static HRPPacket isValidText(ByteBuffer buffer) {
		return HRPPacketImpl.isValidText(buffer);
	}

	public static HRPPacket isValidTempText(ByteBuffer buffer) {
		return HRPPacketImpl.isValidTempText(buffer);
	}

	public static HRPPacket isValidPoll(ByteBuffer buffer) {
		return HRPPacketImpl.isValidPoll(buffer);
	}

	public static HRPPacket isValidStatus(ByteBuffer buffer) {
		return HRPPacketImpl.isValidStatus(buffer);
	}

	public static HRPPacket isValidRegister(ByteBuffer buffer) {
		return HRPPacketImpl.isValidRegister(buffer);
	}

	public static byte[] assembleHeader(HRPPacket packet) {
		return HRPPacketImpl.assembleHeader(packet);
	}

	public static byte[] assembleAMBE(HRPPacket packet) {
		return HRPPacketImpl.assembleAMBE(packet);
	}

	public static byte[] assemblePoll(HRPPacket packet) {
		return HRPPacketImpl.assemblePoll(packet);
	}

	public static byte[] assembleText(HRPPacket packet) {
		return HRPPacketImpl.assembleText(packet);
	}

	public static byte[] assembleStatus(HRPPacket packet) {
		return HRPPacketImpl.assembleStatus(packet);
	}

	public static byte[] assembleRegister(HRPPacket packet) {
		return HRPPacketImpl.assembleRegister(packet);
	}
}
