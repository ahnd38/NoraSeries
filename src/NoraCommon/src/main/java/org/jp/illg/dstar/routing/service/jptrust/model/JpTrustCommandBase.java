package org.jp.illg.dstar.routing.service.jptrust.model;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.lang3.SerializationUtils;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.util.ArrayUtil;

import lombok.Getter;
import lombok.Setter;

public abstract class JpTrustCommandBase
implements JpTrustCommand{

	public static enum CommandType{
		Unknown((byte)0x00),
		RequestPositionUpdate((byte)0x01),
		RequestPositionInformation((byte)0x02),
		RequestAreaPositionInformation((byte)0x04),
		GatewayIPUpdate((byte)0x06),
		HeaderPacket((byte)0x10),
		VoiceData((byte)0x20),
		PositionUpdateExtend((byte)0x11),
		PositionQueryExtend((byte)0x12),
		GatewayIPUpdateExtend((byte)0x16),
		;

		private final byte val;

		CommandType(final byte val) {
			this.val = val;
		}

		public byte getValue() {
			return this.val;
		}

		public static CommandType getTypeByValue(byte value) {
			for(CommandType v : values()) {
				if(v.getValue() == value) {return v;}
			}
			return CommandType.Unknown;
		}
	}

	private static final Random randomGen;


	/**
	 * コマンドID
	 * (管理サーバ用)
	 */
	private byte[] commandID;

	/**
	 * コマンドタイプ
	 */
	private CommandType commandType;

	/**
	 * Repeater 2 Callsign
	 */
	private char[] repeater2Callsign;

	/**
	 * Repeater 1 Callsign
	 */
	private char[] repeater1Callsign;

	/**
	 * Your Callsign
	 */
	private char[] yourCallsign;

	/**
	 * My Callsign
	 */
	private char[] myCallsign;

	/**
	 * My Callsign Additional
	 */
	private char[] myCallsignAdd;

	/**
	 * ゲートウェイアドレス
	 */
	private InetAddress gatewayAddress;

	/**
	 * リモートアドレス
	 */
	private InetSocketAddress remoteAddress;

	/**
	 * タイムスタンプ
	 */
	private long timestamp;

	@Getter
	@Setter
	private JpTrustResult result;

	static{
		randomGen = new Random(System.currentTimeMillis() ^ 0x43FDC4F4);
	}

	/**
	 * コンストラクタ
	 */
	public JpTrustCommandBase() {
		super();

		createCommandID();
		createRepeater2Callsign();
		createRepeater1Callsign();
		createYourCallsign();
		createMyCallsign();
		createMyCallsignAdd();

		this.setCommandType(CommandType.Unknown);
		this.setGatewayAddress(null);
		this.clearTimestamp();

		setResult(JpTrustResult.NowDisable);
	}

	public void clear() {
		Arrays.fill(this.repeater2Callsign, (char)0x20);
		Arrays.fill(this.repeater1Callsign, (char)0x20);
		Arrays.fill(this.yourCallsign, (char)0x20);
		Arrays.fill(this.myCallsign, (char)0x20);
		Arrays.fill(this.myCallsignAdd, (char)0x20);
		this.setGatewayAddress(null);
		this.clearTimestamp();
	}

	@Override
	public JpTrustCommand clone() {
		JpTrustCommandBase copy = null;
		try {
			copy = (JpTrustCommandBase)super.clone();

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

			copy.result = result;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	@Override
	public int compareTo(JpTrustCommand o) {
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
	public abstract JpTrustCommand parseCommandData(ByteBuffer buffer);

	private void createCommandID() {
		this.commandID = new byte[2];
	}

	@Override
	public byte[] getCommandID() {
		return commandID;
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

	@Override
	public CommandType getCommandType() {
		return commandType;
	}

	/**
	 * コマンドタイプをセットする
	 * @param commandType コマンドタイプ
	 */
	protected void setCommandType(CommandType commandType) {
		this.commandType = commandType;
	}

	private void createRepeater2Callsign() {
		this.repeater2Callsign = new char[DSTARDefines.CallsignFullLength];
	}

	@Override
	public char[] getRepeater2Callsign() {
		return repeater2Callsign;
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
	public char[] getRepeater1Callsign() {
		return repeater1Callsign;
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
	public char[] getYourCallsign() {
		return yourCallsign;
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
	public char[] getMyCallsign() {
		return myCallsign;
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
	public char[] getMyCallsignAdd() {
		return myCallsignAdd;
	}

	@Override
	public void setMyCallsignAdd(char[] myCallsignAdd) {
		if(myCallsignAdd == null || myCallsignAdd.length <= 0) {return;}

		ArrayUtil.copyOf(this.myCallsignAdd, myCallsignAdd);
	}

	@Override
	public InetAddress getGatewayAddress() {
		return gatewayAddress;
	}

	@Override
	public void setGatewayAddress(InetAddress address) {
		this.gatewayAddress = address;
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return remoteAddress;
	}

	@Override
	public void setRemoteAddress(InetSocketAddress remoteAddress) {
		this.remoteAddress = remoteAddress;
	}

	@Override
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public long getTimestamp() {
		return this.timestamp;
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
		StringBuffer sb = new StringBuffer("[" + this.getClass().getSimpleName() + "]:");

		sb.append(String.format("CommandID=0x%04X", getCommandIDInteger()));

		try {
			final char[] myCallsign = getMyCallsign();
			sb.append("/MY=");
			sb.append(myCallsign);
		}catch(UnsupportedOperationException ex) {}

		try {
			final char[] yourCallsign = getYourCallsign();
			sb.append("/UR=");
			sb.append(yourCallsign);
		}catch(UnsupportedOperationException ex) {}

		try {
			final char[] repeater1Callsign = getRepeater1Callsign();
			sb.append("/RPT1=");
			sb.append(repeater1Callsign);
		}catch(UnsupportedOperationException ex) {}

		try {
			final char[] repeater2Callsign = getRepeater2Callsign();
			sb.append("/RPT2=");
			sb.append(repeater2Callsign);
		}catch(UnsupportedOperationException ex) {}

		if(getGatewayAddress() != null) {
			sb.append("/GatewayAddress=");
			sb.append(getGatewayAddress().getHostAddress());
		}

		if(getRemoteAddress() != null) {
			sb.append("/RemoteAddress=");
			sb.append(getRemoteAddress().toString());
		}

		return sb.toString();
	}

	protected static int generateCommandID() {
		return randomGen.nextInt(0xFFFF + 0x0001);
	}
}
