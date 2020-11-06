package org.jp.illg.dstar.gateway.tool.reflectorlink;

import java.util.List;

import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.config.ReflectorLinkManagerProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;

import com.annimon.stream.Optional;

import lombok.NonNull;

public interface ReflectorLinkManager {

	public boolean setProperties(ReflectorLinkManagerProperties properties);

	public boolean linkReflector(
		DSTARRepeater repeater, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	);
	public boolean unlinkReflector(DSTARRepeater repeater);

	public boolean setAutoControlEnable(DSTARRepeater repeater, boolean enable);
	public Optional<Boolean> getAutoControlEnable(DSTARRepeater repeater);

	public boolean isReflectorLinked(
		@NonNull final DSTARRepeater repeater,
		@NonNull final ConnectionDirectionType dir
	);

	public List<String> getLinkedReflectorCallsign(
		@NonNull final DSTARRepeater repeater,
		@NonNull final ConnectionDirectionType dir
	);

	public ReflectorCommunicationService getOutgoingLinkedReflectorCommunicationService(
		@NonNull final DSTARRepeater repeater
	);

	public void processReflectorLinkManagement();

	public void notifyUseReflector(DSTARRepeater repeater, ConnectionDirectionType dir);

	public boolean isAllowReflectorIncomingConnectionWithLocalRepeater(
		String localRepeaterCallsign
	);

	public boolean isAllowReflectorIncomingConnectionWithRemoteRepeater(
		String remoteRepeaterCallsign
	);
}
