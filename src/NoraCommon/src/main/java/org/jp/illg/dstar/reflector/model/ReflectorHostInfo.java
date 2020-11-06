package org.jp.illg.dstar.reflector.model;


import org.jp.illg.dstar.model.defines.DSTARProtocol;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class ReflectorHostInfo implements Cloneable {

	public static int priorityMax = 0;
	public static int priorityNormal = 5;
	public static int priorityMin = 10;
	public static int priorityDefault = priorityMin;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DSTARProtocol reflectorProtocol;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String reflectorCallsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String reflectorAddress;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int reflectorPort;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int priority;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String dataSource;

	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private long updateTime;


	public ReflectorHostInfo(
		DSTARProtocol protocol,
		String reflectorCallsign, String reflectorAddress
	) {
		this(protocol, reflectorCallsign, reflectorAddress, priorityDefault);
	}

	public ReflectorHostInfo(
		DSTARProtocol protocol,
		String reflectorCallsign, String reflectorAddress, int priority
	) {
		this(
			protocol, protocol.getPortNumber(), reflectorCallsign, reflectorAddress, priority
		);
	}

	public ReflectorHostInfo(
		DSTARProtocol protocol,
		int reflectorPort, String reflectorCallsign, String reflectorAddress
	) {
		this(
			protocol, reflectorPort, reflectorCallsign, reflectorAddress, priorityDefault
		);
	}

	public ReflectorHostInfo(
		DSTARProtocol protocol,
		int reflectorPort, String reflectorCallsign, String reflectorAddress, int priority
	) {
		this(
			protocol, protocol.getPortNumber(), reflectorCallsign, reflectorAddress, priority,
			System.currentTimeMillis(),
			null, null
		);
	}

	public ReflectorHostInfo(
		DSTARProtocol protocol,
		int reflectorPort, String reflectorCallsign, String reflectorAddress,
		int priority,
		long updateTime,
		String dataSource,
		String name
	) {
		this();

		setReflectorProtocol(protocol);
		setReflectorPort(reflectorPort);
		setReflectorCallsign(reflectorCallsign);
		setReflectorAddress(reflectorAddress);
		setPriority(priority);
		setUpdateTime(updateTime);
		setDataSource(dataSource != null ? dataSource : "");
		setName(name != null ? name : "");
	}

	private ReflectorHostInfo() {
		super();
	}

	@Override
	public ReflectorHostInfo clone() {
		ReflectorHostInfo copy = null;

		try {
			copy = (ReflectorHostInfo)super.clone();

			copy.reflectorProtocol = reflectorProtocol;
			copy.reflectorCallsign = reflectorCallsign;
			copy.reflectorAddress = reflectorAddress;
			copy.reflectorPort = reflectorPort;
			copy.priority = priority;
			copy.dataSource = dataSource;
			copy.name = name;
			copy.updateTime = updateTime;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder();

		sb.append(indent);
		sb.append("[ReflectorCallsign]:");
		sb.append(getReflectorCallsign());

		sb.append("/");

		sb.append("[DataSource]:");
		sb.append(getDataSource());

		sb.append("/");

		sb.append("[ReflectorAddress]:");
		sb.append(getReflectorAddress());
		sb.append(":");
		sb.append(getReflectorPort());

		sb.append("/");

		sb.append("[Protocol]:");
		sb.append(getReflectorProtocol());

		sb.append("/");

		sb.append("[Priority]:");
		sb.append(getPriority());

		sb.append("/");

		sb.append("[Name]:");
		sb.append(getName());

		sb.append("/");

		sb.append("[UpdateTime];");
		sb.append(getUpdateTime());

		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dataSource == null) ? 0 : dataSource.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + priority;
		result = prime * result + ((reflectorAddress == null) ? 0 : reflectorAddress.hashCode());
		result = prime * result + ((reflectorCallsign == null) ? 0 : reflectorCallsign.hashCode());
		result = prime * result + reflectorPort;
		result = prime * result + ((reflectorProtocol == null) ? 0 : reflectorProtocol.hashCode());
		result = prime * result + (int) (updateTime ^ (updateTime >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		return equals(obj, false);
	}

	public boolean equalsIgnoreUpdateTimestamp(Object obj) {
		return equals(obj, true);
	}

	private boolean equals(Object obj, boolean ignoreUpdateTimestamp) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ReflectorHostInfo other = (ReflectorHostInfo) obj;
		if (dataSource == null) {
			if (other.dataSource != null)
				return false;
		} else if (!dataSource.equals(other.dataSource))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (priority != other.priority)
			return false;
		if (reflectorAddress == null) {
			if (other.reflectorAddress != null)
				return false;
		} else if (!reflectorAddress.equals(other.reflectorAddress))
			return false;
		if (reflectorCallsign == null) {
			if (other.reflectorCallsign != null)
				return false;
		} else if (!reflectorCallsign.equals(other.reflectorCallsign))
			return false;
		if (reflectorPort != other.reflectorPort)
			return false;
		if (reflectorProtocol != other.reflectorProtocol)
			return false;
		if (!ignoreUpdateTimestamp && updateTime != other.updateTime)
			return false;
		return true;
	}

}
