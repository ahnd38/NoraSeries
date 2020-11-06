package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;

public interface AccessPointCommand {

	public long getCreatedTimestamp();

	public DVPacket getDvPacket();
	public Header getDvHeader();
	public void setDvHeader(Header header);
	public VoiceData getVoiceData();
	public void setVoiceData(VoiceData voice);
	public void setBackBone(BackBoneHeader backBone);
	public BackBoneHeader getBackBone();

	public char[] getYourCallsign();

	public char[] getRepeater1Callsign();

	public char[] getRepeater2Callsign();

	public char[] getMyCallsign();

	public char[] getMyCallsignAdd();

	public byte[] getVoiceSegment();

	public byte[] getDataSegment();

	public boolean isEndPacket();

	public AccessPointCommand clone();

	/**
	 * 送信コマンドデータを取得する
	 * @return
	 */
	public byte[] assembleCommandData();

	/**
	 * 受信データコマンドを解析し、パースする
	 * @param buffer
	 * @return
	 */
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer);

	public String toString();
}
