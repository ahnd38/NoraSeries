package org.jp.illg.dstar.routing.service.jptrust.model;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.jp.illg.dstar.routing.service.jptrust.model.JpTrustCommandBase.CommandType;

public interface JpTrustCommand extends Cloneable,Comparable<JpTrustCommand>{

	public abstract JpTrustCommand clone();

	public abstract int compareTo(JpTrustCommand o);

	public void clear();

	/**
	 * コマンドを組み立てる
	 * @return コマンドバイト列
	 */
	public abstract byte[] assembleCommandData();

	/**
	 * バッファを解析して該当あればレスポンスコマンドを生成する
	 * @param buffer 受信バイト列
	 * @return レスポンスコマンド(該当が無かった場合はnull)
	 */
	public abstract JpTrustCommand parseCommandData(ByteBuffer buffer);

	/**
	 * 処理結果を取得する
	 * @return 処理結果
	 */
	public JpTrustResult getResult();

	/**
	 * 処理結果をセットする
	 * @param result 処理結果
	 */
	public void setResult(JpTrustResult result);

	/**
	 * コマンドIDを取得する
	 * @return コマンドID
	 */
	public byte[] getCommandID();

	/**
	 * Integer型に変換されたコマンドIDを取得する
	 * @return コマンドID
	 */
	public int getCommandIDInteger();

	/**
	 * コマンドIDを設定する
	 * @param commandID int型コマンドID
	 */
	public void setCommandIDInteger(int commandID);

	/**
	 * コマンドタイプを取得する
	 * @return コマンドタイプ
	 */
	public CommandType getCommandType();

	/**
	 * レピータ2コールサインを取得する
	 * @return レピータ2コールサイン(8桁)
	 */
	public char[] getRepeater2Callsign();

	/**
	 * レピータ2コールサインを設定する
	 * @param repeater2Callsign レピータ2コールサイン(8桁)
	 */
	public void setRepeater2Callsign(char[] repeater2Callsign);

	/**
	 * レピータ1コールサインを取得する
	 * @return レピータ1コールサイン(8桁)
	 */
	public char[] getRepeater1Callsign();

	/**
	 * レピータ1コールサインを設定する
	 * @param repeater1Callsign レピータ1コールサイン(8桁)
	 */
	public void setRepeater1Callsign(char[] repeater1Callsign);

	/**
	 * URコールサインを取得する
	 * @return URコールサイン(8桁)
	 */
	public char[] getYourCallsign();

	/**
	 * URコールサインを設定する
	 * @param yourCallsign URコールサイン(8桁)
	 */
	public void setYourCallsign(char[] yourCallsign);

	/**
	 * MYコールサインを取得する
	 * @return MYコールサイン(8桁)
	 */
	public char[] getMyCallsign();

	/**
	 * MYコールサインを設定する
	 * @param myCallsign MYコールサイン(8桁)
	 */
	public void setMyCallsign(char[] myCallsign);

	/**
	 * 付加コールサインを取得する
	 * @return 付加コールサイン(4桁)
	 */
	public char[] getMyCallsignAdd();

	/**
	 * 付加コールサインを設定する
	 * @param myCallsignAdd 付加コールサイン(4桁)
	 */
	public void setMyCallsignAdd(char[] myCallsignAdd);

	/**
	 * ゲートウェイアドレスを取得する
	 * @return ゲートウェイアドレス
	 */
	public InetAddress getGatewayAddress();

	/**
	 * ゲートウェイアドレスを設定する
	 * @param address ゲートウェイアドレス
	 */
	public void setGatewayAddress(InetAddress address);

	/**
	 * リモートアドレスを取得する(このコマンドの送信元)
	 * @return コマンド送信元のアドレス
	 */
	public InetSocketAddress getRemoteAddress();

	/**
	 * リモートアドレスを設定する(このコマンドの送信先)
	 * @param remoteAddress コマンド送信先のアドレス
	 */
	public void setRemoteAddress(InetSocketAddress remoteAddress);

	/**
	 * タイムスタンプを設定する
	 * @param timestamp タイムスタンプ値
	 */
	public void setTimestamp(long timestamp);

	/**
	 * タイムスタンプを取得する
	 * @return タイムスタンプ値
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


	public String toString();
}

