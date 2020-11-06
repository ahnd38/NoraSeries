package org.jp.illg.dstar.g123.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;

public interface G2Packet extends DSTARPacket{

	public abstract G2Packet clone();

	/**
	 * 保持しているデータをクリアする
	 */
	public abstract void clear();

	/**
	 * Repeater 2 Callsignを取得する
	 * @return
	 */
	public char[] getRepeater2Callsign();

	/**
	 * Repeater 1 Callsignを取得する
	 * @return
	 */
	public char[] getRepeater1Callsign();

	/**
	 * Your Callsignを取得する
	 * @return
	 */
	public char[] getYourCallsign();

	/**
	 * My Callsignを取得する
	 * @return
	 */
	public char[] getMyCallsign();

	/**
	 * My Callsign Additionalを取得する
	 * @return
	 */
	public char[] getMyCallsignAdd();

	public Header getRfHeader();

	public BackBoneHeader getBackBone();

	public VoiceData getVoiceData();

	/**
	 * リモートアドレスをセットする
	 * (直接の送受信先・元)
	 * @param address
	 */
	public void setRemoteAddress(InetSocketAddress address);

	/**
	 * リモートアドレスを取得する
	 * (直接の送受信先・元)
	 * @return
	 */
	public InetSocketAddress getRemoteAddress();

	/**
	 * タイムスタンプをセットする
	 * @param timestamp
	 */
	public void setTimestamp(long timestamp);

	/**
	 * タイムスタンプを取得する
	 * @return
	 */
	public long getTimestamp();

	/**
	 * タイムスタンプを更新する
	 */
	public void updateTimestamp();

	/**
	 * タイムスタンプをクリアする
	 */
	public void clearTimestamp();

	/**
	 * 送信用コマンドを組み立てる
	 * (予め、コールサインなどのデータのセットが必要)
	 * @return
	 */
	public byte[] assembleCommandData();

	/**
	 * 受信データの解析&パースを行う
	 * @param buffer 受信バッファ
	 * @return
	 */
	public G2Packet parseCommandData(ByteBuffer buffer);


	public String toString(int indentLevel);
}
