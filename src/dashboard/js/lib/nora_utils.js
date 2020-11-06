'use strict';

class NoraUtils {
	static roomIDEscape(roomid) {
		return roomid.replace(/\./g,"\\.");
	}

	static injectValidateRules() {
		jQuery.validator.addMethod(
			"isValidUserCallsign", function(value, element) {
				return this.optional(element) || NoraUtils.isValidUserCallsign(value);
			},
			i18next.t("validator.is_valid_callsign.fail")
		);
	}

	static isValidUserCallsign(callsign) {
		return callsign.match(
			/^(([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[ ]{0,}[ A-FH-Z]$/
		);
	}
}
