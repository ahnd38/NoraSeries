package org.jp.illg.dstar.routing.service.gltrust.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AreaPositionQuery extends GlobalTrustCommandBase {

	public AreaPositionQuery() {
		super();
	}

	@Override
	public byte[] assembleCommandData() {
		byte[] data = new byte[16];
		Arrays.fill(data, (byte)0x0);
		for(int index = 0;index < data.length;index++) {
			switch(index) {
			case 0:case 1:
				data[index] = getCommandID()[1 - index];break;
			case 2:
				data[index] = (byte)0x10;
			case 3:
				break;
			case 4:
				break;
			case 5:
				break;
			case 6:case 7:
				break;	//reserved
			case 8:case 9:case 10:case 11:
			case 12:case 13:case 14:case 15:
				data[index] = (byte)getYourCallsign()[index - 8];break;
			default:
				break;
			}
		}
		return data;
	}

	@Override
	public GlobalTrustCommand parseCommandData(ByteBuffer buffer) {
		if(buffer == null) {return null;}

		buffer.rewind();

		if(buffer.remaining() != 40)
			return null;

		getCommandID()[1] = buffer.get();
		getCommandID()[0] = buffer.get();

		if(buffer.get() == (byte)0x90){
			final byte r1 = buffer.get();
			final byte r2 = buffer.get();
			
			setValid((r1 & 0x01) == 0x00 && (r2 & 0x01) == 0x01);
			
			byte[] gatewayAddress = new byte[4];

			for(int index = 5;index < 40;index++) {
				byte data = buffer.get();
				switch(index){
				case 5:case 6:case 7:
					break;
				case 8:case 9:case 10:case 11:
				case 12:case 13:case 14:case 15:
					getYourCallsign()[index - 8] = (char) data;
					break;
				case 16:case 17:case 18:case 19:
				case 20:case 21:case 22:case 23:
					getRepeater1Callsign()[index - 16] = (char) data;
					break;
				case 24:case 25:case 26:case 27:
				case 28:case 29:case 30:case 31:
					getRepeater2Callsign()[index - 24] = (char) data;
					break;
				case 32:case 33:case 34:case 35:
					gatewayAddress[index - 32] = (byte)data;
					break;
				default:
					break;
				}
			}
			
			getRepeater1Callsign()[DSTARDefines.CallsignFullLength - 1] = 'G';

			try {
				setGatewayAddress(InetAddress.getByAddress(gatewayAddress));
			}catch(UnknownHostException ex) {
				if(log.isWarnEnabled())
					log.warn("Could not convert unknown ip address...");

				setGatewayAddress(null);

				buffer.rewind();
				return null;
			}

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			return this;
		}else {
			buffer.rewind();
			return null;
		}
	}

	@Override
	protected CommandType getCommandTypeInt() {
		return CommandType.AreaPositionInformation;
	}

}
