package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class NakCommand extends RemoteControlCommandBase implements Cloneable{

	@Getter
	@Setter
	private String message;

	public NakCommand() {
		super(RemoteControlCommandType.NAK);
	}

	@Override
	public NakCommand clone() {
		NakCommand copy = null;
		copy = (NakCommand)super.clone();

		copy.message = message;

		return copy;
	}

	@Override
	protected String getHeader() {
		return "NAK";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		return false;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		if(message == null) {message = "";}

		char[] mes = message.toCharArray();
		int mesLength = mes.length < 100 ? mes.length : 100;

		int dataLength = mesLength + 1;

		byte[] dstBuffer = new byte[dataLength];
		Arrays.fill(dstBuffer, (byte)0x0);

		for(int i = 0; i < mesLength; i++)
			dstBuffer[i] = (byte)mes[i];

		return Optional.of(dstBuffer);
	}

}
