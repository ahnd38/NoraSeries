package org.jp.illg.dstar.gateway.helper.model;

import org.jp.illg.dstar.model.defines.VoiceCharactors;

import lombok.Getter;
import lombok.Setter;

public class GatewayHelperProperties {

	@Getter
	@Setter
	private boolean disableHeardAtReflector;
	private static final boolean disableHeardAtReflectorDefault = true;

	@Getter
	@Setter
	private boolean autoReplaceCQFromReflectorLinkCommand;
	private static final boolean autoReplaceCQFromReflectorLinkCommandDefault = false;

	@Getter
	@Setter
	private VoiceCharactors announceCharactor;
	private static final VoiceCharactors announceCharactorDefault = VoiceCharactors.KizunaAkari;


	public GatewayHelperProperties() {
		super();

		setDisableHeardAtReflector(disableHeardAtReflectorDefault);
		setAutoReplaceCQFromReflectorLinkCommand(autoReplaceCQFromReflectorLinkCommandDefault);
		setAnnounceCharactor(announceCharactorDefault);
	}

}
