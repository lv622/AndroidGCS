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
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
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
import com.naver.maps.map.overlay.PolygonOverlay;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.MissionApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;
import com.o3dr.services.android.lib.util.MathUtils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    private Spinner modeSelector;

    private int click = 0;
    private int guidedCheck = 0;

    private double setAltitude = 5.5, setFlightWidth = 5.5;
    private int setDistanceTo = 50;
    private String textAltitude = "", textFlightWidth = "", textDistanceTo = "";

    private ArrayList<String> list = new ArrayList<>();

    private Marker mDroneMarker = new Marker();
    private Marker mTargetMarker = new Marker();
    private ArrayList<LatLng> mLatLngArr = new ArrayList<>();
    private PolylineOverlay mDronePolyline = new PolylineOverlay();

    private Mission mMission = new Mission();
    private Waypoint mWaypoint = new Waypoint();
    private ArrayList<Marker> mMissionMarkerArr = new ArrayList<>();
    private ArrayList<LatLng> mMissionLatLngArr = new ArrayList<>();

    private int mCheck = 0;
    private Marker mMarkerA = new Marker();
    private Marker mMarkerB = new Marker();
    private PolylineOverlay mABolyline = new PolylineOverlay();

    private ArrayList<Double> mAngleArr = new ArrayList<>();
    private double compareDistance = 0;
    private double longDistance = 0;
    private int indexX = 0, indexY = 0;
    private double mBearing = 0;

    private PolylineOverlay tPolyline = new PolylineOverlay();
    private ArrayList<Marker> tMarkerArr = new ArrayList<>();

    private LatLng mBoundsCenter;

    private ArrayList<LatLng> mPolygonLatArr = new ArrayList<>();
    private ArrayList<Marker> mPolygonMarkerArr = new ArrayList<>();
    private PolygonOverlay mPolygon = new PolygonOverlay();
    private ArrayList<LatLng> mSortPolygonArr = new ArrayList<>();

    private ArrayList<Marker> mBoundsArr = new ArrayList<>();
    private ArrayList<Marker> mBoundsMarkerArr = new ArrayList<>();
    private ArrayList<LatLng> mBoundsLatLngArr = new ArrayList<>();

    private ArrayList<LatLng> mBoundsLeftLatLngArr = new ArrayList<>();
    private ArrayList<LatLng> mBoundsRightLatLngArr = new ArrayList<>();

    Handler mainHandler;

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
        updateMapCadastralButton(); //지적도 버튼
        updateCameraMove(); //카메라 버튼
        setOverlayClear(); //전체 오버레이 삭제 버튼
        //setRecyclerView(); //리사이클러뷰

        setMission(); //임무
        setFlightWidth(); //비행폭
        setDistanceAtoB(); //비행거리

        //카메라
        String videoURL = "http://192.168.0.19:8081";
        WebView mWebView;
        mWebView = findViewById(R.id.webView);
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


    //--------------------Drone UI--------------------//

    //기체 연결 버튼, UI
    protected void onBtnConnectTap() {
        final Button btnConnect = (Button) findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (drone.isConnected()) {
                    drone.disconnect();
                    btnConnect.setText("Disconnect");
                    btnConnect.setBackgroundResource(R.drawable.btn_off);
                } else {
                    ConnectionParameter connectionParams = ConnectionParameter.newUdpConnection(null);
                    drone.connect(connectionParams);
                    btnConnect.setText("Connect");
                    btnConnect.setBackgroundResource(R.drawable.btn_on);
                }
            }
        });
    }

    //드론 모드 스피너
    protected void setModeSpinner() {
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
                btnMapSatellite.setBackgroundResource(R.drawable.btn_on);
                btnMapTerrain.setBackgroundResource(R.drawable.btn_off);
                btnMapBasic.setBackgroundResource(R.drawable.btn_off);
                btnMapStatic.setText("위성지도");
            }
        });

        btnMapTerrain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNaverMap.setMapType(NaverMap.MapType.Terrain);
                btnMapSatellite.setBackgroundResource(R.drawable.btn_off);
                btnMapTerrain.setBackgroundResource(R.drawable.btn_on);
                btnMapBasic.setBackgroundResource(R.drawable.btn_off);
                btnMapStatic.setText("지형도");
            }
        });

        btnMapBasic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNaverMap.setMapType(NaverMap.MapType.Basic);
                btnMapSatellite.setBackgroundResource(R.drawable.btn_off);
                btnMapTerrain.setBackgroundResource(R.drawable.btn_off);
                btnMapBasic.setBackgroundResource(R.drawable.btn_on);
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
                    btnCadastral.setBackgroundResource(R.drawable.btn_on);
                    btnCadastral.setText("지적도\non");
                    click = 0;
                } else {
                    mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                    btnCadastral.setBackgroundResource(R.drawable.btn_off);
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
                btnCameraMove.setBackgroundResource(R.drawable.btn_on);
                btnCameraUnMove.setBackgroundResource(R.drawable.btn_off);
                btnCamera.setText("맵 이동");
            }
        });

        btnCameraUnMove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(mDroneMarker.getPosition().latitude, mDroneMarker.getPosition().longitude)).animate(CameraAnimation.Easing, 500);
                mNaverMap.moveCamera(cameraUpdate);

                btnCameraMove.setBackgroundResource(R.drawable.btn_off);
                btnCameraUnMove.setBackgroundResource(R.drawable.btn_on);
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

        mDroneMarker.setPosition(new LatLng(droneGpsPosition.getLatitude(), droneGpsPosition.getLongitude()));
        mDroneMarker.setAngle((float) droneYaw);
        mDroneMarker.setAnchor(new PointF(0.5F, 0.9F));
        markerCustom(mDroneMarker);

        checkGuidedMode();
    }

    //비행 경로 그리기
    protected void setGpsPolyLine() {
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong droneGpsPosition = droneGps.getPosition();

        mLatLngArr.add(new LatLng(droneGpsPosition.getLatitude(), droneGpsPosition.getLongitude()));

        for (int i = 0; i < mLatLngArr.size(); i++) {
            mDronePolyline.setCoords(mLatLngArr);
        }

        mDronePolyline.setColor(Color.WHITE);
        mDronePolyline.setWidth(5);
        mDronePolyline.setMap(mNaverMap);
    }

//    //리사이클러뷰 추가
//    protected void setRecyclerView() {
//        RecyclerView recyclerView = findViewById(R.id.recyclerView);
//        recyclerView.setLayoutManager(new LinearLayoutManager(this));
//
//        SimpleTextAdapter adapter = new SimpleTextAdapter(list);
//        recyclerView.setAdapter(adapter);
//
//        recyclerView.smoothScrollToPosition(adapter.getItemCount());
//
//        //layoutManager.setReverseLayout(true); //역순 출력
//        //layoutManager.setStackFromEnd(true);
//    }

    //전체 오버레이 삭제 //수정 필요
    protected void setOverlayClear() {
        Button btnClear = (Button) findViewById(R.id.btnClear);

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLatLngArr.clear();
                mDronePolyline.setMap(null);
                mTargetMarker.setMap(null);

                for (int i = 0; i < mMissionMarkerArr.size(); i++) {
                    mMissionMarkerArr.get(i).setMap(null);
                }

                mMissionMarkerArr.clear();
                mMissionLatLngArr.clear();
                mMarkerA.setMap(null);
                mMarkerB.setMap(null);
                mABolyline.setMap(null);
                mCheck = 0;

                mMission.clear();

                for (int i = 0; i < mPolygonMarkerArr.size(); i++) {
                    mPolygonMarkerArr.get(i).setMap(null);

                }
                for (int i = 0; i < mBoundsMarkerArr.size(); i++) {
                    mBoundsMarkerArr.get(i).setMap(null);
                }

                for (int i = 0; i < mMissionMarkerArr.size(); i++) {
                    mMissionMarkerArr.get(i).setMap(null);
                }

                mPolygon.setMap(null);
                tPolyline.setMap(null);
                mAngleArr.clear();

                mPolygonMarkerArr.clear();
                mPolygonLatArr.clear();
                mSortPolygonArr.clear();

                mBoundsMarkerArr.clear();
                mBoundsLatLngArr.clear();

                mBoundsArr.clear();
            }
        });
    }

    //마커 커스텀
    private void markerCustom(Marker marker) {
        if (marker == mTargetMarker) {
            marker.setWidth(80);
            marker.setHeight(80);
            marker.setIcon(OverlayImage.fromResource(R.drawable.icon_long));
            marker.setMap(mNaverMap);
        } else if (marker == mDroneMarker) {
            marker.setWidth(280);
            marker.setHeight(280);
            marker.setIcon(OverlayImage.fromResource(R.drawable.icon_drone));
            marker.setMap(mNaverMap);
        } else if (marker == mMarkerA) {
            marker.setWidth(60);
            marker.setHeight(60);
            marker.setAnchor(new PointF(0.5F, 0.5F));
            marker.setIcon(OverlayImage.fromResource(R.drawable.icon_a));
            marker.setMap(mNaverMap);
        } else if (marker == mMarkerB) {
            marker.setWidth(60);
            marker.setHeight(60);
            marker.setAnchor(new PointF(0.5F, 0.5F));
            marker.setIcon(OverlayImage.fromResource(R.drawable.icon_b));
            marker.setMap(mNaverMap);
        } else {
            marker.setWidth(60);
            marker.setHeight(60);
            marker.setMap(mNaverMap);
        }
    }

    //LatLong 코드 변경
    protected LatLong toLatLong(LatLng latLong) {
        LatLong tl = new LatLong(latLong.latitude, latLong.longitude);
        return tl;
    }

    //LatLng 코드 변경
    protected LatLng toLatLng(LatLng latLng) {
        LatLng tl = new LatLng(latLng.latitude, latLng.longitude);
        return tl;
    }

    //--------------------Guided Mode--------------------//

    //가이드 모드 롱클릭
    protected void guidedModeLongClick() {
        mNaverMap.setOnMapLongClickListener(new NaverMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull @NotNull PointF pointF, @NonNull @NotNull LatLng latLng) {
                State vehicleState = drone.getAttribute(AttributeType.STATE);
                VehicleMode vehicleMode = vehicleState.getVehicleMode();

                mTargetMarker.setPosition(toLatLng(latLng));
                markerCustom(mTargetMarker);

                if (vehicleMode == VehicleMode.COPTER_GUIDED) {
                    guidedCheck = 1;
                    goToTarget();

                } else {
                    onGuidedModeClickHandler();
                }
            }
        });
    }

    //가이드 모드 목적지 이동 및 도착
    private void goToTarget() {
        ControlApi.getApi(drone).goTo(toLatLong(mTargetMarker.getPosition()), true, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("목적지로 이동합니다.");
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
    }

    //목적지 도착 확인
    private void checkGuidedMode() {
        State vehicleState = drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();

        if (vehicleMode == VehicleMode.COPTER_GUIDED && guidedCheck == 1) {
            if (CheckGoal(drone, toLatLng(mTargetMarker.getPosition()))) {
                alertUser("기체가 목적지에 도착했습니다.");
                updateLoiterMode();
                mTargetMarker.setMap(null);
                guidedCheck = 0;
            }
        }
    }

    //목적지 도착 체크
    public static boolean CheckGoal(final Drone drone, LatLng recentLatLng) {
        Gps droneGps = drone.getAttribute(AttributeType.GPS);
        LatLng droneGpsPosition = new LatLng(droneGps.getPosition().getLatitude(), droneGps.getPosition().getLongitude());
        return droneGpsPosition.distanceTo(recentLatLng) <= 1;
    }


    //--------------------Polyline Mission--------------------//

    //임무 버튼 UI
    protected void setMission() {
        final Button btnMission = (Button) findViewById(R.id.btnMissionStatic),
                btnAtoB = (Button) findViewById(R.id.btnAtoB),
                btnPolygon = (Button) findViewById(R.id.btnPolygon),
                btnCancel = (Button) findViewById(R.id.btnCancel),
                btnAtoBStatic = (Button) findViewById(R.id.btnAtoBStatic),
                btnRotationLeft = (Button) findViewById(R.id.btnRotationLeft),
                btnRotationRight = (Button) findViewById(R.id.btnRotationRight);

        btnMission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnAtoB.getVisibility() == v.INVISIBLE) {
                    btnAtoB.setVisibility(View.VISIBLE);
                    btnPolygon.setVisibility(View.VISIBLE);
                    btnCancel.setVisibility(View.VISIBLE);
                } else {
                    btnAtoB.setVisibility(View.INVISIBLE);
                    btnPolygon.setVisibility(View.INVISIBLE);
                    btnCancel.setVisibility(View.INVISIBLE);
                }
            }
        });

        btnAtoB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnMission.setText("AB");
                btnAtoB.setVisibility(View.INVISIBLE);
                btnPolygon.setVisibility(View.INVISIBLE);
                btnCancel.setVisibility(View.INVISIBLE);

                btnAtoBStatic.setVisibility(View.VISIBLE);
                mCheck = 1;

                updateBtnMission(mCheck);
            }
        });

        btnPolygon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnMission.setText("다각형");
                btnAtoB.setVisibility(View.INVISIBLE);
                btnPolygon.setVisibility(View.INVISIBLE);
                btnCancel.setVisibility(View.INVISIBLE);

                btnAtoBStatic.setVisibility(View.INVISIBLE);

                btnRotationLeft.setVisibility(View.VISIBLE);
                btnRotationRight.setVisibility(View.VISIBLE);

                setPolygonMarker();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnMission.setText("임무");
                btnAtoB.setVisibility(View.INVISIBLE);
                btnPolygon.setVisibility(View.INVISIBLE);
                btnCancel.setVisibility(View.INVISIBLE);

                btnAtoBStatic.setVisibility(View.INVISIBLE);

                btnRotationLeft.setVisibility(View.INVISIBLE);
                btnRotationRight.setVisibility(View.INVISIBLE);
            }
        });
    }

    //비행폭 버튼, UI
    protected void setFlightWidth() {
        final Button btnflightWidth = (Button) findViewById(R.id.btnflightWidth),
                btnWidthUp = (Button) findViewById(R.id.btnflightWidthUp),
                btnWidthDown = (Button) findViewById(R.id.btnflightWidthDown);

        btnflightWidth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnWidthUp.getVisibility() == v.INVISIBLE) {
                    btnWidthUp.setVisibility(View.VISIBLE);
                    btnWidthDown.setVisibility(View.VISIBLE);
                } else {
                    btnWidthUp.setVisibility(View.INVISIBLE);
                    btnWidthDown.setVisibility(View.INVISIBLE);
                }
            }
        });

        btnWidthUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (setFlightWidth < 10) {
                    setFlightWidth += 0.5;
                    textFlightWidth = setFlightWidth + "M\n비행폭";
                    btnflightWidth.setText(textFlightWidth);
                }
            }
        });

        btnWidthDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (setFlightWidth > 3) {
                    setFlightWidth -= 0.5;
                    textFlightWidth = setFlightWidth + "M\n비행폭";
                    btnflightWidth.setText(textFlightWidth);
                }
            }
        });
    }

    //A-B거리 UI
    protected void setDistanceAtoB() {
        final Button btnDistanceTo = (Button) findViewById(R.id.btnDistanceTo),
                btnDistanceUp = (Button) findViewById(R.id.btnDistanceToUp),
                btnDistanceDown = (Button) findViewById(R.id.btnDistanceToDown);

        btnDistanceTo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnDistanceUp.getVisibility() == v.INVISIBLE) {
                    btnDistanceUp.setVisibility(View.VISIBLE);
                    btnDistanceDown.setVisibility(View.VISIBLE);
                } else {
                    btnDistanceUp.setVisibility(View.INVISIBLE);
                    btnDistanceDown.setVisibility(View.INVISIBLE);
                }
            }
        });

        btnDistanceUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (setDistanceTo < 80) {
                    setDistanceTo += 10;
                    textDistanceTo = setDistanceTo + "M\nAB거리";
                    btnDistanceTo.setText(textDistanceTo);
                }
            }
        });

        btnDistanceDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (setDistanceTo > 30) {
                    setDistanceTo -= 10;
                    textDistanceTo = setDistanceTo + "M\nAB거리";
                    btnDistanceTo.setText(textDistanceTo);
                }
            }
        });
    }

    //임무설정 버튼 UI
    protected void updateBtnMission(int check) {
        final Button btnAtoBStatic = (Button) findViewById(R.id.btnAtoBStatic);

        switch (check) {
            case 0:
                btnAtoBStatic.setText("임무 설정");
                break;
            case 1:
                btnAtoBStatic.setText("A지점 설정");
                setAMarker();
                break;
            case 2:
                btnAtoBStatic.setText("B지점 설정");
                setBMarker();
                break;
            case 3:
                btnAtoBStatic.setText("임무 전송");
                sentMission();
                break;
            case 4:
                btnAtoBStatic.setText("임무 시작");
                updateMissionStart();
                break;
            case 5:
                btnAtoBStatic.setText("임무 중지");
                updateMissionPause();
                break;
        }
    }

    //A 마커 설정 (1)
    public void setAMarker() {
        final Button btnAtoBStatic = (Button) findViewById(R.id.btnAtoBStatic);

        mNaverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull @NotNull PointF pointF, @NonNull @NotNull LatLng latLng) {
                mMarkerA.setPosition(toLatLng(latLng));
                markerCustom(mMarkerA);
            }
        });

        btnAtoBStatic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCheck = 2;
                mMissionMarkerArr.add(new Marker(toLatLng(mMarkerA.getPosition())));
                updateBtnMission(mCheck);
            }
        });
    }

    //B 마커 설정 (2)
    public void setBMarker() {
        final Button btnAtoBStatic = (Button) findViewById(R.id.btnAtoBStatic);

        mNaverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull @NotNull PointF pointF, @NonNull @NotNull LatLng latLng) {
                mMarkerB.setPosition(toLatLng(latLng));
                markerCustom(mMarkerB);
            }
        });

        btnAtoBStatic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCheck = 3;
                mMissionMarkerArr.add(new Marker(toLatLng(mMarkerB.getPosition())));
                setDistance();
                updateBtnMission(mCheck);
            }
        });
    }

    //AB 지점 폴리라인
    protected void setDistance() {
        double tLine = MathUtils.getDistance2D(toLatLong(mMissionMarkerArr.get(0).getPosition()), toLatLong(mMissionMarkerArr.get(1).getPosition()));
        double distance = (int) (setDistanceTo / setFlightWidth * 2);

        if (distance % 4 == 1 || distance % 4 == 2) {
            if (distance % 4 == 1) {
                distance += 3;
            } else if (distance % 4 == 2) {
                distance += 2;
            }
        }

        for (int i = 2; i < distance; i++) {
            if (i % 4 == 0) {
                LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mMissionMarkerArr.get(i - 1).getPosition()), MathUtils.getHeadingFromCoordinates(toLatLong(mMissionMarkerArr.get(i - 2).getPosition()), toLatLong(mMissionMarkerArr.get(i - 1).getPosition())) - 90, setFlightWidth);
                mMissionMarkerArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
            } else if (i % 4 == 1) {
                LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mMissionMarkerArr.get(i - 1).getPosition()), MathUtils.getHeadingFromCoordinates(toLatLong(mMissionMarkerArr.get(i - 2).getPosition()), toLatLong(mMissionMarkerArr.get(i - 1).getPosition())) - 90, tLine);
                mMissionMarkerArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
            } else if (i % 4 == 2) {
                LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mMissionMarkerArr.get(i - 1).getPosition()), MathUtils.getHeadingFromCoordinates(toLatLong(mMissionMarkerArr.get(i - 2).getPosition()), toLatLong(mMissionMarkerArr.get(i - 1).getPosition())) + 90, setFlightWidth);
                mMissionMarkerArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
            } else if (i % 4 == 3) {
                LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mMissionMarkerArr.get(i - 1).getPosition()), MathUtils.getHeadingFromCoordinates(toLatLong(mMissionMarkerArr.get(i - 2).getPosition()), toLatLong(mMissionMarkerArr.get(i - 1).getPosition())) + 90, tLine);
                mMissionMarkerArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
            }
        }

        for (int i = 0; i < mMissionMarkerArr.size(); i++) {
            mMissionLatLngArr.add(mMissionMarkerArr.get(i).getPosition());
        }

        for (int i = 0; i < mMissionMarkerArr.size(); i++) {
            mABolyline.setCoords(mMissionLatLngArr);

            mABolyline.setColor(Color.YELLOW);
            mABolyline.setWidth(10);
            mABolyline.setCapType(PolylineOverlay.LineCap.Round);
            mABolyline.setMap(mNaverMap);
        }
    }

    //임무 전송 (3)
    protected void sentMission() {
        final Button btnAtoBStatic = (Button) findViewById(R.id.btnAtoBStatic);
        final Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);

        btnAtoBStatic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < mMissionLatLngArr.size(); i++) {
                    mWaypoint.setCoordinate(new LatLongAlt(mMissionLatLngArr.get(i).latitude, mMissionLatLngArr.get(i).longitude, droneAltitude.getAltitude()));
                    mWaypoint.setDelay(1);
                    mMission.addMissionItem(i, mWaypoint);

                    Log.d("test_point", i + " / " + mWaypoint);
                }

                MissionApi.getApi(drone).setMission(mMission, true);

                mCheck = 4;
                updateBtnMission(mCheck);
            }
        });
    }

    //임무 시작 (4)
    protected void updateMissionStart() {
        final Button btnAtoBStatic = (Button) findViewById(R.id.btnAtoBStatic);

        btnAtoBStatic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateAutoMode();

                MissionApi.getApi(drone).startMission(true, true, new AbstractCommandListener() {
                    @Override
                    public void onSuccess() {
                        alertUser("Mission Start...");
                    }

                    @Override
                    public void onError(int executionError) {
                        alertUser("Error to Mission Start." + executionError);
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("Timeout to Mission Start.");
                    }
                });

                mCheck = 5;
                updateBtnMission(mCheck);
            }
        });
    }

    //임무 중지 (5)
    protected void updateMissionPause() {
        final Button btnAtoBStatic = (Button) findViewById(R.id.btnAtoBStatic);

        btnAtoBStatic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MissionApi.getApi(drone).pauseMission(new AbstractCommandListener() {
                    @Override
                    public void onSuccess() {
                        alertUser("Mission Pause...");
                        updateLoiterMode();

                        mCheck = 4;
                        updateBtnMission(mCheck);
                    }

                    @Override
                    public void onError(int executionError) {
                        alertUser("Error to Mission Pause.");
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("Timeout to Mission Pause.");
                    }
                });
            }
        });
    }


    //--------------------Polygon Mission--------------------//

    //다각형 폴리곤 설정
    public void setPolygonMarker() {
        mNaverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull @NotNull PointF pointF, @NonNull @NotNull LatLng latLng) {
                mPolygonMarkerArr.add(new Marker(toLatLng(latLng)));
                mPolygonLatArr.add(toLatLng(latLng));

                for (int i = 0; i < mPolygonMarkerArr.size(); i++) {
                    markerCustom(mPolygonMarkerArr.get(i));
                    mPolygonMarkerArr.get(i).setMap(mNaverMap);
                }

                LatLngBounds bounds = new LatLngBounds.Builder().include(mPolygonLatArr).build();

                mBoundsCenter = bounds.getCenter();

                if (mPolygonLatArr.size() > 2) {
                    sortingPolygon();
                    setLongLength();
                    setBounds();
                    setBoundsRotation();
                    setPolygonLRLine(); //setBoundsLine();
                    setPolygonPoint();

                    mPolygon.setCoords(mSortPolygonArr);
                    mPolygon.setColor(0x9fffffff);
                    mPolygon.setMap(mNaverMap);
                }
            }
        });
    }

    //폴리곤 정렬
    protected void sortingPolygon() {
        mAngleArr.clear();
        mSortPolygonArr.clear();

        for (int i = 0; i < mPolygonLatArr.size(); i++) {
            mAngleArr.add(i, MathUtils.getHeadingFromCoordinates(toLatLong(mPolygonLatArr.get(i)), toLatLong(mBoundsCenter)));
            mSortPolygonArr.add(i, mPolygonLatArr.get(i));
        }

        for (int i = 0; i < mPolygonLatArr.size() - 1; i++) {
            for (int j = i + 1; j < mPolygonLatArr.size(); j++) {
                if (mAngleArr.get(i) > mAngleArr.get(j)) {
                    Collections.swap(mSortPolygonArr, i, j);
                    Collections.swap(mAngleArr, i, j);
                }
            }
        }
    }

    //가장 긴 변 길이
    protected void setLongLength() {
        longDistance = 0;
        indexX = 0;
        indexY = mSortPolygonArr.size() - 1;

        longDistance = MathUtils.getDistance2D(toLatLong(mSortPolygonArr.get(mSortPolygonArr.size() - 1)), toLatLong(mSortPolygonArr.get(0)));

        for (int i = 0; i < mSortPolygonArr.size() - 1; i++) {
            compareDistance = MathUtils.getDistance2D(toLatLong(mSortPolygonArr.get(i)), toLatLong(mSortPolygonArr.get(i + 1)));

            if (longDistance < compareDistance) {
                longDistance = compareDistance;
                indexX = i;
                indexY = i + 1;
            }
        }
    }

    //긴 변의 각으로 bounds 설정
    protected void setBounds() {
        mBoundsArr.clear();

        mBearing = MathUtils.getHeadingFromCoordinates(toLatLong(mSortPolygonArr.get(indexX)), toLatLong(mSortPolygonArr.get(indexY))) + 45;

        for (int i = 0; i < 4; i++) {
            LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mBoundsCenter), mBearing + (i * 90), longDistance * 1.5);
            mBoundsArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
        }
    }

    //폴리곤 Bounds 라인 설정
    protected void setBoundsLine() {
        mBoundsMarkerArr.clear();
        mBoundsLatLngArr.clear();
        tPolyline.setMap(null);

        double tLine = MathUtils.getDistance2D(toLatLong(mBoundsArr.get(2).getPosition()), toLatLong(mBoundsArr.get(3).getPosition()));
        double distance = (int) MathUtils.getDistance2D(toLatLong(mBoundsArr.get(1).getPosition()), toLatLong(mBoundsArr.get(2).getPosition())) / setFlightWidth * 2;

        mBoundsMarkerArr.add(new Marker(toLatLng(mBoundsArr.get(2).getPosition())));
        mBoundsMarkerArr.add(new Marker(toLatLng(mBoundsArr.get(3).getPosition())));

        for (int i = 2; i < distance; i++) {
            if (i % 4 == 0) {
                LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mBoundsMarkerArr.get(i - 1).getPosition()), MathUtils.getHeadingFromCoordinates(toLatLong(mBoundsMarkerArr.get(i - 2).getPosition()), toLatLong(mBoundsMarkerArr.get(i - 1).getPosition())) - 90, setFlightWidth);
                mBoundsMarkerArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
            } else if (i % 4 == 1) {
                LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mBoundsMarkerArr.get(i - 1).getPosition()), MathUtils.getHeadingFromCoordinates(toLatLong(mBoundsMarkerArr.get(i - 2).getPosition()), toLatLong(mBoundsMarkerArr.get(i - 1).getPosition())) - 90, tLine);
                mBoundsMarkerArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
            } else if (i % 4 == 2) {
                LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mBoundsMarkerArr.get(i - 1).getPosition()), MathUtils.getHeadingFromCoordinates(toLatLong(mBoundsMarkerArr.get(i - 2).getPosition()), toLatLong(mBoundsMarkerArr.get(i - 1).getPosition())) + 90, setFlightWidth);
                mBoundsMarkerArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
            } else if (i % 4 == 3) {
                LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mBoundsMarkerArr.get(i - 1).getPosition()), MathUtils.getHeadingFromCoordinates(toLatLong(mBoundsMarkerArr.get(i - 2).getPosition()), toLatLong(mBoundsMarkerArr.get(i - 1).getPosition())) + 90, tLine);
                mBoundsMarkerArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
            }
        }

        for (int i = 0; i < mBoundsMarkerArr.size(); i++) {
            mBoundsLatLngArr.add(mBoundsMarkerArr.get(i).getPosition());
        }

        for (int i = 0; i < mBoundsMarkerArr.size(); i++) {
            tPolyline.setCoords(mBoundsLatLngArr);

            tPolyline.setColor(Color.YELLOW);
            tPolyline.setWidth(10);
            tPolyline.setCapType(PolylineOverlay.LineCap.Round);
            tPolyline.setMap(mNaverMap);
        }

    }

    //폴리곤 bounds 왼, 오 따로 설정
    protected void setPolygonLRLine() {
        mBoundsMarkerArr.clear();
        mBoundsLatLngArr.clear();
        tPolyline.setMap(null);

        mBoundsLeftLatLngArr.clear();
        mBoundsRightLatLngArr.clear();
        tPolyline.setMap(null);

        double distance = (int) MathUtils.getDistance2D(toLatLong(mBoundsArr.get(1).getPosition()), toLatLong(mBoundsArr.get(2).getPosition())) / setFlightWidth;

        mBoundsLeftLatLngArr.add(toLatLng(mBoundsArr.get(2).getPosition()));
        mBoundsRightLatLngArr.add(toLatLng(mBoundsArr.get(3).getPosition()));

        for (int i = 1; i < distance; i++) {
            LatLong LeftLatLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mBoundsLeftLatLngArr.get(0)), mBearing + 45, setFlightWidth * i);
            mBoundsLeftLatLngArr.add(new LatLng(LeftLatLong.getLatitude(), LeftLatLong.getLongitude()));
            LatLong RightLatLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mBoundsRightLatLngArr.get(0)), mBearing + 45, setFlightWidth * i);
            mBoundsRightLatLngArr.add(new LatLng(RightLatLong.getLatitude(), RightLatLong.getLongitude()));
        }

        for (int i = 1; i < distance; i++) {
            if (i % 2 == 1) {
                mBoundsLatLngArr.add(mBoundsRightLatLngArr.get(i));
                mBoundsLatLngArr.add(mBoundsLeftLatLngArr.get(i));
            } else {
                mBoundsLatLngArr.add(mBoundsLeftLatLngArr.get(i));
                mBoundsLatLngArr.add(mBoundsRightLatLngArr.get(i));
            }
        }

        for (int i = 0; i < distance; i++) {
            tPolyline.setCoords(mBoundsLatLngArr);

            tPolyline.setColor(Color.YELLOW);
            tPolyline.setWidth(10);
            tPolyline.setCapType(PolylineOverlay.LineCap.Round);
            tPolyline.setMap(mNaverMap);
        }
    }

    //교점 구해서 array에 추가
    protected void setPolygonPoint() {
        for (int i = 0; i < mMissionMarkerArr.size(); i++) {
            mMissionMarkerArr.get(i).setMap(null);
        }
        mMissionMarkerArr.clear();
        mMissionLatLngArr.clear();

        for (int i = 0; i < mSortPolygonArr.size() - 1; i++) {
            for (int j = 0; j < mBoundsLeftLatLngArr.size(); j++) {
                IntersectionPoint(toLatLong(mSortPolygonArr.get(i)), toLatLong(mSortPolygonArr.get(i + 1)), toLatLong(mBoundsLeftLatLngArr.get(j)), toLatLong(mBoundsRightLatLngArr.get(j)));
            }
        }

        for (int j = 0; j < mBoundsLeftLatLngArr.size(); j++) {
            IntersectionPoint(toLatLong(mSortPolygonArr.get(0)), toLatLong(mSortPolygonArr.get(mSortPolygonArr.size() - 1)), toLatLong(mBoundsLeftLatLngArr.get(j)), toLatLong(mBoundsRightLatLngArr.get(j)));
        }

        for (int i = 0; i < mMissionMarkerArr.size(); i++) {
            markerCustom(mMissionMarkerArr.get(i));
            mMissionMarkerArr.get(i).setMap(mNaverMap);
        }
    }

    //교점 구하는 공식
    protected void IntersectionPoint(LatLong p1, LatLong p2, LatLong p3, LatLong p4) {
        double x1 = p1.getLatitude(), y1 = p1.getLongitude();
        double x2 = p2.getLatitude(), y2 = p2.getLongitude();
        double x3 = p3.getLatitude(), y3 = p3.getLongitude();
        double x4 = p4.getLatitude(), y4 = p4.getLongitude();

        double px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4));
        double py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4));
        double p = ((x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4));

        if (p != 0) {
            px = px / p;
            py = py / p;

            if (px <= x1 && px >= x2) {
                if (py >= y1 && py <= y2) {
                    LatLng point = new LatLng(px, py);
                    mMissionLatLngArr.add(toLatLng(point));
                    mMissionMarkerArr.add(new Marker(toLatLng(point)));
                } else if (py <= y1 && py >= y2) {
                    LatLng point = new LatLng(px, py);
                    mMissionLatLngArr.add(toLatLng(point));
                    mMissionMarkerArr.add(new Marker(toLatLng(point)));
                }
            } else if (px >= x1 && px <= x2) {
                if (py >= y1 && py <= y2) {
                    LatLng point = new LatLng(px, py);
                    mMissionLatLngArr.add(toLatLng(point));
                    mMissionMarkerArr.add(new Marker(toLatLng(point)));
                } else if (py <= y1 && py >= y2) {
                    LatLng point = new LatLng(px, py);
                    mMissionLatLngArr.add(toLatLng(point));
                    mMissionMarkerArr.add(new Marker(toLatLng(point)));
                }
            }
        }
    }

    //폴리곤 Bounds 회전
    protected void setBoundsRotation() {
        final Button btnRotationLeft = (Button) findViewById(R.id.btnRotationLeft);
        final Button btnRotationRight = (Button) findViewById(R.id.btnRotationRight);

        btnRotationLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBoundsArr.clear();
                mBearing -= 30;

                for (int i = 0; i < 4; i++) {
                    LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mBoundsCenter), mBearing + (i * 90), longDistance * 1.5);
                    mBoundsArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
                }
                setPolygonLRLine();
                setPolygonPoint();
            }
        });

        btnRotationRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBoundsArr.clear();
                mBearing += 30;

                for (int i = 0; i < 4; i++) {
                    LatLong latLong = MathUtils.newCoordFromBearingAndDistance(toLatLong(mBoundsCenter), mBearing + (i * 90), longDistance * 1.5);
                    mBoundsArr.add(new Marker(new LatLng(latLong.getLatitude(), latLong.getLongitude())));
                }
                setPolygonLRLine();
                setPolygonPoint();
            }
        });
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
                goToTarget();
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

    //--------------------drone Mode--------------------//

    //가이드 모드 전환
    public void updateGuidedMode() {
        VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Guided MODE...");
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

    //로이터 모드 전환
    public void updateLoiterMode() {
        VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("LOITER MODE...");
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

    //오토 모드 전환
    public void updateAutoMode() {
        VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_AUTO, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Auto MODE...");
            }

            @Override
            public void onError(int i) {
                alertUser("Error to Auto MODE.");
            }

            @Override
            public void onTimeout() {
                alertUser("Time Out to Auto MODE.");
            }
        });
    }


    //--------------------drone Event--------------------//

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

        list.add(String.format(" ★ " + message + "  "));
        //setRecyclerView();
    }

    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }
}