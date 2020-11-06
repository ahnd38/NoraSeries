/**
 *
 */
package org.jp.illg.dstar.routing.service.jptrust.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;

/**
 * @author AHND
 *
 */
public class TableUpdateRequest
	extends TableUpdate
	implements Cloneable
{

	public TableUpdateRequest() {
		super();
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.commands.CommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		byte[] data = new byte[32];
		Arrays.fill(data, (byte)0x0);

		char[] zoneRepeaterCallsign = new char[DSTARDefines.CallsignFullLength];
		Arrays.fill(zoneRepeaterCallsign, ' ');
		for(int i = 0; i < (DSTARDefines.CallsignFullLength - 2); i++)
			zoneRepeaterCallsign[i] = getRepeater2Callsign()[i];

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
				data[index] = (byte) this.getMyCallsign()[index - 8];break;
			case 16:case 17:case 18:case 19:
			case 20:case 21:case 22:case 23:
				data[index] = (byte) zoneRepeaterCallsign[index - 16];break;
			case 24:case 25:case 26:case 27:
			case 28:case 29:case 30:case 31:
				data[index] = (byte) this.getRepeater1Callsign()[index - 24];break;
			default:
				break;
			}
		}
		return data;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.commands.CommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public JpTrustCommand parseCommandData(ByteBuffer buffer) {
		if(buffer == null || buffer.rewind().remaining() <= 0)
			return null;

		buffer.rewind();

		if(buffer.remaining() < 32)
			return null;

		super.getCommandID()[1] = buffer.get();
		super.getCommandID()[0] = buffer.get();
		if(buffer.get() != (byte) 0x00) {return null;}
		super.setResult(JpTrustResult.getResultByValue(buffer.get()));

		if(
				buffer.get() == super.getCommandType().getValue()
		){
			for(int index = 5;index < 32;index++) {
				byte data = buffer.get();
				switch(index){
				case 5:case 6:case 7:
					break;
				case 8:case 9:case 10:case 11:
				case 12:case 13:case 14:case 15:
					this.getMyCallsign()[index - 8] = (char) data;
					break;
				case 16:case 17:case 18:case 19:
				case 20:case 21:case 22:case 23:
					this.getRepeater2Callsign()[index - 16] = (char) data;
					break;
				case 24:case 25:case 26:case 27:
				case 28:case 29:case 30:case 31:
					this.getRepeater1Callsign()[index - 24] = (char) data;
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
	public char[] getYourCallsign() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setYourCallsign(char[] myCallsign) {
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
