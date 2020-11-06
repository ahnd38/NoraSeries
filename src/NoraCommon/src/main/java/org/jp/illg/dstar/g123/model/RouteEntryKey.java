package org.jp.illg.dstar.g123.model;

import java.net.InetSocketAddress;

public class RouteEntryKey{
	private InetSocketAddress remoteAddress;
	private int frameID;

	private RouteEntryKey() {
		super();
	}

	public RouteEntryKey(InetSocketAddress remoteAddress, int frameID) {
		this();

		setRemoteAddress(remoteAddress);
		setFrameID(frameID);
	}

	/* (非 Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + frameID;
		result = prime * result + ((remoteAddress == null) ? 0 : remoteAddress.hashCode());
		return result;
	}

	/* (非 Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RouteEntryKey other = (RouteEntryKey) obj;
		if (frameID != other.frameID)
			return false;
		if (remoteAddress == null) {
			if (other.remoteAddress != null)
				return false;
		} else if (!remoteAddress.equals(other.remoteAddress))
			return false;
		return true;
	}

	public static boolean isValidFrameID(int frameID) {
		if(frameID < 0x0 || frameID > 0xFFFF)
			return false;
		else
			return true;
	}

	/**
	 * @return remoteAddress
	 */
	public InetSocketAddress getRemoteAddress() {
		return remoteAddress;
	}

	/**
	 * @param remoteAddress セットする remoteAddress
	 */
	public void setRemoteAddress(InetSocketAddress remoteAddress) {
		assert remoteAddress != null;
		if(remoteAddress == null) {throw new IllegalArgumentException();}

		this.remoteAddress = remoteAddress;
	}

	/**
	 * @return frameID
	 */
	public int getFrameID() {
		return frameID;
	}

	/**
	 * @param frameID セットする frameID
	 */
	public void setFrameID(int frameID) {
		if(!isValidFrameID(frameID))
			throw new IllegalArgumentException();

		this.frameID = frameID;
	}
}
