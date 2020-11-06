package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;
import org.jp.illg.dstar.util.DSTARUtils;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class RandomCommand extends RemoteControlCommandBase implements Cloneable{

	@Getter
	@Setter
	private int randomValue;

	public RandomCommand() {
		super(RemoteControlCommandType.RANDOM);
	}

	@Override
	public RandomCommand clone() {
		RandomCommand copy = null;
		copy = (RandomCommand)super.clone();

		copy.randomValue = randomValue;

		return copy;
	}

	@Override
	protected String getHeader() {
		return "RND";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		return false;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		byte[] dstBuffer = new byte[4];
		DSTARUtils.writeInt32BigEndian(dstBuffer, 0, getRandomValue());

		return Optional.of(dstBuffer);
	}

}
