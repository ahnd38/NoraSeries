/**
 *
 */
package org.jp.illg.dstar.routing.service.jptrust.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.CallSignValidator;

/**
 * @author AHND
 *
 */
public class GatewayIPUpdateRequest
	extends GatewayIPUpdate
	implements Cloneable
{

	public GatewayIPUpdateRequest() {
		super();
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.gw.commands.CommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		byte[] data = new byte[16];
		Arrays.fill(data, (byte)0x00);
		for(int index = 0;index < data.length;index++) {
			switch(index) {
			case 0:case 1:
				data[index] = super.getCommandID()[1 - index];break;
			case 2:case 3:
				break;
			case 4:
				data[index] = super.getCommandType().getValue();break;
			case 5:
				break;
			case 6:case 7:	//Reserved
				break;
			case 8:case 9:case 10:case 11:
			case 12:case 13:case 14:case 15:
				data[index] = (byte) this.getRepeater1Callsign()[index - 8];break;
			default:
				break;
			}
		}
		return data;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.gw.commands.CommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public JpTrustCommand parseCommandData(ByteBuffer buffer) {
		if(buffer == null || buffer.rewind().remaining() <= 0)
			return null;

		buffer.rewind();

		if(buffer.remaining() < 16)
			return null;

		super.getCommandID()[1] = buffer.get();
		super.getCommandID()[0] = buffer.get();
		if(buffer.get() != (byte)0x00) {return null;}
		super.setResult(JpTrustResult.getResultByValue(buffer.get()));

		if(
				buffer.get() == super.getCommandType().getValue()
		){
			for(int index = 5;index < 16;index++) {
				byte data = buffer.get();
				switch(index){
				case 5:case 6:case 7:
					break;
				case 8:case 9:case 10:case 11:
				case 12:case 13:case 14:case 15:
					this.getRepeater1Callsign()[index - 8] = (char) data;
					break;
				default:
					break;
				}
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
	public void setRepeater1Callsign(char[] repeater1Callsign) {
		if(!CallSignValidator.isValidGatewayCallsign(repeater1Callsign))
			throw new IllegalArgumentException("Bad gateway callsign = " + String.valueOf(repeater1Callsign) + ".");

		char[] gatewayCallsign = Arrays.copyOf(repeater1Callsign, repeater1Callsign.length);

		gatewayCallsign[DSTARDefines.CallsignFullLength - 1] = ' ';

		super.setRepeater1Callsign(gatewayCallsign);
	}

	@Override
	public char[] getRepeater2Callsign() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setRepeater2Callsign(char[] repeater2Callsign) {
		throw new UnsupportedOperationException();
	}

	@Override
	public char[] getYourCallsign() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setYourCallsign(char[] yourCallsign) {
		throw new UnsupportedOperationException();
	}

	@Override
	public char[] getMyCallsign() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setMyCallsign(char[] myCallsign) {
		throw new UnsupportedOperationException();
	}

	@Override
	public char[] getMyCallsignAdd() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setMyCallsignAdd(char[] myCallsignAdd) {
		throw new UnsupportedOperationException();
	}
}
