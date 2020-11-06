package org.jp.illg.dstar.model;

import java.util.List;

import org.jp.illg.dstar.model.config.ModemProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.VoiceCodecType;
import org.jp.illg.dstar.reporter.model.ModemStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlModemHandler;

import lombok.NonNull;

public interface RepeaterModem {

	public boolean start();
	public void stop();
	public boolean isRunning();

	public String getGatewayCallsign();
	public void setGatewayCallsign(String gatewayCallsign);

	public String getRepeaterCallsign();
	public void setRepeaterCallsign(String repeaterCallsign);

	public AccessScope getScope();
	public void setScope(AccessScope scope);

	public ModemTransceiverMode getTransceiverMode();
	public ModemTransceiverMode getDefaultTransceiverMode();
	public ModemTransceiverMode[] getSupportedTransceiverModes();

	public boolean setProperties(ModemProperties properties);
	public ModemProperties getProperties(ModemProperties properties);

	public String initializeWebRemoteControl(final WebRemoteControlService webRemoteControlService);

	public DSTARPacket readPacket();
	public boolean hasReadPacket();

	public boolean writePacket(DSTARPacket packet);
	public boolean hasWriteSpace();

	public boolean isAllowDIRECT();
	public void setAllowDIRECT(boolean allowDIRECT);

	public int getModemId();

	public VoiceCodecType getCodecType();

	public ModemTypes getModemType();

	public String getWebSocketRoomId();

	public ModemStatusReport getStatusReport();

	public WebRemoteControlModemHandler getWebRemoteControlHandler();

	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	);
}
