package com.viasofts.mygcs;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraUpdate;
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
import com.naver.maps.map.util.MarkerIcons;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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

    private Marker mGpsMarker = new Marker();
    private Marker mLineMarker = new Marker();
    private Marker mTargetMarker = new Marker();
    private ArrayList<LatLng> mLatLngArr = new ArrayList<>();
    private PolylineOverlay polyline = new PolylineOverlay();
    private Spinner modeSelector;

    private int click = 1;
    private double setAltitude = 5.5;
    private String textAltitude = "";

    private ArrayList<String> list = new ArrayList<>();

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
        setModeSpinner(); //드론 모드 스피너
        onBtnConnectTap(); //기체 연결 버튼
        updateAltitudeButton(); //이륙고도 버튼
        updateMapTypeButton(); //지도 유형 버튼
        updateMapCadastralButton(); //지적편집도 버튼
        updateCameraMove(); //카메라 버튼
        setOverlayClear(); //전체 오버레이 삭제 버튼
        setRecyclerView(); //리사이클러뷰
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

        guidedModeLongClick(); //가이드모드 롱-클릭
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
        updateVehicleModesForType(this.droneType);
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
                    updateVehicleModesForType(this.droneType);
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


    //--------------------Drone UI--------------------//

    //기체 연결 버튼, UI
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

    //드론 모드 스피너
    public void setModeSpinner() {
        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((TextView) parent.getChildAt(0)).setTextColor(Color.WHITE);
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
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
                    alertUser("Timeout to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            onTakeOffClickHandler();
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed
            onArmClickHandler();
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

        btnAltiUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (setAltitude < 10) {
                    setAltitude += 0.5;
                    textAltitude = setAltitude + "M\n이륙고도";
                    btnAltitude.setText(textAltitude);
                }
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

    //지적편집도 버튼
    protected void updateMapCadastralButton() {
        final Button btnCadastral = (Button) findViewById(R.id.btnCadastral);

        btnCadastral.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (click == 1) {
                    mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                    btnCadastral.setBackgroundResource(R.drawable.onbutton);
                    btnCadastral.setText("지적도\non");
                    click = 0;
                } else {
                    mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                    btnCadastral.setBackgroundResource(R.drawable.offbutton);
                    btnCadastral.setText("지적도\noff");
                    click = 1;
                }
            }
        });
    }

    //카메라 이동 잠금
    protected void updateCameraMove() {
        final Button btnCamera = (Button) findViewById(R.id.btnCameraStatic);
        final Button btnCameraMove = (Button) findViewById(R.id.btnCameraMove);
        final Button btnCameraUnMove = (Button) findViewById(R.id.btnCameraUnMove);

        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnCameraMove.getVisibility() == v.INVISIBLE) {
                    btnCameraMove.setVisibility(View.VISIBLE);
                    btnCameraUnMove.setVisibility(View.VISIBLE);
                } else {
                    btnCameraMove.setVisibility(View.INVISIBLE);
                    btnCameraUnMove.setVisibility(View.INVISIBLE);
                }
            }
        });

        btnCameraMove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnCameraMove.setBackgroundResource(R.drawable.onbutton);
                btnCameraUnMove.setBackgroundResource(R.drawable.offbutton);
                btnCamera.setText("맵 이동");
            }
        });

        btnCameraUnMove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(mGpsMarker.getPosition().latitude, mGpsMarker.getPosition().longitude)).animate(CameraAnimation.Easing, 500);
                mNaverMap.moveCamera(cameraUpdate);

                btnCameraMove.setBackgroundResource(R.drawable.offbutton);
                btnCameraUnMove.setBackgroundResource(R.drawable.onbutton);
                btnCamera.setText("맵 잠금");
            }
        });
    }

    //드론 gps 마커
    protected void updateGpsPosition() {
        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong droneGpsPosition = droneGps.getPosition();
        double droneYaw = 0;

        setGpsPolyLine(); //비행경로

        if (attitude.getYaw() < 0) {
            droneYaw = 360 + attitude.getYaw();
        } else {
            droneYaw = attitude.getYaw();
        }

        mLineMarker.setPosition(new LatLng(droneGpsPosition.getLatitude(), droneGpsPosition.getLongitude()));
        mLineMarker.setWidth(80);
        mLineMarker.setHeight(300);
        mLineMarker.setAngle((float) droneYaw);
        mLineMarker.setIcon(OverlayImage.fromResource(R.drawable.dronelinemarker));
        mLineMarker.setMap(mNaverMap);

        mGpsMarker.setPosition(new LatLng(droneGpsPosition.getLatitude(), droneGpsPosition.getLongitude()));
        mGpsMarker.setWidth(80);
        mGpsMarker.setHeight(80);
        mGpsMarker.setAngle((float) droneYaw);
        mGpsMarker.setIcon(OverlayImage.fromResource(R.drawable.dronemaker));
        mGpsMarker.setMap(mNaverMap);

        Log.d("mylog", "위도: " + droneGpsPosition.getLatitude() + ", 경도: " + droneGpsPosition.getLongitude());
    }

    //비행 경로 그리기
    protected void setGpsPolyLine() {
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong droneGpsPosition = droneGps.getPosition();

        mLatLngArr.add(new LatLng(droneGpsPosition.getLatitude(), droneGpsPosition.getLongitude()));

        for (int i = 0; i < mLatLngArr.size(); i++) {
            polyline.setCoords(mLatLngArr);
        }

        polyline.setColor(Color.WHITE);
        polyline.setWidth(3);
        polyline.setMap(mNaverMap);
    }

    //전체 오버레이 삭제
    protected void setOverlayClear(){
        Button btnClear = (Button) findViewById(R.id.btnClear);

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                list.clear();
                mLatLngArr.clear();
                polyline.setMap(null);

                mTargetMarker.setMap(null);

            }
        });
    }

    //리사이클러뷰 추가
    protected void setRecyclerView(){
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        SimpleTextAdapter adapter = new SimpleTextAdapter(list);
        recyclerView.setAdapter(adapter);

        //5개 넘어가면 삭제
//        if(list.size() > 5){
//
//        }
    }


    //--------------------Guided Mode--------------------//

    //가이드 모드 롱클릭
    protected void guidedModeLongClick() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        final VehicleMode vehicleMode = vehicleState.getVehicleMode();

        mNaverMap.setOnMapLongClickListener(new NaverMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull @NotNull PointF pointF, @NonNull @NotNull LatLng latLng) {
                mTargetMarker.setPosition(new LatLng(latLng.latitude, latLng.longitude));
                goalMarkerCustom();

                if (vehicleMode == VehicleMode.COPTER_GUIDED) {
                    mTargetMarker.setMap(null);
                    mTargetMarker.setPosition(new LatLng(latLng.latitude, latLng.longitude));
                    goalMarkerCustom();

                    goToTarget();

                } else {
                    onGuidedModeClickHandler();
                }
            }
        });
    }

    //가이드 모드 전환
    private void updateGuidedMode() {
        VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Guided MODE...");
                Log.d("mylog_test", "가이드모드 전환");
            }

            @Override
            public void onError(int i) {
                alertUser("Error to Guided MODE.");
            }

            @Override
            public void onTimeout() {
                alertUser("Time Out to Guided MODE.");
            }
        });
    }

    //가이드 모드 목적지 이동 및 도착
    private void goToTarget() {
        ControlApi.getApi(drone).goTo(new LatLong(mTargetMarker.getPosition().latitude, mTargetMarker.getPosition().longitude), true, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("목적지로 이동합니다.");
                Log.d("mylog_test", "타겟 이동 중");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Error to Guided Mode.");
            }

            @Override
            public void onTimeout() {
                alertUser("Timeout to Guided Mode.");
            }
        });

        if (CheckGoal(drone, new LatLng(mGpsMarker.getPosition().latitude, mGpsMarker.getPosition().longitude))) {
            alertUser("기체가 목적지에 도착했습니다.");
            updateLoiterMode();
            mTargetMarker.setMap(null);
            Log.d("mylog_test", "목적지 도착, 타겟 마크 삭제");
        }
    }

    //로이터 모드 전환
    private void updateLoiterMode() {
        VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("LOITER MODE...");
                Log.d("mylog_test", "로이터모드 전환");
            }

            @Override
            public void onError(int i) {
                alertUser("Error to LOITER MODE.");
            }

            @Override
            public void onTimeout() {
                alertUser("Time Out to LOITER MODE.");
            }
        });
    }

    //목적지 도착 확인
    public static boolean CheckGoal(final Drone drone, LatLng recentLatLng) {
        GuidedState guidedState = drone.getAttribute(AttributeType.GUIDED_STATE);
        LatLng target = new LatLng(guidedState.getCoordinate().getLatitude(), guidedState.getCoordinate().getLongitude());
        return target.distanceTo(recentLatLng) <= 3;
    }

    //가이드모드 마커 커스텀
    private void goalMarkerCustom() {
        mTargetMarker.setIcon(MarkerIcons.BLACK);
        mTargetMarker.setIconTintColor(Color.YELLOW);
        mTargetMarker.setWidth(80);
        mTargetMarker.setHeight(80);
        mTargetMarker.setMap(mNaverMap);
    }


    //--------------------drone Warning--------------------//

    //Arm 경고 메세지
    public void onArmClickHandler() {
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
                dialog.cancel();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    //TakeOff 경고 메세지
    public void onTakeOffClickHandler() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage("지정한 이륙고도까지 기체가 상승합니다.\n안전거리를 유지하세요.");
        builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                ControlApi.getApi(drone).takeoff(setAltitude, new AbstractCommandListener() {

                    @Override
                    public void onSuccess() {
                        alertUser("Taking off...");
                    }

                    @Override
                    public void onError(int i) {
                        alertUser("Error to take off.");
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("Timeout to take off.");
                    }
                });
            }
        });

        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    //GuidedMode 경고 메세지
    public void onGuidedModeClickHandler() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("가이드모드 전환");
        builder.setMessage("현재 고도를 유지하며\n목표 지점까지 기체가 이동합니다.");
        builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                updateGuidedMode();
                goToTarget(); //7.27 추가
            }
        });

        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                mTargetMarker.setMap(null);
                dialog.cancel();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }


    //--------------------drone event--------------------//

    protected void updateBattery() {
        TextView voltageTextView = (TextView) findViewById(R.id.batteryValueTextView);
        Battery droneBattery = this.drone.getAttribute(AttributeType.BATTERY);
        voltageTextView.setText(String.format("%3.1f", droneBattery.getBatteryVoltage()) + "V");
        Log.d("test", String.format("%3.1f", droneBattery.getBatteryVoltage()));
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
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
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null) {
            alertUser("Unable to retrieve the solo state.");
        } else {
            alertUser("Solo state is up to date.");
        }
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
        //Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);

        list.add(String.format("★ " + message));
        setRecyclerView();
    }

    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }
}