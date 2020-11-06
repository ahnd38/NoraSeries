package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;
import org.jp.illg.util.ArrayUtil;

import com.annimon.stream.Optional;

import lombok.Getter;

public class HashCommand extends RemoteControlCommandBase implements Cloneable{

	private static final int hashLength = 32;

	@Getter
	private byte[] hash;


	public HashCommand() {
		super(RemoteControlCommandType.HASH);

		hash = new byte[hashLength];
		Arrays.fill(hash, (byte)0x0);
	}

	@Override
	public HashCommand clone() {
		HashCommand copy = null;
		copy = (HashCommand)super.clone();

		copy.hash = new byte[hashLength];
		ArrayUtil.copyOf(copy.hash, hash);

		return copy;
	}

	@Override
	protected String getHeader() {
		return "SHA";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		if(srcBuffer == null || srcBuffer.remaining() < hash.length)
			return false;

		for(int c = 0; c < hash.length; c++)
			hash[c] = srcBuffer.get();

		return true;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		return Optional.empty();
	}

}
