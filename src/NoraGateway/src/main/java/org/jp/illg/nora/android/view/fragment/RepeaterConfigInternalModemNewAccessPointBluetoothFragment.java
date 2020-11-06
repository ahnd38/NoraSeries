package org.jp.illg.nora.android.view.fragment;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalModemNewAccessPointBluetoothFragmentData;
import org.jp.illg.nora.android.view.model.ModemNewAccessPointBluetoothConfig;
import org.jp.illg.nora.android.view.model.ModemNewAccessPointBluetoothConfigBundler;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.FragmentUtil;
import org.jp.illg.util.android.view.EventBusEvent;
import org.jp.illg.util.android.view.ViewUtil;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterConfigInternalModemNewAccessPointBluetoothFragment extends FragmentBase {
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;
	
	@Getter
	@Setter
	@State(ModemNewAccessPointBluetoothConfigBundler.class)
	ModemNewAccessPointBluetoothConfig modemNewAccessPointBluetoothConfig;
	
	@State
	boolean serviceRunning;
	
	@BindView(R.id.spinnerRepeaterConfigModemNewAccessPointBluetoothPort)
	Spinner spinnerRepeaterConfigModemNewAccessPointBluetoothPort;
	
	@BindView(R.id.buttonRepeaterConfigModemNewAccessPointBluetoothRefresh)
	Button buttonRepeaterConfigModemNewAccessPointBluetoothRefresh;
	
	@BindView(R.id.switchRepeaterConfigModemNewAccessPointBluetoothAllowDVSimplex)
	Switch switchRepeaterConfigModemNewAccessPointBluetoothAllowDVSimplex;
	
	
	public static class RepeaterConfigInternalFragmentEvent
			extends EventBusEvent<RepeaterConfigInternalModemNewAccessPointBluetoothFragment.RepeaterConfigInternalFragmentEventType> {
		public RepeaterConfigInternalFragmentEvent(RepeaterConfigInternalModemNewAccessPointBluetoothFragment.RepeaterConfigInternalFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}
	
	public enum RepeaterConfigInternalFragmentEventType{
		UpdateData{
			@Override
			void apply(RepeaterConfigInternalModemNewAccessPointBluetoothFragment fragment, Object attachment){
				if(
						attachment != null &&
								attachment instanceof RepeaterConfigInternalModemNewAccessPointBluetoothFragmentData
				){
					RepeaterConfigInternalModemNewAccessPointBluetoothFragmentData data =
							(RepeaterConfigInternalModemNewAccessPointBluetoothFragmentData)attachment;
					
					fragment.serviceRunning = data.isServiceRunning();
					fragment.setModemNewAccessPointBluetoothConfig(data.getModemNewAccessPointBluetoothConfig());
					
					fragment.updateView();
				}
			}
		},
		RequestSaveConfig{
			void apply(RepeaterConfigInternalModemNewAccessPointBluetoothFragment fragment, Object attachment){
				
				fragment.sendConfigToParent();
			}
		}
		;
		
		abstract void apply(RepeaterConfigInternalModemNewAccessPointBluetoothFragment fragment, Object attachment);
	}
	
	{
		modemNewAccessPointBluetoothConfig = new ModemNewAccessPointBluetoothConfig();
	}
	
	public RepeaterConfigInternalModemNewAccessPointBluetoothFragment(){
		super();
		
		if(log.isTraceEnabled())
			log.trace(RepeaterConfigInternalModemNewAccessPointBluetoothFragment.class.getSimpleName() + " : Create instance.");
		
		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}
	
	public static RepeaterConfigInternalModemNewAccessPointBluetoothFragment getInstance(final EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}
		
		RepeaterConfigInternalModemNewAccessPointBluetoothFragment instance =
				new RepeaterConfigInternalModemNewAccessPointBluetoothFragment();
		RepeaterConfigInternalModemNewAccessPointBluetoothFragment.setEventBus(eventBus);
		
		return instance;
	}
	
	@Subscribe
	public void onRepeaterConfigInternalFragmentEvent(
			RepeaterConfigInternalModemNewAccessPointBluetoothFragment.RepeaterConfigInternalFragmentEvent event
	){
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
		View view = inflater.inflate(R.layout.repeater_config_internal_nap_bluetooth, null);
		
		ButterKnife.bind(this, view);
		
		return view;
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		buttonRepeaterConfigModemNewAccessPointBluetoothRefresh.setOnClickListener(
				buttonRepeaterConfigModemNewAccessPointBluetoothRefreshOnClickListener
		);
		
		spinnerRepeaterConfigModemNewAccessPointBluetoothPort.setOnItemSelectedListener(
				spinnerRepeaterConfigModemNewAccessPointBluetoothPortOnItemSelectedListener
		);
		
		switchRepeaterConfigModemNewAccessPointBluetoothAllowDVSimplex.setOnCheckedChangeListener(
				switchRepeaterConfigModemNewAccessPointBluetoothAllowDVSimplexOnCheckedChangeListener
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
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEventType.OnFragmentCreated,
						this
				)
		);
		
		updateUartPorts();
		updateView();
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
	
	private Button.OnClickListener buttonRepeaterConfigModemNewAccessPointBluetoothRefreshOnClickListener =
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					updateUartPorts();
					
					updateView();
				}
			};
	
	private Spinner.OnItemSelectedListener spinnerRepeaterConfigModemNewAccessPointBluetoothPortOnItemSelectedListener =
			new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					final String portName =
							(String)spinnerRepeaterConfigModemNewAccessPointBluetoothPort.getItemAtPosition(position);
					
					getModemNewAccessPointBluetoothConfig().setPortName(portName);
				}
				
				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemNewAccessPointBluetoothAllowDVSimplexOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					
					getModemNewAccessPointBluetoothConfig().setAllowDVSimplex(isChecked);
				}
			};
	
	private void updateUartPorts(){
		BluetoothManager bluetoothManager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		if(bluetoothAdapter == null){return;}
		
		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		
		List<String> pairedDeviceNames = new ArrayList<>();
		for(final BluetoothDevice pairedDevice : pairedDevices){
			pairedDeviceNames.add(pairedDevice.getName());
		}
		
		updateUartPorts(pairedDeviceNames.toArray(new String[pairedDeviceNames.size()]));
	}
	
	private void updateUartPorts(String[] ports){
		if(ports == null){ports = new String[]{};}
		
		List<String> portList = new LinkedList<>();
		for(String port : ports){portList.add(port);}
		
		String selectedPort =
				getModemNewAccessPointBluetoothConfig().getPortName();
		if(selectedPort == null){selectedPort = "";}
		
		boolean selectedPortAvailable = false;
		for(String port : portList) {
			if (port != null && selectedPort.equals(port))
				selectedPortAvailable = true;
		}
		
		if(!selectedPortAvailable && !"".equals(selectedPort)){
			portList.add(selectedPort);
		}
		
		ArrayAdapter<String> portAdapter =
				new ArrayAdapter<String>(
						getContext(),
						R.layout.spinner_list_style,
						ports
				);
		portAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
		Spinner.OnItemSelectedListener listener =
				spinnerRepeaterConfigModemNewAccessPointBluetoothPort.getOnItemSelectedListener();
		spinnerRepeaterConfigModemNewAccessPointBluetoothPort.setOnItemSelectedListener(null);
		spinnerRepeaterConfigModemNewAccessPointBluetoothPort.setAdapter(portAdapter);
		spinnerRepeaterConfigModemNewAccessPointBluetoothPort.setOnItemSelectedListener(listener);
	}
	
	private boolean updateView() {
		
		ViewUtil.consumerWhileViewDisabled(
				spinnerRepeaterConfigModemNewAccessPointBluetoothPort,
				new Consumer<Spinner>() {
					@Override
					public void accept(Spinner spinner) {
						SpinnerAdapter portAdapter = spinner.getAdapter();
						
						if (portAdapter != null) {
							int portCount = portAdapter.getCount();
							
							for (int index = 0; index < portCount; index++) {
								String port = (String) portAdapter.getItem(index);
								
								if (port.equals(getModemNewAccessPointBluetoothConfig().getPortName())) {
									spinner.setOnItemSelectedListener(null);
									spinner.setSelection(index, false);
									spinner.setOnItemSelectedListener(spinnerRepeaterConfigModemNewAccessPointBluetoothPortOnItemSelectedListener);
									break;
								}
							}
						}
					}
				}
		);
		
		
		switchRepeaterConfigModemNewAccessPointBluetoothAllowDVSimplex.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemNewAccessPointBluetoothAllowDVSimplex.setChecked(getModemNewAccessPointBluetoothConfig().isAllowDVSimplex());
		switchRepeaterConfigModemNewAccessPointBluetoothAllowDVSimplex.setOnCheckedChangeListener(
				switchRepeaterConfigModemNewAccessPointBluetoothAllowDVSimplexOnCheckedChangeListener
		);
		
		return true;
	}
	
	private void sendConfigToParent(){
		getEventBus().post(
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEventType.UpdateConfig,
						getModemNewAccessPointBluetoothConfig().clone()
				)
		);
	}
}
