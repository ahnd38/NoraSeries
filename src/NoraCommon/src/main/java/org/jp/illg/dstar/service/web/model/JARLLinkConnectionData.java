package org.jp.illg.dstar.service.web.model;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class JARLLinkConnectionData extends ReflectorConnectionData {
	
	private List<JARLLinkClient> loginClients;
	
	private boolean extraRepeaterLinked;
	
	private String serverSoftware;
	
	public JARLLinkConnectionData() {
		super();
	}
	
}
