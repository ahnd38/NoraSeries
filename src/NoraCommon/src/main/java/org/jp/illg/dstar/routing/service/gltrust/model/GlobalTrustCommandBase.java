package org.jp.illg.dstar.routing.service.gltrust.model;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.lang3.SerializationUtils;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.util.ArrayUtil;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public abstract class GlobalTrustCommandBase implements GlobalTrustCommand{


	/**
	 * コマンドID
	 * (管理サーバ用)
	 */
	@Getter
	private byte[] commandID;

	/**
	 * コマンドタイプ
	 */
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private CommandType commandType;

	/**
	 * Repeater 2 Callsign
	 */
	@Getter
	private char[] repeater2Callsign;

	/**
	 * Repeater 1 Callsign
	 */
	@Getter
	private char[] repeater1Callsign;

	/**
	 * Your Callsign
	 */
	@Getter
	private char[] yourCallsign;

	/**
	 * My Callsign
	 */
	@Getter
	private char[] myCallsign;

	/**
	 * My Callsign Additional
	 */
	@Getter
	private char[] myCallsignAdd;

	/**
	 * ゲートウェイアドレス
	 */
	@Getter
	@Setter
	private InetAddress gatewayAddress;

	/**
	 * リモートアドレス
	 */
	@Getter
	@Setter
	private InetSocketAddress remoteAddress;

	/**
	 * タイムスタンプ
	 */
	@Getter
	@Setter
	private long timestamp;

	/**
	 * 成否
	 */
	@Getter
	@Setter
	private boolean valid;

	/**
	 * コンストラクタ
	 */
	public GlobalTrustCommandBase() {
		super();

		setCommandType(getCommandTypeInt());

		createCommandID();
		createRepeater2Callsign();
		createRepeater1Callsign();
		createYourCallsign();
		createMyCallsign();
		createMyCallsignAdd();

		this.setGatewayAddress(null);
		this.clearTimestamp();

		setValid(false);
	}

	public void clear() {
		Arrays.fill(this.repeater2Callsign, (char)0x20);
		Arrays.fill(this.repeater1Callsign, (char)0x20);
		Arrays.fill(this.yourCallsign, (char)0x20);
		Arrays.fill(this.myCallsign, (char)0x20);
		Arrays.fill(this.myCallsignAdd, (char)0x20);
		this.setGatewayAddress(null);
		this.clearTimestamp();
		this.setValid(false);
	}

	@Override
	public GlobalTrustCommand clone() {
		GlobalTrustCommandBase copy = null;
		try {
			copy = (GlobalTrustCommandBase)super.clone();

			copy.createCommandID();
			ArrayUtil.copyOf(copy.commandID, this.commandID);

			copy.commandType = this.commandType;

			copy.createRepeater2Callsign();
			ArrayUtil.copyOf(copy.repeater2Callsign, this.repeater2Callsign);

			copy.createRepeater1Callsign();
			ArrayUtil.copyOf(copy.repeater1Callsign, this.repeater1Callsign);

			copy.createYourCallsign();
			ArrayUtil.copyOf(copy.yourCallsign, this.yourCallsign);

			copy.createMyCallsign();
			ArrayUtil.copyOf(copy.myCallsign, this.myCallsign);

			copy.createMyCallsignAdd();
			ArrayUtil.copyOf(copy.myCallsignAdd, this.myCallsignAdd);

			if(this.gatewayAddress != null)
				copy.gatewayAddress =  SerializationUtils.clone(this.gatewayAddress);

			if(this.remoteAddress != null)
				copy.remoteAddress = SerializationUtils.clone(this.remoteAddress);

			copy.timestamp = this.timestamp;

			copy.valid = this.valid;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	@Override
	public int compareTo(GlobalTrustCommand o) {
		long diff = this.getTimestamp() - o.getTimestamp();
		if(diff > 0)
			return 1;
		else if(diff < 0)
			return -1;
		else
			return 0;
	}

	@Override
	public abstract byte[] assembleCommandData();

	@Override
	public abstract GlobalTrustCommand parseCommandData(ByteBuffer buffer);

	private void createCommandID() {
		this.commandID = new byte[2];
	}


	@Override
	public int getCommandIDInteger() {
		return ((commandID[1] << 8) & 0xFF00) | (commandID[0] & 0x00FF);
	}

	@Override
	public void setCommandIDInteger(int commandID) {
		this.commandID[0] = (byte)(commandID & 0xFF);
		this.commandID[1] = (byte)((commandID >> 8) & 0xFF);
	}

	/**
	 * コマンドIDをセットする
	 * @param commandID コマンドID
	 */
	protected void setCommandID(byte[] commandID) {
		ArrayUtil.copyOf(this.commandID, commandID);
	}


	private void createRepeater2Callsign() {
		this.repeater2Callsign = new char[DSTARDefines.CallsignFullLength];
	}


	@Override
	public void setRepeater2Callsign(char[] repeater2Callsign) {
		if(repeater2Callsign == null || repeater2Callsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.repeater2Callsign, repeater2Callsign);
	}

	private void createRepeater1Callsign() {
		this.repeater1Callsign = new char[DSTARDefines.CallsignFullLength];
	}


	@Override
	public void setRepeater1Callsign(char[] repeater1Callsign) {
		if(repeater1Callsign == null || repeater1Callsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.repeater1Callsign, repeater1Callsign);
	}

	private void createYourCallsign() {
		this.yourCallsign = new char[DSTARDefines.CallsignFullLength];
	}

	@Override
	public void setYourCallsign(char[] yourCallsign) {
		if(yourCallsign == null || yourCallsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.yourCallsign, yourCallsign);
	}

	private void createMyCallsign() {
		this.myCallsign = new char[DSTARDefines.CallsignFullLength];
	}

	@Override
	public void setMyCallsign(char[] myCallsign) {
		if(myCallsign == null || myCallsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.myCallsign, myCallsign);
	}

	private void createMyCallsignAdd() {
		this.myCallsignAdd = new char[DSTARDefines.CallsignShortLength];
	}

	@Override
	public void setMyCallsignAdd(char[] myCallsignAdd) {
		if(myCallsignAdd == null || myCallsignAdd.length <= 0) {return;}

		ArrayUtil.copyOf(this.myCallsignAdd, myCallsignAdd);
	}

	@Override
	public void updateTimestamp() {
		this.timestamp = System.currentTimeMillis();
	}

	@Override
	public void clearTimestamp() {
		this.setTimestamp(0);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuffer sb = new StringBuffer(indent);
		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]:");

		sb.append(String.format("CommandID=0x%04X", getCommandIDInteger()));

		sb.append("/MY=");
		try {
			sb.append(getMyCallsign());
		}catch(UnsupportedOperationException ex) {sb.append("null");}
		sb.append("/UR=");
		try {
			sb.append(getYourCallsign());
		}catch(UnsupportedOperationException ex) {sb.append("null");}
		sb.append("/RPT1=");
		try {
			sb.append(getRepeater1Callsign());
		}catch(UnsupportedOperationException ex) {sb.append("null");}
		sb.append("/RPT2=");
		try {
			sb.append(getRepeater2Callsign());
		}catch(UnsupportedOperationException ex) {sb.append("null");}

		sb.append("/GatewayAddress=");
		if(getGatewayAddress() != null)
			sb.append(getGatewayAddress().getHostAddress());
		else
			sb.append("null");

		sb.append("/RemoteAddress=");
		if(getRemoteAddress() != null)
			sb.append(getRemoteAddress().toString());
		else
			sb.append("null");

		sb.append("/Valid=");
		sb.append(isValid());

		return sb.toString();
	}

	protected abstract CommandType getCommandTypeInt();

}
