package org.jp.illg.dstar.reflector.model;

import lombok.Getter;
import lombok.NonNull;

public class ReflectorHostInfoKey {
	
	@Getter
	private String reflectorCallsign;
	
	@Getter
	private String dataSource;
	
	public ReflectorHostInfoKey(
		@NonNull final String reflectorCallsign,
		@NonNull final String dataSource
	) {
		this.reflectorCallsign = reflectorCallsign;
		this.dataSource = dataSource;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dataSource == null) ? 0 : dataSource.hashCode());
		result = prime * result + ((reflectorCallsign == null) ? 0 : reflectorCallsign.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ReflectorHostInfoKey other = (ReflectorHostInfoKey) obj;
		if (dataSource == null) {
			if (other.dataSource != null)
				return false;
		} else if (!dataSource.equals(other.dataSource))
			return false;
		if (reflectorCallsign == null) {
			if (other.reflectorCallsign != null)
				return false;
		} else if (!reflectorCallsign.equals(other.reflectorCallsign))
			return false;
		return true;
	}
	
	
}
