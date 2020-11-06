package org.jp.illg.nora.gateway.reporter;

import org.jp.illg.dstar.gateway.DStarGatewayImpl;
import org.jp.illg.dstar.model.DStarGateway;
import org.jp.illg.dstar.model.DStarRepeater;
import org.jp.illg.dstar.repeater.DStarRepeaterManager;
import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.reporter.model.RoutingServiceStatusReport;
import org.jp.illg.nora.gateway.NoraGatewayUtil;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.util.Timer;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class NoraGatewayStatusReporterAndroid extends ThreadBase {
	private static final long reportPeriodMillisDefault = TimeUnit.MILLISECONDS.toMillis(500);
	
	private final List<NoraGatewayStatusReportListener> listeners;
	private final Lock listenersLock = new ReentrantLock();
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long reportPeriodMillis;
	
	private Timer lastReportTime;
	
	{
		reportPeriodMillis = reportPeriodMillisDefault;
		lastReportTime = new Timer(reportPeriodMillis);
	}
	
	public NoraGatewayStatusReporterAndroid(
			ThreadUncaughtExceptionListener exceptionListener
	){
		super(
				exceptionListener,
				NoraGatewayStatusReporterAndroid.class.getSimpleName(),
				TimeUnit.MILLISECONDS.toMillis(100)
		);
		
		listeners = new LinkedList<>();
	}
	
	public NoraGatewayStatusReporterAndroid(
			ThreadUncaughtExceptionListener exceptionListener,
			NoraGatewayStatusReportListener listener
	){
		this(exceptionListener);
		
		addListener(listener);
	}
	
	public NoraGatewayStatusReporterAndroid(
			ThreadUncaughtExceptionListener exceptionListener,
			NoraGatewayStatusReportListener listener,
			long reportPeriodMillis
	){
		this(exceptionListener, listener);
		
		if(reportPeriodMillis >= TimeUnit.MILLISECONDS.toMillis(100))
			setReportPeriodMillis(reportPeriodMillis);
		else
			setReportPeriodMillis(reportPeriodMillisDefault);
	}
	
	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}
	
	@Override
	protected ThreadProcessResult process() {
		
		listenersLock.lock();
		try {
			for(NoraGatewayStatusReportListener listener : listeners) {
				if(listener != null) {listener.listenerProcess();}
			}
		}finally {
			listenersLock.unlock();
		}
		
		if(lastReportTime.isTimeout()) {
			lastReportTime.updateTimestamp();
			
			final BasicStatusInformation info = new BasicStatusInformation();
			info.setApplicationName(NoraGatewayUtil.getApplicationName());
			info.setApplicationVersion(NoraGatewayUtil.getApplicationVersion());
			info.setApplicationRunningOS(NoraGatewayUtil.getRunningOperatingSystem());
			info.setApplicationUptime(NoraGatewayUtil.getApplicationUptimeSeconds());
			
			info.setCpuUsageReport(null);
			
			final DStarGateway gateway = DStarGatewayImpl.getCreatedGateway();
			if(gateway != null){info.setGatewayStatusReport(gateway.getGatewayStatusReport());}
			
			final List<DStarRepeater> repeaters = DStarRepeaterManager.getRepeaters();
			if(repeaters != null){
				for(DStarRepeater repeater : repeaters)
					info.getRepeaterStatusReports().add(repeater.getRepeaterStatusReport());
			}
			final List<ReflectorStatusReport> reflectors =
					gateway != null ? gateway.getReflectorStatusReport() : new ArrayList<>(0);
			info.setReflectorStatusReports(reflectors);
			
			final List<RoutingServiceStatusReport> routingServices =
					gateway != null ? gateway.getRoutingStatusReport() : new ArrayList<>(0);
			info.setRoutingStatusReports(routingServices);
			
			if(gateway != null && repeaters != null && reflectors != null) {
				listenersLock.lock();
				try {
					for(Iterator<NoraGatewayStatusReportListener> it = listeners.iterator(); it.hasNext();) {
						NoraGatewayStatusReportListener listener = it.next();
						if(listener != null)
							listener.report(info);
						else
							it.remove();
					}
					
				}finally {
					listenersLock.unlock();
				}
			}
		}
		
		return ThreadProcessResult.NoErrors;
	}
	
	@Override
	protected void threadFinalize() {
		removeListenerAll();
	}
	
	
	public boolean addListener(NoraGatewayStatusReportListener listener) {
		if(listener == null) {return false;}
		
		listenersLock.lock();
		try {
			return listeners.add(listener);
		}finally {
			listenersLock.unlock();
		}
	}
	
	public boolean removeListener(NoraGatewayStatusReportListener listener) {
		if(listener == null) {return false;}
		
		listenersLock.lock();
		try {
			boolean found = false;
			for(Iterator<NoraGatewayStatusReportListener> it = listeners.iterator(); it.hasNext();) {
				NoraGatewayStatusReportListener entry = it.next();
				
				if(entry == listener) {
					found = true;
					it.remove();
				}
			}
			return found;
		}finally {
			listenersLock.unlock();
		}
	}
	
	public void removeListenerAll() {
		listenersLock.lock();
		try {
			listeners.clear();
		}finally {
			listenersLock.unlock();
		}
	}
}
