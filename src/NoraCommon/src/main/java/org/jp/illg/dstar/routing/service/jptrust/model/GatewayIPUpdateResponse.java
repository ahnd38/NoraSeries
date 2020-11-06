/**
 *
 */
package org.jp.illg.dstar.routing.service.jptrust.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;


/**
 * @author AHND
 *
 */
@Slf4j
public class GatewayIPUpdateResponse
	extends GatewayIPUpdate
	implements Cloneable
{

	public GatewayIPUpdateResponse() {
		super();
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.gw.commands.CommandBase#assembleCommandData()
	 */
	@Override
	public byte[] assembleCommandData() {
		byte[] data = new byte[20];
		Arrays.fill(data, (byte)0x00);

		byte[] ip =
				super.getGatewayAddress() != null ? super.getGatewayAddress().getAddress() : new byte[]{0,0,0,0};

		for(int index = 0;index < data.length;index++) {
			switch(index) {
			case 0:case 1:
				data[index] = super.getCommandID()[1 - index];break;
			case 2:
				data[index] = (byte) 0x80;
				break;
			case 3:
				data[index] = (byte) super.getResult().getValue();
				break;
			case 4:
				data[index] = super.getCommandType().getValue();break;
			case 5:
				break;
			case 6:case 7:	//Reserved
				break;
			case 8:case 9:case 10:case 11:
			case 12:case 13:case 14:case 15:
				data[index] = (byte) this.getRepeater1Callsign()[index - 8];break;
			case 16:case 17:case 18 :case 19:
				data[index] = ip[index - 16];break;
			default:
				break;
			}
		}
		return data;
	}

	/* (非 Javadoc)
	 * @see org.jp.illg.dstar.gw.commands.CommandBase#analyzeCommandData(java.nio.ByteBuffer)
	 */
	@Override
	public JpTrustCommand parseCommandData(ByteBuffer buffer) {
		if(buffer == null || buffer.rewind().remaining() <= 0)
			return null;

		buffer.rewind();

		if(buffer.remaining() < 20)
			return null;

		super.getCommandID()[1] = buffer.get();
		super.getCommandID()[0] = buffer.get();
		if(buffer.get() != (byte)0x80) {return null;}
		super.setResult(JpTrustResult.getResultByValue(buffer.get()));

		if(
				buffer.get() == super.getCommandType().getValue()
		){
			byte[] ip = new byte[4];

			for(int index = 5;index < 20;index++) {
				byte data = buffer.get();
				switch(index){
				case 5:case 6:case 7:
					break;
				case 8:case 9:case 10:case 11:
				case 12:case 13:case 14:case 15:
					this.getRepeater1Callsign()[index - 8] = (char) data;
					break;
				case 16:case 17:case 18:case 19:
					ip[index - 16] = (byte)data;
					break;
				default:
					break;
				}
			}
			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			try {
				this.setGatewayAddress(InetAddress.getByAddress(ip));
			}catch(UnknownHostException ex) {
				if(log.isWarnEnabled()) {log.warn("function AnalyzeCommand() illiegal ip address received...");}
			}

			return this;
		}else {
			buffer.rewind();
			return null;
		}
	}

	@Override
	public char[] getRepeater2Callsign() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setRepeater2Callsign(char[] repeater2Callsign) {
		throw new UnsupportedOperationException();
	}

	@Override
	public char[] getYourCallsign() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setYourCallsign(char[] yourCallsign) {
		throw new UnsupportedOperationException();
	}

	@Override
	public char[] getMyCallsign() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setMyCallsign(char[] myCallsign) {
		throw new UnsupportedOperationException();
	}

	@Override
	public char[] getMyCallsignAdd() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setMyCallsignAdd(char[] myCallsignAdd) {
		throw new UnsupportedOperationException();
	}
}
