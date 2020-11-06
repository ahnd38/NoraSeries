package org.jp.illg.dstar.reporter.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Data
public class ProxyGatewayStatusReport {

	@Setter(AccessLevel.PRIVATE)
	private List<ProxyGatewayClientReport> clients = new ArrayList<>();

	private int trustRequestRateCurrent;

	private int trustRequestRateMax;

	private int trustRequestRateLimit;

	private int proxyPortNumber;
	private int g1PortNumber;
	private int trustPortNumber;

	public boolean equalsProxyGatewayStatusReport(ProxyGatewayStatusReport o) {

		if(
			o == null
		) {return false;}

		List<ProxyGatewayClientReport> targetClients =
			Stream.of(o.getClients())
			.sorted(ComparatorCompat.comparing(new Function<ProxyGatewayClientReport, UUID>(){
				@Override
				public UUID apply(ProxyGatewayClientReport t) {
					return t.getId();
				}
			}))
			.toList();

		List<ProxyGatewayClientReport> clients =
			Stream.of(getClients())
			.sorted(ComparatorCompat.comparing(new Function<ProxyGatewayClientReport, UUID>(){
				@Override
				public UUID apply(ProxyGatewayClientReport t) {
					return t.getId();
				}
			}))
			.toList();

		if(targetClients.size() != clients.size()) {return false;}

		for(int i = 0; i < targetClients.size() && i < clients.size(); i++) {
			if(!targetClients.get(i).equals(clients.get(i))) {return false;}
		}

		return true;
	}
}
