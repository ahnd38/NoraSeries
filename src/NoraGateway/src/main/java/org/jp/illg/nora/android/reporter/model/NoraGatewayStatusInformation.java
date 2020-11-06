package org.jp.illg.nora.android.reporter.model;

import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.parceler.Parcel;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class NoraGatewayStatusInformation {

	private GatewayStatusReport gatewayStatusReport;

	private List<RepeaterStatusReport> repeaterStatusReports;

	public NoraGatewayStatusInformation(){
		super();

		setRepeaterStatusReports(new ArrayList<RepeaterStatusReport>());
	}

	public NoraGatewayStatusInformation(BasicStatusInformation src){
		super();

		if(src != null){
			setGatewayStatusReport(new GatewayStatusReport(src.getGatewayStatusReport()));

			setRepeaterStatusReports(new ArrayList<RepeaterStatusReport>());
			if(src.getRepeaterStatusReports() != null){
				for(org.jp.illg.dstar.reporter.model.RepeaterStatusReport srcStatusReport : src.getRepeaterStatusReports())
					getRepeaterStatusReports().add(new RepeaterStatusReport(srcStatusReport));
			}
		}
	}
}
