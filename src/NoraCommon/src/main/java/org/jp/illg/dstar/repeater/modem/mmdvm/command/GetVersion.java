package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;

import com.annimon.stream.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class GetVersion extends MMDVMCommandBase{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int protocolVersion;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String version;

	public GetVersion() {
		super();
	}

	@Override
	public GetVersion clone() {
		GetVersion copy = (GetVersion)super.clone();

		copy.protocolVersion = this.protocolVersion;

		copy.version = new String(version);

		return copy;
	}

	@Override
	public boolean isValidCommand(ByteBuffer buffer) {
		if(buffer == null) {return false;}

		if(!parseReceiveDataIfValid(buffer)) {return false;}

		int dataLength = getDataLength();
		byte[] data= getData();

		if(dataLength < 1) {return false;}

		int protocolVersion = data[0];

		StringBuilder sb = new StringBuilder("");
		for(int i = 1; i < dataLength; i++) {sb.append((char)data[i]);}

		setProtocolVersion(protocolVersion);
		setVersion(sb.toString());

		return true;
	}

	@Override
	public Optional<ByteBuffer> assembleCommand() {
		byte[] buf = new byte[3];

		buf[0] = MMDVMDefine.FRAME_START;
		buf[1] = (byte)buf.length;
		buf[2] = MMDVMFrameType.GET_VERSION.getTypeCode();

		return Optional.of(ByteBuffer.wrap(buf));
	}

	@Override
	public MMDVMFrameType getCommandType() {
		return MMDVMFrameType.GET_VERSION;
	}

}
