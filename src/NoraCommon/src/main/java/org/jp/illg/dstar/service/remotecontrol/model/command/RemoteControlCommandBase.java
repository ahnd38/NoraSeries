package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.lang3.SerializationUtils;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommand;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;
import org.jp.illg.util.ArrayUtil;

import com.annimon.stream.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public abstract class RemoteControlCommandBase implements RemoteControlCommand,Cloneable{


	@Getter
	@Setter(AccessLevel.PRIVATE)
	private RemoteControlCommandType type;

	@Setter
	@Getter
	private InetAddress remoteAddress;

	@Setter
	@Getter
	private int remotePort;

	@Override
	public RemoteControlCommandBase clone() {
		RemoteControlCommandBase copy = null;
		try {
			copy = (RemoteControlCommandBase)super.clone();

			copy.type = type;

			if(this.remoteAddress != null)
				copy.remoteAddress = SerializationUtils.clone(this.remoteAddress);

			copy.remotePort = remotePort;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	private RemoteControlCommandBase() {
		super();
	}

	protected RemoteControlCommandBase(RemoteControlCommandType type) {
		this();

		setType(type);
	}

	protected abstract String getHeader();

	@Override
	public Optional<RemoteControlCommand> isValidCommand(ByteBuffer srcBuffer) {
		if(srcBuffer == null || srcBuffer.remaining() <= 0) {return Optional.empty();}
		assert getHeader() != null && !"".equals(getHeader());

		int headerLength = getHeader().length();
		//バッファの長さがヘッダより短い場合にはfalse
		if(headerLength > srcBuffer.remaining()) {return Optional.empty();}

		int srcBufferPosSnapshot = srcBuffer.position();

		char[] srcHeader = new char[headerLength];
		for(int c = 0; c < srcHeader.length && 0 < srcBuffer.remaining(); c++)
			srcHeader[c] = (char)srcBuffer.get();

		if(Arrays.equals(getHeader().toCharArray(), srcHeader) && parseCommand(srcBuffer)) {
			return Optional.of((RemoteControlCommand)this);
		}else {
			srcBuffer.position(srcBufferPosSnapshot);
			return Optional.empty();
		}
	}

	protected abstract boolean parseCommand(ByteBuffer srcBuffer);

	@Override
	public Optional<byte[]> assembleCommand(){
		Optional<byte[]> cmdData = assembleCommandInt();

		if(cmdData.isPresent()) {
			int dataLength = getHeader().length() + cmdData.get().length;
			byte[] dstBuffer = new byte[dataLength];
			ArrayUtil.copyOfRange(dstBuffer, getHeader().getBytes(), 0, getHeader().length());
			ArrayUtil.copyOfRange(
					dstBuffer, cmdData.get(),
					getHeader().length(), dataLength,
					0, cmdData.get().length
			);

			return Optional.of(dstBuffer);
		}else {
			return Optional.empty();
		}
	}

	protected abstract Optional<byte[]> assembleCommandInt();

}
