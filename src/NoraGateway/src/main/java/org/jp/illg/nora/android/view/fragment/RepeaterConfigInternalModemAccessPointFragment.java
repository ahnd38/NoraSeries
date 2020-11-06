package org.jp.illg.nora.android.view.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalModemAccessPointFragmentData;
import org.jp.illg.nora.android.view.model.ModemAccessPointConfig;
import org.jp.illg.nora.android.view.model.ModemAccessPointConfigBundler;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.FragmentUtil;
import org.jp.illg.util.android.view.EventBusEvent;
import org.jp.illg.util.android.view.ViewUtil;

import java.util.LinkedList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterConfigInternalModemAccessPointFragment extends FragmentBase {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;

	@BindView(R.id.spinnerRepeaterConfigModemAccessPointPort)
	Spinner spinnerRepeaterConfigModemAccessPointPort;

	@BindView(R.id.buttonRepeaterConfigModemAccessPointRefresh)
	Button buttonRepeaterConfigModemAccessPointRefresh;
	
	@BindView(R.id.switchRepeaterConfigModemAccessPointTerminalMode)
	Switch switchRepeaterConfigModemAccessPointTerminalMode;

	@Getter
	@Setter
	@State(ModemAccessPointConfigBundler.class)
	ModemAccessPointConfig modemAccessPointConfig;

	@State
	boolean serviceRunning;

	{
		modemAccessPointConfig = new ModemAccessPointConfig();
	}


	public static class RepeaterConfigInternalFragmentEvent extends EventBusEvent<RepeaterConfigInternalFragmentEventType> {
		public RepeaterConfigInternalFragmentEvent(RepeaterConfigInternalFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigInternalFragmentEventType{
		UpdateData{
			@Override
			void apply(RepeaterConfigInternalModemAccessPointFragment fragment, Object attachment){
				if(
					attachment != null &&
					attachment instanceof RepeaterConfigInternalModemAccessPointFragmentData
				){
					RepeaterConfigInternalModemAccessPointFragmentData data =
							(RepeaterConfigInternalModemAccessPointFragmentData)attachment;

					fragment.serviceRunning = data.isServiceRunning();
					fragment.setModemAccessPointConfig(data.getModemAccessPointConfig());

					fragment.updateView();
				}
			}
		},
		ResultUartPorts{
			@Override
			void apply(RepeaterConfigInternalModemAccessPointFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof String[]){
					fragment.updateUartPorts((String[])attachment);

					fragment.updateView();
				}
			}
		},
		RequestSaveConfig{
			@Override
			void apply(RepeaterConfigInternalModemAccessPointFragment fragment, Object attachment){
				fragment.sendConfigToParent();
			}
		}
		;

		abstract void apply(RepeaterConfigInternalModemAccessPointFragment fragment, Object attachment);
	}


	public RepeaterConfigInternalModemAccessPointFragment(){
		super();

		if(log.isTraceEnabled())
			log.trace(RepeaterConfigInternalModemAccessPointFragment.class.getSimpleName() + " : Create instance.");

		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}

	/**
	 * create instance
	 * @param eventBus eventbus
	 * @return instance
	 */
	public static RepeaterConfigInternalModemAccessPointFragment getInstance(EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}

		RepeaterConfigInternalModemAccessPointFragment instance =
				new RepeaterConfigInternalModemAccessPointFragment();
		RepeaterConfigInternalModemAccessPointFragment.setEventBus(eventBus);

		return instance;
	}
/*
	@Subscribe
	public void onMainActivityEvent(MainActivityEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}
*/
	@Subscribe
	public void onRepeaterConfigInternalFragmentEvent(RepeaterConfigInternalFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Icepick.restoreInstanceState(this, savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.repeater_config_internal_ap, null);

		ButterKnife.bind(this, view);

		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		buttonRepeaterConfigModemAccessPointRefresh.setOnClickListener(
				buttonRepeaterConfigModemAccessPointRefreshOnClickListener
		);
		
		spinnerRepeaterConfigModemAccessPointPort.setOnItemSelectedListener(
				spinnerRepeaterConfigModemAccessPointPortOnItemSelectedListener
		);
	}
	
	@Override
	public void onStart(){
		super.onStart();
		
		getEventBus().register(this);
	}
	
	@Override
	public void onResume(){
		super.onResume();

		getEventBus().post(
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemAccessPointFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemAccessPointFragmentEventType.OnFragmentCreated,
						this
				)
		);

		sendRequestUartPorts();
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

	private Button.OnClickListener buttonRepeaterConfigModemAccessPointRefreshOnClickListener =
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					sendRequestUartPorts();
				}
			};

	private Spinner.OnItemSelectedListener spinnerRepeaterConfigModemAccessPointPortOnItemSelectedListener =
			new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					final String portName =
							(String)spinnerRepeaterConfigModemAccessPointPort.getItemAtPosition(position);
					
					getModemAccessPointConfig().setPortName(portName);
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {

				}
			};

	private CheckBox.OnCheckedChangeListener switchRepeaterConfigModemAccessPointTerminalModeOnClickListener =
			new CheckBox.OnCheckedChangeListener(){
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					getModemAccessPointConfig().setTerminalMode(isChecked);
				}
			};

	private void updateUartPorts(String[] ports){
		if(ports == null){ports = new String[]{};}

		List<String> portList = new LinkedList<>();
		for(String port : ports){portList.add(port);}

		String selectedPort =
				modemAccessPointConfig.getPortName();
//				(String)spinnerRepeaterConfigModemAccessPointPort.getSelectedItem();
		if(selectedPort == null){selectedPort = "";}

		boolean selectedPortAvailable = false;
		for(String port : portList) {
			if (port != null && selectedPort.equals(port))
				selectedPortAvailable = true;
		}

		if(!selectedPortAvailable && !"".equals(selectedPort)){
//			selectedPort = "[X]" + selectedPort;
			portList.add(selectedPort);
		}

		ArrayAdapter<String> portAdapter =
				new ArrayAdapter<String>(
						getContext(),
						R.layout.spinner_list_style,
						ports
				);
		portAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
		spinnerRepeaterConfigModemAccessPointPort.setAdapter(portAdapter);
	}

	private boolean updateView(){
		ViewUtil.consumerWhileViewDisabled(
				spinnerRepeaterConfigModemAccessPointPort,
				new Consumer<Spinner>() {
					@Override
					public void accept(Spinner spinner) {
						SpinnerAdapter portAdapter = spinner.getAdapter();
						
						if(portAdapter != null) {
							int portCount = portAdapter.getCount();
							
							for (int index = 0; index < portCount; index++) {
								String port = (String) portAdapter.getItem(index);
								
								if (port.equals(getModemAccessPointConfig().getPortName())) {
									spinner.setOnItemSelectedListener(null);
									spinner.setSelection(index, false);
									spinner.setOnItemSelectedListener(spinnerRepeaterConfigModemAccessPointPortOnItemSelectedListener);
									break;
								}
							}
						}
					}
				}
		);
		
		ViewUtil.consumerWhileViewDisabled(
				switchRepeaterConfigModemAccessPointTerminalMode,
				new Consumer<Switch>() {
					@Override
					public void accept(final Switch switch_) {
							switch_.setOnCheckedChangeListener(null);
							switch_.setChecked(getModemAccessPointConfig().isTerminalMode());
							switch_.setOnCheckedChangeListener(switchRepeaterConfigModemAccessPointTerminalModeOnClickListener);
					}
				}
		);
		
		if(serviceRunning){
			spinnerRepeaterConfigModemAccessPointPort.setEnabled(false);
			buttonRepeaterConfigModemAccessPointRefresh.setEnabled(false);
			switchRepeaterConfigModemAccessPointTerminalMode.setEnabled(false);
		}
		else{
			spinnerRepeaterConfigModemAccessPointPort.setEnabled(true);
			buttonRepeaterConfigModemAccessPointRefresh.setEnabled(true);
			switchRepeaterConfigModemAccessPointTerminalMode.setEnabled(true);
		}
		
		return true;
	}

	private void sendRequestUartPorts(){
		getEventBus().post(
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemAccessPointFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemAccessPointFragmentEventType.RequestUartPorts,
						null
				)
		);
	}

	private void sendConfigToParent(){
		getEventBus().post(
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemAccessPointFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemAccessPointFragmentEventType.UpdateConfig,
						getModemAccessPointConfig().clone()
				)
		);
	}
}
