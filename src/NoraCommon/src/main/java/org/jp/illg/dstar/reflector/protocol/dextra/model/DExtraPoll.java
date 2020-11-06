package org.jp.illg.dstar.reflector.protocol.dextra.model;

import org.jp.illg.dstar.model.PollData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;

public class DExtraPoll extends PollData implements Cloneable {

	private boolean dongle;

	private ConnectionDirectionType direction;

	public DExtraPoll() {
		super();

	}

	@Override
	protected DExtraPoll clone() throws CloneNotSupportedException {
		DExtraPoll copy = null;

		try {
			copy = (DExtraPoll)super.clone();

			copy.direction = this.direction;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	/**
	 * @return dongle
	 */
	public boolean isDongle() {
		return dongle;
	}

	/**
	 * @param dongle セットする dongle
	 */
	public void setDongle(boolean dongle) {
		this.dongle = dongle;
	}

	/**
	 * @return direction
	 */
	public ConnectionDirectionType getDirection() {
		return direction;
	}

	/**
	 * @param direction セットする direction
	 */
	public void setDirection(ConnectionDirectionType direction) {
		this.direction = direction;
	}



}
