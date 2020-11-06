package org.jp.illg.dstar.service.remotecontrol.model.command;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlCommandType;
import org.jp.illg.dstar.service.remotecontrol.model.RemoteControlRepeater;
import org.jp.illg.dstar.util.DSTARUtils;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;

public class RepeaterCommand extends RemoteControlCommandBase implements Cloneable{

	@Getter
	@Setter
	private RemoteControlRepeater remoteRepeater;

	public RepeaterCommand() {
		super(RemoteControlCommandType.REPEATERS);
	}

	@Override
	public RepeaterCommand clone() {
		RepeaterCommand copy = null;
		copy = (RepeaterCommand)super.clone();

		copy.remoteRepeater = remoteRepeater.clone();

		return copy;
	}

	@Override
	protected String getHeader() {
		return "RPT";
	}

	@Override
	protected boolean parseCommand(ByteBuffer srcBuffer) {
		return false;
	}

	@Override
	protected Optional<byte[]> assembleCommandInt() {
		if(getRemoteRepeater() == null) {return Optional.empty();}

		int dataLength =
				DSTARDefines.CallsignFullLength +	// callsign
				4 +													// Reconnect
				DSTARDefines.CallsignFullLength +	// reflector callsign
				(
					getRemoteRepeater().getLinks().size() *
					(
						DSTARDefines.CallsignFullLength +	// callsign
						4 +													// protocol
						4 +													// linked
						4 +													// direction
						4														// dongle
					)
				);

		byte[] dstBuffer = new byte[dataLength];
		Arrays.fill(dstBuffer, (byte)0x0);

		char[] callsign = null;
		int offset = 0;

		callsign = DSTARUtils.formatFullLengthCallsign(getRemoteRepeater().getRepeaterCallsign()).toCharArray();
		for(int i = 0; i < callsign.length; i++) {dstBuffer[i + offset] = (byte)callsign[i];}
		offset += DSTARDefines.CallsignFullLength;

		DSTARUtils.writeInt32BigEndian(dstBuffer, offset, getRemoteRepeater().getReconnectType().getValue());
		offset += 4;

		callsign = DSTARUtils.formatFullLengthCallsign(getRemoteRepeater().getStartupReflectorCallsign()).toCharArray();
		for(int i = 0; i < callsign.length; i++) {dstBuffer[i + offset] = (byte)callsign[i];}
		offset += DSTARDefines.CallsignFullLength;

		for(ReflectorLinkInformation link : getRemoteRepeater().getLinks()) {

			callsign = DSTARUtils.formatFullLengthCallsign(link.getCallsign()).toCharArray();
			for(int i = 0; i < callsign.length; i++) {dstBuffer[i + offset] = (byte)callsign[i];}
			offset += DSTARDefines.CallsignFullLength;

			DSTARUtils.writeInt32BigEndian(dstBuffer, offset, link.getLinkProtocol().getValue());
			offset += 4;

			DSTARUtils.writeBooleanBigEndian(dstBuffer, offset, link.isLinked());
			offset += 4;

			DSTARUtils.writeInt32BigEndian(dstBuffer, offset, link.getConnectionDirection().getValue());
			offset += 4;

			DSTARUtils.writeBooleanBigEndian(dstBuffer, offset, link.isDongle());
			offset += 4;
		}

		return Optional.of(dstBuffer);
	}

}
