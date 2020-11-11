package org.jp.illg.noravrclient;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.jp.illg.noravrclient.util.DialogFragmentBase;

import butterknife.BindView;
import butterknife.ButterKnife;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraVRReceiveGPSLocationDialog extends DialogFragmentBase {
	
	public static final String IDEXT_CALLSIGN = "Callsign";
	public static final String IDEXT_MESSAGE = "Message";
	public static final String IDEXT_LATITUDE = "Latitude";
	public static final String IDEXT_LONGITUDE = "Longitude";
	
	
	@BindView(R.id.relativeLayoutGPSLocation)
	RelativeLayout relativeLayoutGPSLocation;
	
	@BindView(R.id.textViewGPSLocationCallsign)
	TextView textViewGPSLocationCallsign;
	
	@BindView(R.id.textViewGPSLocationMessage)
	TextView textViewGPSLocationMessage;
	
	@BindView(R.id.mapViewGPSLocation)
	MapView mapViewGPSLocation;
	
	@BindView(R.id.textViewGPSLocationRemainTime)
	TextView textViewGPSLocationRemainTime;
	
	private Handler handler;
	
	private GoogleMap googleMap;
	
	private String callsign, message;
	private double latitude, longitude;
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View content = inflater.inflate(R.layout.receive_gps_location_dialog, null);
		
		ButterKnife.bind(this, content);
		
		mapViewGPSLocation.onCreate(savedInstanceState);
		mapViewGPSLocation.getMapAsync(new OnMapReadyCallback() {
			@Override
			public void onMapReady(GoogleMap googleMap) {
				setupMap(googleMap);
			}
		});
		
		if(getArguments() != null){
			callsign = getArguments().getString(IDEXT_CALLSIGN, "");
			textViewGPSLocationCallsign.setText(callsign);
			
			message = getArguments().getString(IDEXT_MESSAGE, "");
			textViewGPSLocationMessage.setText(message);
			
			final String latitudeString =
					getArguments().getString(IDEXT_LATITUDE, "0");
			latitude = 0;
			try {
				latitude = Double.valueOf(latitudeString);
			}catch(NumberFormatException ex){
				log.warn("Could not convert latitude(" + latitudeString + ") to number.", ex);
			}
			
			final String longitudeString =
					getArguments().getString(IDEXT_LONGITUDE, "0");
			longitude = 0;
			try{
				longitude = Double.valueOf(longitudeString);
			}catch(NumberFormatException ex) {
				log.warn("Could not convert longitude(" + longitudeString + ") to number.", ex);
			}
			
			
		}
		
		relativeLayoutGPSLocation.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				remainTimer.cancel();
				textViewGPSLocationRemainTime.setText("-");
			}
		});
		
		remainTimer.start();
		
		builder.setView(content);
		
		builder
				.setTitle("GPS Location Info")
				.setMessage(null)
				.setPositiveButton(
						"OK",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int id) {
								final Intent data  = new Intent();
								
								notifyDialogResult(id, data);
							}
						}
				);
		
		return builder.create();
	}
	
	@Override
	public void onResume() {
		mapViewGPSLocation.onResume();
		super.onResume();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		mapViewGPSLocation.onPause();
		
		remainTimer.cancel();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		mapViewGPSLocation.onDestroy();
	}
	
	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapViewGPSLocation.onLowMemory();
	}
	
	private final CountDownTimer remainTimer = new CountDownTimer(10000, 1000) {
		@Override
		public void onTick(long l) {
			textViewGPSLocationRemainTime.setText(String.valueOf((int)(l / 1000)));
		}
		
		@Override
		public void onFinish() {
			NoraVRReceiveGPSLocationDialog.this.dismiss();
		}
	};
	
	public static class Builder extends DialogFragmentBase.Builder {
		
		@Getter
		private String callsign = "";
		
		@Getter
		private String message = "";
		
		@Getter
		private String latitude = "0";
		
		@Getter
		private String longitude = "0";
		
		public Builder setCallsign(final String callsign) {
			this.callsign = callsign;
			
			return this;
		}
		
		public Builder setMessage(final String message) {
			this.message = message;
			
			return this;
		}
		
		public Builder setLatitude(final String latitude) {
			this.latitude = latitude;
			
			return this;
		}
		
		public Builder setLongitude(final String longitude) {
			this.longitude = longitude;
			
			return this;
		}
		
		@Override
		protected DialogFragmentBase build(){
			final DialogFragmentBase dialog = new NoraVRReceiveGPSLocationDialog();
			
			final Bundle data = new Bundle();
			
			data.putString(IDEXT_CALLSIGN, callsign);
			data.putString(IDEXT_MESSAGE, message);
			data.putString(IDEXT_LATITUDE, latitude);
			data.putString(IDEXT_LONGITUDE, longitude);
			
			dialog.setArguments(data);
			
			return dialog;
		}
	}
	
	private void setupMap(final GoogleMap googleMap) {
		this.googleMap = googleMap;
		
		final CameraUpdate cameraUpdate =
			CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 10);
		
		googleMap.moveCamera(cameraUpdate);
		
		final MarkerOptions markerOptions = new MarkerOptions();
		markerOptions.position(new LatLng(latitude, longitude));
		markerOptions.title(callsign);
		markerOptions.snippet(message);
		
		final Marker marker = googleMap.addMarker(markerOptions);
		marker.showInfoWindow();
	}
}
