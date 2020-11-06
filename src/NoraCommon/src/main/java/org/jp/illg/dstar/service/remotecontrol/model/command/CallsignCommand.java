package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;

import com.annimon.stream.Optional;

import lombok.Getter;

public class CallsignCommand extends RemoteControlCommandBase {

	@Getter
	private List<String> repeaterCallsigns;

	public CallsignCommand() {
		super(RemoteControlCommandType.CALLSIGNS);

		repeaterCallsigns = new ArrayList<>();
	}

	@Override
	public CallsignCommand clone() {
		CallsignCommand copy = null;
		copy = (CallsignCommand)super.clone();

		copy.repeaterCallsigns = new ArrayList<>();
		if(repeaterCallsigns != null)
			for(String callsign : repeaterCallsigns) {copy.repeaterCallsigns.add(callsign);}

		return copy;
	}

	@Override
	protected String getHeader() {
		return "CAL";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		return false;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		int dataLength = repeaterCallsigns.size() * (DSTARDefines.CallsignFullLength + 1);
		byte[] buffer = new byte[dataLength];
		Arrays.fill(buffer, (byte)0x0);

		for(int i = 0; i < repeaterCallsigns.size(); i++) {
			int offset = i * (DSTARDefines.CallsignFullLength + 1);
			buffer[offset] = 'R'; offset+= 1;

			String callsign = repeaterCallsigns.get(i);

			char[] callsignChar = callsign.toCharArray();
			for(int c = 0; c < DSTARDefines.CallsignFullLength && c < callsignChar.length; c++)
				buffer[offset + c] = (byte)callsignChar[c];
		}

		return Optional.of(buffer);
	}

}
