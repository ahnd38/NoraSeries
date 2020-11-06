package org.jp.illg.util.irc.model;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.Setter;

public class IRCMessage {

	private final Lock locker;
	private List<String> params;
	private boolean prefixParsed;
	private List<String> prefixComponents;

	@Getter
	@Setter
	private String prefix;

	@Getter
	@Setter
	private String command;

	public IRCMessage() {
		super();

		locker = new ReentrantLock();

		params = new ArrayList<>();

		prefixParsed = false;
		prefixComponents = new ArrayList<>();

		prefix = "";
		command = "";
	}

	public IRCMessage(final String toNick, final String msg){
		this();

		command = "PRIVMSG";
		addParam( toNick );
		addParam( msg );
		prefixParsed = false;
	}

	public IRCMessage ( final String cmd ){
		this();

		command = cmd;
		prefixParsed = false;
	}


	public void addParam( final String p){
		locker.lock();
		try {
			params.add( p );
		}finally {
			locker.unlock();
		}
	}

	public boolean changeParam(final int index, final String p) {
		locker.lock();
		try {
			if(params.size() > index) {
				params.remove(index);
				params.add(index, p);
				return true;
			}
		}finally {
			locker.unlock();
		}

		return false;
	}

	public int getParamCount()
	{
		locker.lock();
		try {
			return params.size();
		}finally {
			locker.unlock();
		}
	}

	public String getParam( int pos ){
		locker.lock();
		try {
			return pos >= 0 && params.size() > pos ? params.get(pos) : "";
		}finally {
			locker.unlock();
		}
	}

	public void parsePrefix(){
		locker.lock();
		try {
			for (int i=0; i < 3; i++) {prefixComponents.add("");}

			int state = 0;

			for (int i=0; i < prefix.length(); i++) {
				char c = prefix.charAt(i);

				switch (c)
				{
				case '!':
					state = 1; // next is name
					break;

				case '@':
					state = 2; // next is host
					break;

				default:
					prefixComponents.set(state, prefixComponents.get(state) + c);
					break;
				}
			}

			prefixParsed = true;
		}finally {
			locker.unlock();
		}
	}


	public String getPrefixNick(){
		if (!prefixParsed) {parsePrefix();}

		locker.lock();
		try {
			return prefixComponents.get(0);
		}finally {
			locker.unlock();
		}
	}

	public String getPrefixName(){
		if (!prefixParsed) {parsePrefix();}

		locker.lock();
		try {
			return prefixComponents.get(1);
		}finally {
			locker.unlock();
		}
	}

	public String getPrefixHost(){
		if (!prefixParsed){parsePrefix();}

		locker.lock();
		try {
			return prefixComponents.get(2);
		}finally {
			locker.unlock();
		}
	}

	public String composeMessage (){
		locker.lock();
		try {
			final StringBuilder o = new StringBuilder("");

			if (prefix.length() > 0)
				o.append(":" + prefix + " ");

			o.append(command);

			for (int i=0; i < params.size(); i++){
				if (i == (params.size() - 1))
					o.append(" :" + params.get(i));
				else
					o.append(" " + params.get(i));
			}

			o.append("\r\n");

			return o.toString();
		}finally {
			locker.unlock();
		}
	}

	public void writeMessage ( OutputStream os ) {
		PrintWriter p = new PrintWriter(os);
		p.write(composeMessage());
		p.flush();
	}

	public String getDebugMessage() {
		String debugMsg;

		locker.lock();
		try {
			final StringBuilder sb =
					new StringBuilder("[" + prefix + "] [" + command + "]");

			for(final Iterator<String> it = params.iterator(); it.hasNext();) {
				final String param = it.next();

				sb.append(" [" + param + "]");
			}

			debugMsg = sb.toString().replace("%", "%%").replace("\\", "\\\\");

		}finally {
			locker.unlock();
		}

		return debugMsg;
	}
}
