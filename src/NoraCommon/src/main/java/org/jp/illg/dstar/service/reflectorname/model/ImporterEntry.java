package org.jp.illg.dstar.service.reflectorname.model;

import org.jp.illg.dstar.service.reflectorname.importers.ReflectorHostsImporter;
import org.jp.illg.util.Timer;

import lombok.Data;
import lombok.NonNull;

@Data
public class ImporterEntry {

	private final ReflectorHostsImporter importer;

	private final boolean enable;

	private final int intervalMinutes;

	private final Timer intervalTimekeeper;


	public ImporterEntry(
		@NonNull final ReflectorHostsImporter importer,
		final boolean enable,
		final int intervalMinutes
	) {
		this.importer = importer;
		this.enable = enable;
		this.intervalMinutes = intervalMinutes;

		this.intervalTimekeeper = new Timer();
	}

}
