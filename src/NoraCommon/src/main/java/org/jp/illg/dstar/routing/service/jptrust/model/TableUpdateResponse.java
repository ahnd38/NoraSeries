package org.jp.illg.dstar.routing.service.jptrust.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TableUpdateResponse
	extends TableUpdate
	implements Cloneable
{

	public TableUpdateResponse() {
		super();
	}

	@Override
	public byte[] assembleCommandData() {
		byte[] data = new byte[36];
		Arrays.fill(data, (byte)0x0);

		byte[] ip =
			super.getGatewayAddress() != null ? super.getGatewayAddress().getAddress() : new byte[] {0,0,0,0};

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
				data[index] = (byte) this.getMyCallsign()[index - 8];break;
			case 16:case 17:case 18:case 19:
			case 20:case 21:case 22:case 23:
				data[index] = (byte) this.getRepeater2Callsign()[index - 16];break;
			case 24:case 25:case 26:case 27:
			case 28:case 29:case 30:case 31:
				data[index] = (byte) this.getRepeater1Callsign()[index - 24];break;
			case 32:case 33:case 34:case 35:
				data[index] = ip[index - 32];
				break;
			default:
				break;
			}
		}
		return data;
	}

	/**
	 * コマンド解析
	 */
	@Override
	public JpTrustCommand parseCommandData(ByteBuffer buffer) {

		buffer.rewind();

		if(buffer.remaining() < 36)
			return null;

		super.getCommandID()[1] = buffer.get();
		super.getCommandID()[0] = buffer.get();
		if(buffer.get() != (byte) 0x80) {return null;}
		super.setResult(JpTrustResult.getResultByValue(buffer.get()));

		if(
				buffer.get() == super.getCommandType().getValue()
		){
			byte[] gatewayAddress = new byte[4];

			for(int index = 5;index < 36;index++) {
				byte data = buffer.get();
				switch(index){
				case 5:case 6:case 7:
					break;
				case 8:case 9:case 10:case 11:
				case 12:case 13:case 14:case 15:
					this.getMyCallsign()[index - 8] = (char) data;
					break;
				case 16:case 17:case 18:case 19:
				case 20:case 21:case 22:case 23:
					this.getRepeater2Callsign()[index - 16] = (char) data;
					break;
				case 24:case 25:case 26:case 27:
				case 28:case 29:case 30:case 31:
					this.getRepeater1Callsign()[index - 24] = (char) data;
					break;
				case 32:case 33:case 34:case 35:
					gatewayAddress[index - 32] = (byte)data;
					break;
				default:
					break;
				}
				try {
					this.setGatewayAddress(InetAddress.getByAddress(gatewayAddress));
				}catch(UnknownHostException ex) {
					if(log.isWarnEnabled())
						log.warn("function analyzeCommandData() can not to convert unknown ip address...");

					buffer.rewind();
					return null;
				}
			}
			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			return this;
		}else {
			buffer.rewind();
			return null;
		}
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
	public char[] getMyCallsignAdd() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setMyCallsignAdd(char[] myCallsignAdd) {
		throw new UnsupportedOperationException();
	}
}
