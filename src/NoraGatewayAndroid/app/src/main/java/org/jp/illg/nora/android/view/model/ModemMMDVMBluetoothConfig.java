package org.jp.illg.nora.android.view.model;

import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class ModemMMDVMBluetoothConfig implements Cloneable{
	
	private String portName;
	
	private boolean allowDIRECT;
	
	private boolean duplex;
	
	private boolean rxInvert;
	
	private boolean txInvert;
	
	private boolean pttInvert;
	
	private int txDelay;
	
	private long rxFrequency;
	
	private long rxFrequencyOffset;
	
	private long txFrequency;
	
	private long txFrequencyOffset;
	
	private int rxDCOffset;
	
	private int txDCOffset;
	
	private int rfLevel;
	
	private int rxLevel;
	
	private int txLevel;
	
	{
		portName = "";
		allowDIRECT = false;
		duplex = false;
		rxInvert = false;
		txInvert = false;
		pttInvert = false;
		txDelay = 100;
		rxFrequency = 430800000;
		rxFrequencyOffset = 500;
		txFrequency = 430800000;
		txFrequencyOffset = 500;
		rxDCOffset = 0;
		txDCOffset = 0;
		rfLevel = 100;
		rxLevel = 50;
		txLevel = 50;
	}
	
	@Override
	public ModemMMDVMBluetoothConfig clone(){
		ModemMMDVMBluetoothConfig copy = null;
		
		try{
			copy = (ModemMMDVMBluetoothConfig)super.clone();
			
			copy.portName = this.portName;
			copy.allowDIRECT = this.allowDIRECT;
			copy.duplex = this.duplex;
			copy.rxInvert = this.rxInvert;
			copy.txInvert = this.txInvert;
			copy.pttInvert = this.pttInvert;
			copy.txDelay = this.txDelay;
			copy.rxFrequency = this.rxFrequency;
			copy.rxFrequencyOffset = this.rxFrequencyOffset;
			copy.txFrequency = this.txFrequency;
			copy.txFrequencyOffset = this.txFrequencyOffset;
			copy.rxDCOffset = this.rxDCOffset;
			copy.txDCOffset = this.txDCOffset;
			copy.rfLevel = this.rfLevel;
			copy.rxLevel = this.rxLevel;
			copy.txLevel = this.txLevel;
			
			return copy;
		}catch (CloneNotSupportedException ex){
			throw new RuntimeException(ex);
		}
	}
}
