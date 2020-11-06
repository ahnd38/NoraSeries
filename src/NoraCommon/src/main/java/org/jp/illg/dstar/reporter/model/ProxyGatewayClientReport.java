package org.jp.illg.dstar.reporter.model;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.UUID;

import lombok.Data;

@Data
public class ProxyGatewayClientReport {

	private UUID id;

	private long connectedTime;

	private String gatewayCallsign;

	private String[] repeaterCallsigns;

	private InetSocketAddress remoteHost;

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ProxyGatewayClientReport other = (ProxyGatewayClientReport) obj;
		if (connectedTime != other.connectedTime)
			return false;
		if (gatewayCallsign == null) {
			if (other.gatewayCallsign != null)
				return false;
		} else if (!gatewayCallsign.equals(other.gatewayCallsign))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (remoteHost == null) {
			if (other.remoteHost != null)
				return false;
		} else if (!remoteHost.equals(other.remoteHost))
			return false;
		if (!Arrays.equals(repeaterCallsigns, other.repeaterCallsigns))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (connectedTime ^ (connectedTime >>> 32));
		result = prime * result + ((gatewayCallsign == null) ? 0 : gatewayCallsign.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((remoteHost == null) ? 0 : remoteHost.hashCode());
		result = prime * result + Arrays.hashCode(repeaterCallsigns);
		return result;
	}


}
