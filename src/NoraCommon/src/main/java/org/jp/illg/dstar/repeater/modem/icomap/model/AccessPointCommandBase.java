/**
 *
 */
package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;


/**
 * @author AHND
 *
 */
public abstract class AccessPointCommandBase
implements AccessPointCommand,Cloneable{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	private DVPacket dvPacket;


	/**
	 * コンストラクタ
	 */
	public AccessPointCommandBase() {
		super();

		setCreatedTimestamp(System.currentTimeMillis());

		this.dvPacket = null;
	}

	@Override
	public AccessPointCommandBase clone() {
		AccessPointCommandBase copy = null;

		try {
			copy = (AccessPointCommandBase)super.clone();

			if(dvPacket != null)
				copy.dvPacket = dvPacket.clone();

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}


	public Header getDvHeader() {
		return getDvPacket() != null ? getDvPacket().getRfHeader() : null;
	}

	public void setDvHeader(Header header) {
		if(getDvPacket() != null)
			getDvPacket().setRfHeader(header);
	}

	public VoiceData getVoiceData() {
		return getDvPacket() != null ? getDvPacket().getVoiceData() : null;
	}

	public void setVoiceData(VoiceData voice) {
		if(getDvPacket() != null)
			getDvPacket().setVoiceData(voice);
	}

	@Override
	public BackBoneHeader getBackBone() {
		return getDvPacket() != null ? getDvPacket().getBackBone() : null;
	}

	@Override
	public void setBackBone(BackBoneHeader backBone) {
		if(getDvPacket() != null)
			getDvPacket().setBackBone(backBone);
	}

	@Override
	public DVPacket getDvPacket() {
		return dvPacket;
	}

	protected void setDvPacket(final DVPacket packet) {
		this.dvPacket = packet;
	}

	@Override
	public char[] getYourCallsign() {
		return getDvPacket() != null ? getDvHeader().getYourCallsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getRepeater1Callsign() {
		return getDvPacket() != null ? getDvHeader().getRepeater1Callsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getRepeater2Callsign() {
		return getDvPacket() != null ? getDvHeader().getRepeater2Callsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getMyCallsign() {
		return getDvPacket() != null ? getDvHeader().getMyCallsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getMyCallsignAdd() {
		return getDvPacket() != null ? getDvHeader().getMyCallsignAdd() : DSTARDefines.EmptyShortCallsignChar;
	}

	@Override
	public byte[] getVoiceSegment() {
		return getDvPacket() != null ? getVoiceData().getVoiceSegment() : null;
	}

	@Override
	public byte[] getDataSegment() {
		return getDvPacket() != null ? getVoiceData().getDataSegment() : null;
	}

	@Override
	public boolean isEndPacket() {
		//フレームの終わりか？
		return
//				(
//					this.getVoiceData().getVoiceSegment()[0] == (byte)0x55 &&
//					this.getVoiceData().getVoiceSegment()[1] == (byte)0xC8 &&
//					this.getVoiceData().getVoiceSegment()[2] == (byte)0x7A
//				) ||
				(getDvPacket() != null ? getDvPacket().getBackBone().isEndSequence() : false);
	}

	/**
	 * 送信コマンドデータを取得する
	 * @return
	 */
	public abstract byte[] assembleCommandData();

	/**
	 * 受信データコマンドを解析し、パースする
	 * @param buffer
	 * @return
	 */
	public abstract AccessPointCommand analyzeCommandData(ByteBuffer buffer);

	@Override
	public String toString() {
		final StringBuilder sb =
			new StringBuilder("[" + this.getClass().getSimpleName() + "]:");

		sb.append("DVPacket=\n");
		sb.append(getDvPacket() != null ? getDvPacket().toString(4) : "NULL");

		return sb.toString();
	}

}
