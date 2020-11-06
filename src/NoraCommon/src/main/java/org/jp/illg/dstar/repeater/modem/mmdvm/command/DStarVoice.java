package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;
import org.jp.illg.util.ArrayUtil;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class DStarVoice extends MMDVMCommandBase {

	@Getter
	@Setter
	private VoiceData voice;

	@Getter
	@Setter
	private boolean rssiPresent;

	@Getter
	@Setter
	private int rssi;

	public DStarVoice() {
		super();

		setVoice(null);
		setRssiPresent(false);
		setRssi(0);
	}

	@Override
	public DStarVoice clone() {
		DStarVoice copy = (DStarVoice)super.clone();

		if(this.voice != null)
			copy.voice = this.voice.clone();

		copy.rssiPresent = this.rssiPresent;
		copy.rssi = this.rssi;

		return copy;
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		if(buffer == null) {return false;}

		if(!parseReceiveDataIfValid(buffer)) {return false;}

		if(getDataLength() < DSTARDefines.DvFrameLength) {return false;}

		VoiceAMBE voice = new VoiceAMBE();
		ArrayUtil.copyOfRange(voice.getVoiceSegment(), getData(), 0, DSTARDefines.VoiceSegmentLength);
		ArrayUtil.copyOfRange(
				voice.getDataSegment(),
				getData(),
				DSTARDefines.VoiceSegmentLength,
				DSTARDefines.VoiceSegmentLength + DSTARDefines.DataSegmentLength
		);

		if(getDataLength() >= 15) {
			setRssiPresent(true);

			int rssi = ((getData()[13] << 8) & 0xFF00) | (getData()[14] & 0x00FF);
			setRssi(rssi);
		}

		setVoice(voice);

		return true;
	}

	@Override
	public Optional<ByteBuffer> assembleCommand() {
		if(getVoice() == null) {return Optional.empty();}

		byte[] buf = new byte[15];

		buf[0] = MMDVMDefine.FRAME_START;
		buf[1] = (byte) buf.length;
		buf[2] = getCommandType().getTypeCode();

		ArrayUtil.copyOfRange(buf, 3, getVoice().getVoiceSegment());
		ArrayUtil.copyOfRange(buf, 3 + DSTARDefines.VoiceSegmentLength, getVoice().getDataSegment());

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.DSTAR_DATA;
	}

}
