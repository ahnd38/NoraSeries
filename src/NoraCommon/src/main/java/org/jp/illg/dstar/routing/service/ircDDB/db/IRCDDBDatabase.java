package org.jp.illg.dstar.routing.service.ircDDB.db;

import java.util.Date;
import java.util.List;

import org.jp.illg.dstar.routing.service.ircDDB.db.define.IRCDDBTableID;
import org.jp.illg.dstar.routing.service.ircDDB.db.model.IRCDDBRecord;

import com.annimon.stream.Optional;

import lombok.NonNull;

public interface IRCDDBDatabase {

	public boolean insert(IRCDDBTableID tableID, Date timestamp, String key, String value);

	public boolean update(IRCDDBTableID tableID, Date timestamp, String key, String value);

	public Optional<IRCDDBRecord> findByKey(IRCDDBTableID tableID, String key);

	public List<IRCDDBRecord> findByValue(IRCDDBTableID tableID, String value);

	public Optional<IRCDDBRecord> findLatest(@NonNull IRCDDBTableID tableID);

	public List<IRCDDBRecord> findAll(IRCDDBTableID tableID);

	public void delAll(@NonNull IRCDDBTableID tableID);

	public void delAll();

	public long countRecords(IRCDDBTableID tableID);

	public void dispose();
}
