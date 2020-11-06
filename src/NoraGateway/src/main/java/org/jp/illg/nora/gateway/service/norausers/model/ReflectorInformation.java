package org.jp.illg.nora.gateway.service.norausers.model;

import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;

import lombok.Data;

@Data
public class ReflectorInformation {

	private String reflectorType;

	private boolean incomingLink;

	private boolean outgoingLink;
	
	private int connectedIncomingLink;
	
	private int connectedOutgoingLink;
	
	private int incomingLinkPort;
	
	private String incomingStatus;
	
	private String outgoingStatus;

	public ReflectorInformation() {
		super();
		
		reflectorType = ReflectorProtocolProcessorTypes.Unknown.getTypeName();
		
		incomingLink = false;
		outgoingLink = false;
		
		connectedIncomingLink = 0;
		connectedOutgoingLink = 0;
		
		incomingLinkPort = -1;
		
		incomingStatus = "";
		outgoingStatus = "";
	}

}
