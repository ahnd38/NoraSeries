package org.jp.illg.dstar.service.web.model;

import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMHardwareType;
import org.jp.illg.util.uart.UartInterfaceType;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MMDVMInterfaceStatusData extends ModemStatusData {
	
	private UartInterfaceType uartType;
	
	private int protocolVersion;
	
	private MMDVMHardwareType hardwareType;
	
	private String hardwareVersion;
	
	private String portName;
	
	private boolean enableCodeSquelch;
	
	private int codeSquelchCode;
	
	private boolean enablePacketSlip;
	
	private int packetSlipLimit;
	
	private boolean duplex;
	
	private boolean rxInvert;
	
	private boolean txInvert;
	
	private boolean pttInvert;
	
	private int txDelay;
	
	private boolean debug;
	
	private long rxFrequency;
	
	private long rxFrequencyOffset;
	
	private long txFrequency;
	
	private long txFrequencyOffset;
	
	private long rxDCOffset;
	
	private long txDCOffset;
	
	private long rfLevel;
	
	private float rxLevel;
	
	private float txLevel;
	
	private boolean transparentEnable;
	
	private String transparentRemoteAddress;
	
	private int transparentRemotePort;
	
	private int transparentLocalPort;
	
	private int transparentSendFrameType;
	
	public MMDVMInterfaceStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
