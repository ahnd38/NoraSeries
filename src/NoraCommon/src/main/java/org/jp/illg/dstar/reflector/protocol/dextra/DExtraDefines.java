package org.jp.illg.dstar.reflector.protocol.dextra;

import java.util.concurrent.TimeUnit;

public class DExtraDefines {

	public static final long keepAlivePeriodMillis = TimeUnit.SECONDS.toMillis(5);

	public static final long keepAliveTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
}

/*
 ____Poll____(len=9)
[0..7]	LinkedReflectorModule
[8]		isDongle(00 != dongle)

____Connect____(len=11 or 14)
[0..7] 	RepeaterCallsign(No band include) eg. JQ1ZYC__
[8]		band(A..D)
[9]		LinkReflectorModule(A..D or ' ' unlink)
[10]		Revision(11=rev1/00=rev0)
[11..13]	"ACK" ack or "NAK" nak


____Voice____(len=27)
 */
