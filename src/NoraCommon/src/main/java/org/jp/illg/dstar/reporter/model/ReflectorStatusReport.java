package org.jp.illg.dstar.reporter.model;

import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceStatus;

import lombok.Data;

@Data
public class ReflectorStatusReport {
	
	private ReflectorProtocolProcessorTypes reflectorType;
	
	private ReflectorCommunicationServiceStatus serviceStatus;
	
	private boolean enableIncomingLink;
	
	private boolean enableOutgoingLink;
	
	private int connectedIncomingLink;
	
	private int connectedOutgoingLink;
	
	private int incomingLinkPort;
	
	private String incomingStatus;
	
	private String outgoingStatus;
	
	public ReflectorStatusReport() {
		super();
		
		reflectorType = ReflectorProtocolProcessorTypes.Unknown;
		
		serviceStatus = ReflectorCommunicationServiceStatus.OutOfService;
		
		enableIncomingLink = false;
		enableOutgoingLink = false;
		connectedIncomingLink = 0;
		connectedOutgoingLink = 0;
		incomingLinkPort = 0;
		
		incomingStatus = "";
		outgoingStatus = "";
	}
	
}
