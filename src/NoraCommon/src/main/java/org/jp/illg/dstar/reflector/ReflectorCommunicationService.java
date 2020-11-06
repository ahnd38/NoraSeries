package org.jp.illg.dstar.reflector;

import java.util.List;
import java.util.UUID;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceStatus;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;
import org.jp.illg.dstar.reflector.model.events.ReflectorEvent;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlReflectorHandler;
import org.jp.illg.util.thread.Callback;

import com.annimon.stream.Optional;

public interface ReflectorCommunicationService {

	public DSTARProtocol getProtocolType();
	public ReflectorProtocolProcessorTypes getProcessorType();

	public boolean setProperties(ReflectorProperties properties);
	public ReflectorProperties getProperties(ReflectorProperties properties);

	public boolean initializeWebRemoteControl(WebRemoteControlService webRemoteControlService);

	public boolean start();
	public boolean startAsync(Callback<Boolean> callback);
	public void stop();
	public boolean stopAsync(Callback<Boolean> callback);
	public boolean isRunning();

	public ReflectorCommunicationServiceStatus getStatus();

	public boolean writePacket(DSTARRepeater repeater, DSTARPacket packet, ConnectionDirectionType direction);
	public boolean hasWriteSpace();

	public DSTARPacket readPacket(DSTARRepeater repeater);

	public UUID linkReflector(
		String reflectorCallsign, ReflectorHostInfo reflectorHostInfo, DSTARRepeater repeaterCallsign
	);
	public UUID unlinkReflector(DSTARRepeater repeater);

	public ReflectorEvent getReflectorEvent();

	public boolean isSupportedReflectorCallsign(String reflectorCallsign);

	public boolean isLinked(DSTARRepeater repeater, ConnectionDirectionType connectionDir);

	public Optional<ReflectorLinkInformation> getLinkInformationOutgoing(DSTARRepeater repeater);

	public List<ReflectorLinkInformation> getLinkInformationIncoming(DSTARRepeater repeater);

	public List<ReflectorLinkInformation> getLinkInformation(DSTARRepeater repeater);

	public List<ReflectorLinkInformation> getLinkInformation();

	public boolean isSupportTransparentMode();

	public void setApplicationVersion(String applicationVersion);
	public String getApplicationVersion();

	public void setApplicationName(String applicationName);
	public String getApplicationName();

	public ReflectorStatusReport getStatusReport();

	public WebRemoteControlReflectorHandler getWebRemoteControlHandler();

	public boolean isEnableIncomingLink();
	public int getIncomingLinkPort();
}
