package org.jp.illg.dstar.model;

import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.VoiceCodecType;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.FormatUtil;

import lombok.NonNull;

public abstract class VoiceBase implements VoiceData, Cloneable {

	public static final byte[] lastVoiceSegment =
		new byte[]{(byte)0x55,(byte)0xC8,(byte)0x7A,(byte)0x55,(byte)0x55,(byte)0x55,(byte)0x55,(byte)0x55,(byte)0x55};

	/**
	 * Voice Segment
	 */
	private byte[] voiceSegment;

	/**
	 * Data Segment
	 */
	private byte[] dataSegment;


	private VoiceBase() {
		 dataSegment = new byte[DSTARDefines.DataSegmentLength];
	}

	protected VoiceBase(final int voiceSegmentLength) {
		this();

		if(voiceSegmentLength < 1)
			throw new IllegalArgumentException("voice segment length must have > 1");

		voiceSegment = new byte[voiceSegmentLength];

		clear();
	}

	protected VoiceBase(
		@NonNull final byte[] voiceSegmentData, final byte[] dataSegmentData
	) {
		this(voiceSegmentData.length);

		if(dataSegmentData != null && dataSegmentData.length < this.dataSegment.length)
			throw new IllegalArgumentException("Data segment must have length > " + this.dataSegment.length);

		ArrayUtil.copyOf(this.voiceSegment, voiceSegmentData);
		ArrayUtil.copyOf(
			this.dataSegment,
			dataSegmentData != null ? dataSegmentData : DSTARDefines.SlowdataNullBytes
		);
	}

	protected VoiceBase(
		@NonNull final byte[] voiceSegmentData
	) {
		this(voiceSegmentData, null);
	}

	@Override
	public VoiceBase clone() {
		VoiceBase copy = null;

		try {
			copy = (VoiceBase)super.clone();

			copy.voiceSegment = Arrays.copyOf(voiceSegment, voiceSegment.length);
			copy.dataSegment = Arrays.copyOf(dataSegment, dataSegment.length);

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	public void clear() {
		Arrays.fill(getVoiceSegment(), (byte)0x00);
		Arrays.fill(getDataSegment(), (byte)0x00);
	}

	public byte[] getVoiceSegment() {
		return voiceSegment;
	}

	public void setVoiceSegment(byte[] voiceSegment) {
		if(voiceSegment == null || voiceSegment.length <= 0) {return;}

		ArrayUtil.copyOf(getVoiceSegment(), voiceSegment);
	}

	public byte[] getDataSegment() {
		return dataSegment;
	}

	public void setDataSegment(byte[] dataSegment) {
		if(dataSegment == null || dataSegment.length <= 0) {return;}

		ArrayUtil.copyOf(getDataSegment(), dataSegment);
	}

	public abstract VoiceCodecType getVoiceCodecType();

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
		sb.append("VoiceData=");
		sb.append(FormatUtil.bytesToHex(getVoiceSegment()));

		sb.append("/");
		sb.append("SlowData=");
		sb.append(FormatUtil.bytesToHex(getDataSegment()));

		return sb.toString();
	}
}
