package org.jp.illg.dstar.dvdongle.model;

public enum DvDongleRunStates{
	Unknown(-1),
	Run(0x01),
	Stop(0x02)
	;
	private final int state;

	private DvDongleRunStates(final int state) {
		this.state = state;
	}

	public int getVal() {
		return this.state;
	}

	public static DvDongleRunStates getStateByVal(final int val) {
		for(DvDongleRunStates state : DvDongleRunStates.values())
			if(state.getVal() == val) {return state;}

		return Unknown;
	}
}
