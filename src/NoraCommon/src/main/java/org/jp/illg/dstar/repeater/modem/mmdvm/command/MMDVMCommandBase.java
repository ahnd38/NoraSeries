package org.jp.illg.dstar.repeater.modem.mmdvm.command;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMDefine;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;

import com.annimon.stream.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public abstract class MMDVMCommandBase implements MMDVMCommand, Cloneable {


	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private int frameLength;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private int dataLength;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private byte[] data;


	public MMDVMCommandBase() {
		super();
	}

	@Override
	public abstract boolean isValidCommand(ByteBuffer buffer);

	@Override
	public Optional<MMDVMCommand> parseCommand(ByteBuffer buffer) {
		if(buffer == null) {return Optional.empty();}

		if(isValidCommand(buffer))
			return Optional.of((MMDVMCommand)this);
		else
			return Optional.empty();
	}

	@Override
	public abstract Optional<ByteBuffer> assembleCommand();

	@Override
	public abstract MMDVMFrameType getCommandType();


	@Override
	public MMDVMCommandBase clone() {
		MMDVMCommandBase copy = null;

		try {
			copy = (MMDVMCommandBase)super.clone();

			copy.frameLength = this.frameLength;

			copy.dataLength = this.dataLength;

			copy.data = Arrays.copyOf(this.data, this.data.length);

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}


	protected boolean parseReceiveDataIfValid(ByteBuffer buffer) {
		if(buffer == null || buffer.remaining() < 3) {return false;}

		int savedPosition = buffer.position();
		boolean success = parseReceiveDataIfValidInternal(buffer);

		if(!success) {buffer.position(savedPosition);}

		return success;
	}

	private boolean parseReceiveDataIfValidInternal(ByteBuffer buffer) {
		assert buffer != null;

		byte frameStart = buffer.get();
		if(frameStart != MMDVMDefine.FRAME_START) {return false;}

		int frameLength = buffer.get();
		if(frameLength >= 250) {
			return false;
		}

		byte typeCode = buffer.get();
		MMDVMFrameType type = MMDVMFrameType.getTypeByTypeCode(typeCode);
		if(type != getCommandType()) {
			return false;
		}

		if(
			frameLength > 0 ||
			(frameLength == 0 && buffer.remaining() >= 2)
		) {
			if(frameLength == 0) {
				frameLength = buffer.get();
				frameLength = (frameLength << 8) | buffer.get();
			}
		}
		else {
			return false;
		}

		int dataLength;
		if(
				frameLength < 3 ||
				(dataLength = (frameLength - 3)) > buffer.remaining()
		) {
			return false;
		}

		byte[] data = new byte[dataLength];
		for(int i = 0; i < dataLength; i++) {data[i] = buffer.get();}

		setFrameLength(frameLength);
		setDataLength(dataLength);
		setData(data);

		buffer.compact();
		buffer.limit(buffer.position());
		buffer.rewind();

		return true;
	}

}
