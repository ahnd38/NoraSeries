package org.jp.illg.dstar.routing.service.ircDDB.db.model;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IRCDDBRecord {

	private Date timestamp;

	private String key;

	private String value;
}
