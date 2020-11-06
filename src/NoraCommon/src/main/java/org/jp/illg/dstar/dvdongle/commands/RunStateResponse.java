/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.dvdongle.model.DvDongleRunStates;

/**
 * @author AHND
 *
 */
public class RunStateResponse extends DvDongleCommandForTarget {

	private DvDongleRunStates dongleState;

	/**
	 * @return dongleState
	 */
	public DvDongleRunStates getDongleState() {
		return dongleState;
	}

	/**
	 * @param dongleState セットする dongleState
	 */
	private void setDongleState(DvDongleRunStates dongleState) {
		this.dongleState = dongleState;
	}

	/**
	 *
	 */
	public RunStateResponse() {
		super();

	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.dvdongle.commands.DvDongleCommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public DvDongleCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
				buffer.limit() >= 5 &&
				super.analyzeHeader(buffer) &&
				super.getMessageLength() == 5 &&
				buffer.get() == (byte)0x18 &&	//Control Item Code 0x0018
				buffer.get() == (byte)0x00
		){
			for(int index = 4;index < 5;index++) {
				byte data = buffer.get();
				switch(index){
				case 4:
					this.setDongleState(DvDongleRunStates.getStateByVal(data));
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


}
