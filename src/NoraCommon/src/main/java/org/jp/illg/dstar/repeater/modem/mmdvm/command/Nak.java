package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class Nak extends MMDVMCommandBase {

	@Getter
	@Setter
	private MMDVMFrameType reasonCommand;

	@Getter
	@Setter
	private int reason;


	public Nak() {
		super();
	}

	@Override
	public Nak clone() {
		Nak copy = (Nak)super.clone();

		copy.reasonCommand = this.reasonCommand;
		copy.reason = this.reason;

		return copy;
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		if(buffer == null) {return false;}

		if(!parseReceiveDataIfValid(buffer)) {return false;}

		int dataLangth = getDataLength();
		byte[] data = getData();

		if(dataLangth < 2) {return false;}

		MMDVMFrameType type = MMDVMFrameType.getTypeByTypeCode(data[0]);
		setReasonCommand(type);
		setReason(data[1]);

		return true;
	}

	@Override
	public Optional<ByteBuffer> assembleCommand() {
		throw new UnsupportedOperationException();
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.NAK;
	}

}
