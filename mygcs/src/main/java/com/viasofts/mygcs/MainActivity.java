package com.viasofts.mygcs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.maps.model.Polyline;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.android.client.utils.video.MediaCodecManager;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.EkfStatus;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    private NaverMap mNaverMap;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    private Marker droneGpsMarker = new Marker();
    private Marker droneArrival = new Marker();
    private PolylineOverlay dronePath = new PolylineOverlay();
    //private Spinner modeSelector;

    private double setAltitude = 5.5;
    private String textAltitude = "";

    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        mainHandler = new Handler(getApplicationContext().getMainLooper());

        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        setNaverMap(); //네이버맵
        onBtnConnectTap(); //기체 연결 버튼
        updateAltitudeButton(); //이륙고도 버튼
        updateMapTypeButton(); //지도 유형 버튼
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.mNaverMap = naverMap;
        naverMap.setMapType(NaverMap.MapType.Hybrid);

        //줌버튼 비활성화
        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setZoomControlEnabled(false);

        //내 위치 표시
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

        LocationOverlay locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
    }

    //위치 권한 설정하기
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) { // 권한 거부됨
                mNaverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //네이버맵
    protected void setNaverMap() {
        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        //updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateArmButton();
                checkSoloState();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateArmButton();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    //updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBattery();
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.GPS_COUNT:
                updateGpsCount();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                updateYaw();
                break;

            case AttributeEvent.GPS_POSITION:
                updateGpsPosition();
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    // 기체 연결 버튼, UI
    public void onBtnConnectTap() {
        final Button btnConnect = (Button) findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (drone.isConnected()) {
                    drone.disconnect();
                    btnConnect.setText("Disconnect");
                    btnConnect.setBackgroundResource(R.drawable.offbutton);
                } else {
                    ConnectionParameter connectionParams = ConnectionParameter.newUdpConnection(null);
                    drone.connect(connectionParams);
                    btnConnect.setText("Connect");
                    btnConnect.setBackgroundResource(R.drawable.onbutton);
                }
            }
        });
    }

    //Arm 버튼 UI
    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.btnArmTakeOff);

        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    //Arm 버튼
    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            OnTakeOffClickHandler();
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed
            OnArmClickHandler();
        }
    }

    //이륙고도 버튼, UI
    protected void updateAltitudeButton() {
        final Button btnAltitude = (Button) findViewById(R.id.btnAltitude),
                btnAltiUp = (Button) findViewById(R.id.btnAltitudeUp),
                btnAltiDown = (Button) findViewById(R.id.btnAltitudeDown);

        btnAltitude.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnAltiUp.getVisibility() == v.INVISIBLE) {
                    btnAltiUp.setVisibility(View.VISIBLE);
                    btnAltiDown.setVisibility(View.VISIBLE);
                } else {
                    btnAltiUp.setVisibility(View.INVISIBLE);
                    btnAltiDown.setVisibility(View.INVISIBLE);
                }
            }
        });

        //final Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);

        btnAltiUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (setAltitude < 10) {
                    setAltitude += 0.5;
                    textAltitude = setAltitude + "M\n이륙고도";
                    btnAltitude.setText(textAltitude);
                }
                //droneAltitude.setAltitude(droneAltitude.getTargetAltitude() + setAltitude);

                //Log.d("mylog_alti", "이륙고도 Up: " + droneAltitude.getAltitude());
                Log.d("test_set", "onClick: " + setAltitude);
                //altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
            }
        });

        btnAltiDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (setAltitude > 3) {
                    setAltitude -= 0.5;
                    textAltitude = setAltitude + "M\n이륙고도";
                    btnAltitude.setText(textAltitude);
                }
                //droneAltitude.setAltitude(droneAltitude.getTargetAltitude() - setAltitude);

                //Log.d("mylog_alti", "이륙고도 Down: " + droneAltitude.getAltitude());
                Log.d("test_set", "onClick: " + setAltitude);
                //altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
            }
        });
    }

    //지도 유형 버튼, UI
    protected void updateMapTypeButton() {
        final Button btnMapStatic = (Button) findViewById(R.id.btnMapStatic);
        final Button btnMapSatellite = (Button) findViewById(R.id.btnMapSatellite);
        final Button btnMapTerrain = (Button) findViewById(R.id.btnMapTerrain);
        final Button btnMapBasic = (Button) findViewById(R.id.btnMapBasic);

        btnMapStatic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnMapSatellite.getVisibility() == v.INVISIBLE) {
                    btnMapSatellite.setVisibility(View.VISIBLE);
                    btnMapTerrain.setVisibility(View.VISIBLE);
                    btnMapBasic.setVisibility(View.VISIBLE);
                } else {
                    btnMapSatellite.setVisibility(View.INVISIBLE);
                    btnMapTerrain.setVisibility(View.INVISIBLE);
                    btnMapBasic.setVisibility(View.INVISIBLE);
                }
            }
        });

        btnMapSatellite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNaverMap.setMapType(NaverMap.MapType.Satellite);
                btnMapSatellite.setBackgroundResource(R.drawable.onbutton);
                btnMapTerrain.setBackgroundResource(R.drawable.offbutton);
                btnMapBasic.setBackgroundResource(R.drawable.offbutton);
                btnMapStatic.setText("위성지도");
            }
        });

        btnMapTerrain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNaverMap.setMapType(NaverMap.MapType.Terrain);
                btnMapSatellite.setBackgroundResource(R.drawable.offbutton);
                btnMapTerrain.setBackgroundResource(R.drawable.onbutton);
                btnMapBasic.setBackgroundResource(R.drawable.offbutton);
                btnMapStatic.setText("지형도");
            }
        });

        btnMapBasic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNaverMap.setMapType(NaverMap.MapType.Basic);
                btnMapSatellite.setBackgroundResource(R.drawable.offbutton);
                btnMapTerrain.setBackgroundResource(R.drawable.offbutton);
                btnMapBasic.setBackgroundResource(R.drawable.onbutton);
                btnMapStatic.setText("일반지도");
            }
        });

    }
    
    //드론 gps 마커
    protected void updateGpsPosition() {
        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong droneGpsPosition = droneGps.getPosition();
        double droneYaw = 0;

        if (attitude.getYaw() < 0) {
            droneYaw = 360 + attitude.getYaw();
        } else {
            droneYaw = attitude.getYaw();
        }

        droneGpsMarker.setPosition(new LatLng(droneGpsPosition.getLatitude(), droneGpsPosition.getLongitude()));
        droneGpsMarker.setWidth(100);
        droneGpsMarker.setHeight(100);
        droneGpsMarker.setAngle((float) droneYaw);
        droneGpsMarker.setIcon(OverlayImage.fromResource(R.drawable.dronemaker));
        droneGpsMarker.setMap(mNaverMap);

        //updateGpsPolyLine();

        Log.d("mylog", "위도: " + droneGpsPosition.getLatitude() + ", 경도: " + droneGpsPosition.getLongitude());
    }

    protected void updateBattery() {
        TextView voltageTextView = (TextView) findViewById(R.id.batteryValueTextView);
        Battery droneBattery = this.drone.getAttribute(AttributeType.BATTERY);
        voltageTextView.setText(String.format("%3.1f", droneBattery.getBatteryVoltage()) + "V");
        Log.d("test", String.format("%3.1f", droneBattery.getBatteryVoltage()));
    }

    protected void updateVehicleMode() {
        TextView vehicleModeTextView = (TextView) findViewById(R.id.vehicleModeValueTextView);
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();

        if (vehicleMode == VehicleMode.COPTER_STABILIZE) {
            vehicleModeTextView.setText("STABILIZE");
        } else if (vehicleMode == VehicleMode.COPTER_LAND) {
            vehicleModeTextView.setText("LAND");
        } else if (vehicleMode == VehicleMode.COPTER_LOITER) {
            vehicleModeTextView.setText("LOITER");
        } else if (vehicleMode == VehicleMode.COPTER_GUIDED) {
            vehicleModeTextView.setText("GUIDED");
        } else {
            vehicleModeTextView.setText("UNKNOWN");
        }
    }

    protected void updateAltitude() {
        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    protected void updateSpeed() {
        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateYaw() {
        TextView yawTextView = (TextView) findViewById(R.id.yawValueTextView);
        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        if (attitude.getYaw() < 0) {
            yawTextView.setText(String.format("%3.1f", 360 + attitude.getYaw()) + "deg");
        } else {
            yawTextView.setText(String.format("%3.1f", attitude.getYaw()) + "deg");
        }
    }

    protected void updateGpsCount() {
        TextView gpsCountTextView = (TextView) findViewById(R.id.gpsCountValueTextView);
        Gps droneGpsCount = this.drone.getAttribute(AttributeType.GPS);
        gpsCountTextView.setText(String.format("%d", droneGpsCount.getSatellitesCount()) + "");

        Log.d("mylog_gps", "updateGpsCount: " + droneGpsCount.getSatellitesCount());
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null) {
            alertUser("Unable to retrieve the solo state.");
        } else {
            alertUser("Solo state is up to date.");
        }
    }

    public void OnArmClickHandler() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage("모터를 가동합니다.\n모터가 고속으로 회전합니다.");
        builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                VehicleApi.getApi(drone).arm(true, false, new SimpleCommandListener() {
                    @Override
                    public void onError(int executionError) {
                        alertUser("Unable to arm vehicle.");
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("Arming operation timed out.");
                    }
                });
            }
        });

        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Toast.makeText(getApplicationContext(), "Cancel Click", Toast.LENGTH_SHORT).show();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    public void OnTakeOffClickHandler() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage("지정한 이륙고도까지 기체가 상승합니다.\n안전거리를 유지하세요.");
        builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                ControlApi.getApi(drone).takeoff(10, new AbstractCommandListener() {

                    @Override
                    public void onSuccess() {
                        alertUser("Taking off...");
                    }

                    @Override
                    public void onError(int i) {
                        alertUser("Unable to take off.");
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("Unable to take off.");
                    }
                });
            }
        });

        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Toast.makeText(getApplicationContext(), "Cancel Click", Toast.LENGTH_SHORT).show();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {

    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Start");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    // Helper methods
    // ==========================================================

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }
}


    /* // 드론 모드 스피너
    public void setModeSpinner(){
        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    protected void updateVehicleModesForType(int droneType) {

        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }
    */