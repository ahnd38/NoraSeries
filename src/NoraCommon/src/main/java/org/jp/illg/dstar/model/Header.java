package org.jp.illg.dstar.model;

import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.util.DSTARCRCCalculator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.FormatUtil;

import lombok.Getter;
import lombok.NonNull;

public class Header implements Cloneable{

	/**
	 * Flags
	 */
	private byte[] flags = new byte[3];

	/**
	 * Repeater 2 Callsign
	 */
	private char[] repeater2Callsign = new char[DSTARDefines.CallsignFullLength];

	/**
	 * Repeater 1 Callsign
	 */
	private char[] repeater1Callsign = new char[DSTARDefines.CallsignFullLength];

	/**
	 * Your Callsign
	 */
	private char[] yourCallsign = new char[DSTARDefines.CallsignFullLength];

	/**
	 * My Callsign
	 */
	private char[] myCallsign = new char[DSTARDefines.CallsignFullLength];

	/**
	 * My Callsign Additional
	 */
	private char[] myCallsignAdd = new char[DSTARDefines.CallsignShortLength];

	/**
	 * CRC
	 */
	private byte[] crc = new byte[2];

	/**
	 * Source Repeater 1 Callsign
	 */
	@Getter
	private char[] sourceRepeater1Callsign = new char[DSTARDefines.CallsignFullLength];

	/**
	 * Source Repeater 2 Callsign
	 */
	@Getter
	private char[] sourceRepeater2Callsign = new char[DSTARDefines.CallsignFullLength];


	public Header() {
		super();

		clear();
	}

	public Header(
		final char[] yourCallsign,
		final char[] repeater1Callsign, final char[] repeater2Callsign,
		final char[] myCallsign, final char[] myCallsignAdd
	) {
		this();

		setYourCallsign(yourCallsign);
		setRepeater1Callsign(repeater1Callsign);
		setRepeater2Callsign(repeater2Callsign);
		setMyCallsign(myCallsign);
		setMyCallsignAdd(myCallsignAdd);
	}

	public Header(@NonNull final Header header) {
		this();

		clone(this, header);
	}

	@Override
	public Header clone() {
		Header copy = null;
		try {
			copy = (Header)super.clone();

			clone(copy, this);

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	public void clear() {
		Arrays.fill(this.getFlags(), (byte)0x00);
		Arrays.fill(this.getRepeater2Callsign(), ' ');
		Arrays.fill(this.getRepeater1Callsign(), ' ');
		Arrays.fill(this.getYourCallsign(), ' ');
		Arrays.fill(this.getMyCallsign(), ' ');
		Arrays.fill(this.getMyCallsignAdd(), ' ');
		Arrays.fill(this.getCrc(), (byte)0x00);
		Arrays.fill(this.getSourceRepeater1Callsign(), ' ');
		Arrays.fill(this.getSourceRepeater2Callsign(), ' ');
	}

	public byte[] getFlags() {
		return flags;
	}

	public void setFlags(@NonNull final byte[] flags) {
		if(flags == null || flags.length <= 0) {return;}

		ArrayUtil.copyOf(this.flags, flags);
	}

	/*
	 * Flag1
	 */
	public void setRepeaterControlFlag(@NonNull final RepeaterControlFlag flag) {
		this.flags[0] = (byte)((this.flags[0] & ~RepeaterControlFlag.getMask()) | flag.getValue());
	}

	public boolean isSetRepeaterControlFlag(@NonNull final RepeaterControlFlag flag) {
		return (this.flags[0] & RepeaterControlFlag.getMask()) == flag.getValue();
	}

	public RepeaterControlFlag getRepeaterControlFlag() {
		return RepeaterControlFlag.getTypeByValue(this.flags[0]);
	}

	public void setRepeaterRouteFlag(@NonNull final RepeaterRoute route) {
		this.flags[0] = (byte)((this.flags[0] & ~(RepeaterRoute.getMask())) | route.getValue());
	}

	public boolean isSetRepeaterRouteFlag(@NonNull final RepeaterRoute route) {
		return (this.flags[0] & RepeaterRoute.getMask()) == route.getValue();
	}

	public RepeaterRoute getRepeaterRouteFlag() {
		return RepeaterRoute.getTypeByValue(this.flags[0]);
	}

	public void setInterruptFlag(final boolean interrupt) {
		this.flags[0] = (byte)(interrupt ? this.flags[0] | 0x20 : this.flags[0] & ~0x20);
	}

	public boolean isInterruptFlag() {
		return (this.flags[0] & 0x20) != 0x0;
	}

	public void setControlSignal(final boolean isControlSignal) {
		this.flags[0] = (byte)(isControlSignal ? this.flags[0] | 0x10 : this.flags[0] & ~0x10);
	}

	public boolean isControlSignal() {
		return (this.flags[0] & 0x10) != 0x0;
	}

	public void setEmergencySignal(final boolean isEmergencySignal) {
		this.flags[0] = (byte)(isEmergencySignal ? this.flags[0] | 0x08 : this.flags[0] & ~0x08);
	}

	public boolean isEmergencySignal() {
		return (this.flags[0] & 0x08) != 0x0;
	}

	/*
	 * Flag2
	 */
	public void setIDFlag(final byte idFlag) {
		this.flags[1] = (byte)((this.flags[1] & ~0xF0) | ((idFlag << 4) & 0xF0));
	}

	public byte getIDFlag() {
		return (byte)((this.flags[1] & 0xF0) >> 4);
	}

	public void setMFlag(final byte mFlag) {
		this.flags[1] = (byte)((this.flags[1] & ~0x0F) | (mFlag & 0x0F));
	}

	public byte getMFlag() {
		return (byte)(this.flags[1] & 0x0F);
	}


	public char[] getRepeater2Callsign() {
		return repeater2Callsign;
	}

	public String getRepeater2CallsignString() {
		return String.valueOf(repeater2Callsign);
	}

	public void setRepeater2Callsign(@NonNull final char[] callsign) {
		if(callsign == null || callsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.repeater2Callsign, callsign);
	}

	public void setRepeater2Callsign(@NonNull final String callsign) {
		if(callsign == null || callsign.length() <= 0) {return;}

		setRepeater2Callsign(callsign.toCharArray());
	}


	public char[] getRepeater1Callsign() {
		return repeater1Callsign;
	}

	public String getRepeater1CallsignString() {
		return String.valueOf(repeater1Callsign);
	}

	public void setRepeater1Callsign(@NonNull final char[] callsign) {
		if(callsign == null || callsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.repeater1Callsign, callsign);
	}

	public void setRepeater1Callsign(@NonNull final String callsign) {
		if(callsign == null || callsign.length() <= 0) {return;}

		setRepeater1Callsign(callsign.toCharArray());
	}


	public char[] getYourCallsign() {
		return yourCallsign;
	}

	public String getYourCallsignString() {
		return String.valueOf(yourCallsign);
	}

	public void setYourCallsign(@NonNull final char[] callsign) {
		if(callsign == null || callsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.yourCallsign, callsign);
	}

	public void setYourCallsign(@NonNull final String callsign) {
		if(callsign == null || callsign.length() <= 0) {return;}

		setYourCallsign(callsign.toCharArray());
	}


	public char[] getMyCallsign() {
		return myCallsign;
	}

	public String getMyCallsignString() {
		return String.valueOf(myCallsign);
	}

	public void setMyCallsign(@NonNull final char[] callsign) {
		if(callsign == null || callsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.myCallsign, callsign);
	}

	public void setMyCallsign(@NonNull final String callsign) {
		if(callsign == null || callsign.length() <= 0) {return;}

		setMyCallsign(callsign.toCharArray());
	}


	public char[] getMyCallsignAdd() {
		return myCallsignAdd;
	}

	public String getMyCallsignAddString() {
		return String.valueOf(myCallsignAdd);
	}

	public void setMyCallsignAdd(@NonNull final char[] callsign) {
		if(callsign == null || callsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.myCallsignAdd, callsign);
	}

	public void setMyCallsignAdd(@NonNull final String callsign) {
		if(callsign == null || callsign.length() <= 0) {return;}

		setMyCallsignAdd(callsign.toCharArray());
	}


	public byte[] getCrc() {
		return crc;
	}

	public int getCrcInt() {
		return ((crc[1] << 8) & 0xFF00) | (crc[0] & 0x00FF);
	}

	public void setCrc(@NonNull final byte[] crc) {
		if(crc == null || crc.length <= 0) {return;}

		ArrayUtil.copyOf(this.crc, crc);
	}

	public void setCrcInt(final int crc) {
		this.crc[0] = (byte)(crc & 0xFF);
		this.crc[1] = (byte)((crc >> 8) & 0xFF);
	}


	public String getSourceRepeater1CallsignString() {
		return String.valueOf(sourceRepeater1Callsign);
	}

	public void setSourceRepeater1Callsign(@NonNull final char[] callsign) {
		if(callsign == null || callsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.sourceRepeater1Callsign, callsign);
	}

	public void setSourceRepeater1Callsign(@NonNull final String callsign) {
		if(callsign == null || callsign.length() <= 0) {return;}

		setSourceRepeater1Callsign(callsign.toCharArray());
	}


	public String getSourceRepeater2CallsignString() {
		return String.valueOf(sourceRepeater2Callsign);
	}

	public void setSourceRepeater2Callsign(@NonNull final char[] callsign) {
		if(callsign == null || callsign.length <= 0) {return;}

		ArrayUtil.copyOf(this.sourceRepeater2Callsign, callsign);
	}

	public void setSourceRepeater2Callsign(@NonNull final String callsign) {
		if(callsign == null || callsign.length() <= 0) {return;}

		setSourceRepeater2Callsign(callsign.toCharArray());
	}


	public void saveRepeaterCallsign() {
		setSourceRepeater1Callsign(getRepeater1Callsign());
		setSourceRepeater2Callsign(getRepeater2Callsign());
	}

	public void replaceCallsignsIllegalCharToSpace() {
		replaceCallsignIllegalCharToSpace(getMyCallsign());
		replaceCallsignIllegalCharToSpace(getMyCallsignAdd());
		replaceCallsignIllegalCharToSpace(getYourCallsign());
		replaceCallsignIllegalCharToSpace(getRepeater1Callsign());
		replaceCallsignIllegalCharToSpace(getRepeater2Callsign());
	}

	public int calcCRC() {
		final byte[] data = new byte[39];
		ArrayUtil.copyOfRange(data, 0, getFlags());
		ArrayUtil.copyOfRange(data, 3, getRepeater2Callsign());
		ArrayUtil.copyOfRange(data, 11, getRepeater1Callsign());
		ArrayUtil.copyOfRange(data, 19, getYourCallsign());
		ArrayUtil.copyOfRange(data, 27, getMyCallsign());
		ArrayUtil.copyOfRange(data, 35, getMyCallsignAdd());

		final int crc = DSTARUtils.calcCRC(data, data.length);

		setCrcInt(crc);

		return crc;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(flags);
		result = prime * result + Arrays.hashCode(myCallsign);
		result = prime * result + Arrays.hashCode(myCallsignAdd);
		result = prime * result + Arrays.hashCode(repeater1Callsign);
		result = prime * result + Arrays.hashCode(repeater2Callsign);
		result = prime * result + Arrays.hashCode(yourCallsign);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Header other = (Header) obj;
		if (!Arrays.equals(flags, other.flags))
			return false;
		if (!Arrays.equals(myCallsign, other.myCallsign))
			return false;
		if (!Arrays.equals(myCallsignAdd, other.myCallsignAdd))
			return false;
		if (!Arrays.equals(repeater1Callsign, other.repeater1Callsign))
			return false;
		if (!Arrays.equals(repeater2Callsign, other.repeater2Callsign))
			return false;
		if (!Arrays.equals(yourCallsign, other.yourCallsign))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indent) {
		if(indent < 0) {indent = 0;}

		final StringBuilder sb = new StringBuilder();

		FormatUtil.addIndent(sb, indent);

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]");

		sb.append('\n');
		FormatUtil.addIndent(sb, indent + 4);

		sb.append("Flags=");
		sb.append(String.format("0x%02X 0x%02X 0x%02X", getFlags()[0], getFlags()[1], getFlags()[2]));

		sb.append('/');

		sb.append("MY=");
		sb.append(getMyCallsign());
		sb.append("_");
		sb.append(getMyCallsignAdd());

		sb.append('/');

		sb.append("UR=");
		sb.append(getYourCallsign());

		sb.append('/');

		sb.append("RPT1=");
		sb.append(getRepeater1Callsign());
		sb.append('(');
		sb.append(getSourceRepeater1Callsign());
		sb.append(')');

		sb.append('/');

		sb.append("RPT2=");
		sb.append(getRepeater2Callsign());
		sb.append('(');
		sb.append(getSourceRepeater2Callsign());
		sb.append(')');

		sb.append('/');

		sb.append("CRC=");
		sb.append(String.format("0x%04X", getCrcInt()));

		sb.append('\n');

		FormatUtil.addIndent(sb, indent + 4);
		sb.append("Flag1:(");
		sb.append("RepeaterControlFlag=");
		sb.append(getRepeaterControlFlag());
		sb.append('/');
		sb.append("RepeaterRouteFlag=");
		sb.append(getRepeaterRouteFlag());
		sb.append('/');
		sb.append("Interrupt=");
		sb.append(isInterruptFlag());
		sb.append('/');
		sb.append("EmergencySignal=");
		sb.append(isEmergencySignal());
		sb.append(')');

		sb.append('/');

		sb.append("Flag2:(");
		sb.append("IDFlag=0x");
		sb.append(String.format("%X", getIDFlag()));
		sb.append('/');
		sb.append("MFlag=0x");
		sb.append(String.format("%X", getMFlag()));
		sb.append(')');

		return sb.toString();
	}

	public boolean isValidCRC() {
		final DSTARCRCCalculator calculator = new DSTARCRCCalculator();

		for(int i = 0; i < flags.length; i++)
			calculator.updateCRC(flags[i]);

		for(int i = 0; i < repeater2Callsign.length; i++)
			calculator.updateCRC((byte)repeater2Callsign[i]);

		for(int i = 0; i < repeater1Callsign.length; i++)
			calculator.updateCRC((byte)repeater1Callsign[i]);

		for(int i = 0; i < yourCallsign.length; i++)
			calculator.updateCRC((byte)yourCallsign[i]);

		for(int i = 0; i < myCallsign.length; i++)
			calculator.updateCRC((byte)myCallsign[i]);

		for(int i = 0; i < myCallsignAdd.length; i++)
			calculator.updateCRC((byte)myCallsignAdd[i]);

		return getCrcInt() == calculator.getResultCRC();
	}

	private void replaceCallsignIllegalCharToSpace(@NonNull char[] callsign) {
		final String callsignStr = String.valueOf(callsign);

		final String formatedCallsign =
			DSTARUtils.replaceCallsignIllegalCharToSpace(callsignStr);

		ArrayUtil.copyOf(callsign, formatedCallsign.toCharArray());
	}

	private static void clone(final Header dst, final Header src) {
		dst.flags = Arrays.copyOf(src.flags, src.flags.length);

		dst.repeater2Callsign = Arrays.copyOf(src.repeater2Callsign, src.repeater2Callsign.length);
		dst.repeater1Callsign = Arrays.copyOf(src.repeater1Callsign, src.repeater1Callsign.length);
		dst.yourCallsign = Arrays.copyOf(src.yourCallsign, src.yourCallsign.length);
		dst.myCallsign = Arrays.copyOf(src.myCallsign, src.myCallsign.length);
		dst.myCallsignAdd = Arrays.copyOf(src.myCallsignAdd, src.myCallsignAdd.length);

		dst.crc = Arrays.copyOf(src.crc, src.crc.length);

		dst.sourceRepeater1Callsign = Arrays.copyOf(src.sourceRepeater1Callsign, src.sourceRepeater1Callsign.length);
		dst.sourceRepeater2Callsign = Arrays.copyOf(src.sourceRepeater2Callsign, src.sourceRepeater2Callsign.length);
	}
}
