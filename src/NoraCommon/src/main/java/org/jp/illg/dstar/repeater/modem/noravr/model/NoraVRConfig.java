package org.jp.illg.dstar.repeater.modem.noravr.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NoraVRConfig {
	
	private boolean supportedCodecPCM;
	private boolean supportedCodecOpus64k;
	private boolean supportedCodecOpus24k;
	private boolean supportedCodecOpus8k;
	private boolean supportedCodecAMBE;
	private boolean echoback;
	private boolean allowRFNode;
	
}
