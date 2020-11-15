package org.jp.illg.nora.android.view.fragment;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.nora.android.view.fragment.model.LogFragmentData;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.FragmentUtil;
import org.jp.illg.util.android.view.EventBusEvent;
import org.parceler.Parcels;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Bundler;
import icepick.Icepick;
import icepick.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogFragment  extends FragmentBase {

	private static final int logLimit = 100;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;

	@BindView(R.id.textViewLog)
	TextView textViewLog;

	@BindView((R.id.buttonLogClose))
	Button buttonLogClose;

	@State(LogBufferBundler.class)
	Queue<String> logBuffer;

	public static class LogBufferBundler implements Bundler<Queue<String>> {
		@Override
		public void put(String key, Queue<String> item, Bundle bundle) {
			bundle.putParcelable(key, Parcels.wrap(item));
		}

		@Override
		public Queue<String> get(String key, Bundle bundle) {
			return Parcels.unwrap(bundle.getParcelable(key));
		}
	}

	public static class StatusFragmentEvent extends EventBusEvent<LogFragment.StatusFragmentEventType> {
		public StatusFragmentEvent(LogFragment.StatusFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum StatusFragmentEventType{
		UpdateData{
			@Override
			void apply(LogFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof LogFragmentData){
					LogFragmentData data = (LogFragmentData)attachment;

					if(data.getLogs() != null && data.getLogs().length >= 1){
						fragment.clearLog();
						for(String message : data.getLogs()){fragment.addLog(message);}
						
						fragment.updateView();
					}
					else if(data.getLog() != null && !"".equals(data.getLog())){
//						fragment.addLog(data.getLog());
						fragment.getEventBus().post(
								new StatusFragment.LogFragmentEvent(
										StatusFragment.LogFragmentEventType.RequestSavedLogs,
										this
								)
						);
					}

					fragment.updateView();
				}
			}
		},
		;

		abstract void apply(LogFragment fragment, Object attachment);
	}

	@Subscribe
	public void onStatusFragmentEvent(StatusFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	public LogFragment(){
		super();

		if(log.isTraceEnabled())
			log.trace(LogFragment.class.getSimpleName() + " : Create instance.");

		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}

	public static LogFragment getInstance(EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}

		LogFragment instance = new LogFragment();
		LogFragment.setEventBus(eventBus);

		return instance;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Icepick.restoreInstanceState(this, savedInstanceState);

		logBuffer = new LinkedList<>();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.log_layout, null);

		ButterKnife.bind(this, view);

		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		textViewLog.setMovementMethod(new ScrollingMovementMethod());

		buttonLogClose.setOnClickListener(buttonLogCloseOnClickListener);
	}
	
	@Override
	public void onStart(){
		super.onStart();
		
		getEventBus().register(this);
	}
	
	@Override
	public void onResume() {
		super.onResume();

		getEventBus().post(
				new StatusFragment.LogFragmentEvent(
						StatusFragment.LogFragmentEventType.RequestSavedLogs,
						this
				)
		);
	}
	
	@Override
	public void onStop(){
		super.onStop();
		
		getEventBus().unregister(this);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		Icepick.saveInstanceState(this, outState);
	}

	@Override
	public void onDestroyView() {

		super.onDestroyView();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}


	View.OnClickListener buttonLogCloseOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			getFragmentManager().popBackStack();
		}
	};

	private boolean addLog(String message){
		if(message == null || "".equals(message)){return false;}

		synchronized (logBuffer) {
			while (logBuffer.size() >= logLimit) {
				logBuffer.poll();
			}
			logBuffer.add(message);
		}

//		updateLogView();

		return true;
	}

	private void clearLog(){
		synchronized(logBuffer){
			logBuffer.clear();
		}

//		updateLogView();
	}

	private void updateLogView(){
		List<String> logs = null;
		synchronized(logBuffer) {
			logs = new LinkedList<>(logBuffer);
		}
		Collections.reverse(logs);
		
		StringBuilder sb = new StringBuilder();
		for(String log : logs){sb.append(log);}
		
		textViewLog.setText(sb.toString());
	}

	private void updateView(){
		updateLogView();
	}
}
