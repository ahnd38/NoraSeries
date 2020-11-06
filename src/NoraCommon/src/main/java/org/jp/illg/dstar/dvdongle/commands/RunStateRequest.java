/**
 *
 */
package org.jp.illg.dstar.dvdongle.commands;

import java.util.Arrays;

import org.jp.illg.dstar.dvdongle.model.DvDongleRunStates;

/**
 * @author AHND
 *
 */
public class RunStateRequest extends DvDongleCommandForHost {


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
	public void setDongleState(DvDongleRunStates dongleState) {
		this.dongleState = dongleState;
	}

	/**
	 *
	 */
	public RunStateRequest() {
		super();

		super.setMessageType(DvDongleCommandTypeForHost.SetControlItem);
		super.setMessageLength(5);

		this.setDongleState(DvDongleRunStates.Stop);
	}

	public RunStateRequest(DvDongleRunStates state) {
		this();

		if(state != null && state instanceof DvDongleRunStates)
			this.setDongleState(state);
		else
			this.setDongleState(DvDongleRunStates.Unknown);
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.dvdongle.commands.DvDongleCommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		byte[] data = new byte[super.getMessageLength()];
		Arrays.fill(data, (byte)0x00);

		short header = super.getHeader();

		for(int index = 0;index < data.length;index++) {
			switch(index) {
			case 0:
				data[index] = (byte)((header >>> 8) & 0xFF);
				break;
			case 1:
				data[index] = (byte)(header & 0xFF);
				break;
			case 2:
				data[index] = (byte)0x18;
				break;
			case 3:
				data[index] = (byte)0x00;
				break;
			case 4:
				data[index] = (byte)this.getDongleState().getVal();
				break;

			default:
				break;
			}
		}

		return data;
	}

}
