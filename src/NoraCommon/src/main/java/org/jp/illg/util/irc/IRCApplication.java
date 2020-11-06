package org.jp.illg.util.irc;

import org.jp.illg.util.irc.model.IRCMessage;
import org.jp.illg.util.irc.model.IRCMessageQueue;

import com.annimon.stream.Optional;

public interface IRCApplication
{

	void userJoin (String nick, String name, String host);
	void userLeave (String nick);
	void userChanOp (String nick, boolean op);
	void userListReset();

	void msgChannel (IRCMessage m);
	void msgQuery (IRCMessage m);

	void setCurrentNick(String nick);
	void setTopic(String topic);

	void setSendQ( IRCMessageQueue s );
	Optional<IRCMessageQueue> getSendQ ();

}

