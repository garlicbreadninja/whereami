package com.example.anurag.whereami;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Handler;
import android.os.strictmode.WebViewMethodCalledOnWrongThreadViolation;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconSize;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PermissionsListener, OnMapReadyCallback{
    private MapView mapView;
    private MapboxMap mapboxMap;
    private PermissionsManager permissionsManager;
    private FirebaseAuth firebaseAuth;
    private Style styleLoaded;
    Context mainContext = this;
    double dock_latitude = 0.0;
    double dock_longitude =0.0;
    public static boolean logSuccess = true;
    private final static String TAG = "MainActivityRealtime";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        Mapbox.getInstance(this, "api_key_here");
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }
    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {
                MainActivity.this.mapboxMap = mapboxMap;
                mapboxMap.setStyle(new Style.Builder().fromUrl("mapbox://styles/anrgakla/cjkxglohw0dvy2so94sefcksm"), new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        enableLocationMarker(style);
                        styleLoaded = style;
                    }
                });
    }

    @SuppressWarnings( {"MissingPermission"})
    public void enableLocationMarker(@NonNull Style style){
            if((ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) == PackageManager.PERMISSION_GRANTED){
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "CHANNEL_ID")
                        .setSmallIcon(R.drawable.ic_perm)
                        .setContentTitle("Gotcha")
                        .setContentText("Permissions Obtained. Locating Now.")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText("Permissions Obtained. Locating Now."))
                        .setPriority(Notification.PRIORITY_HIGH);
                LocationComponent locationComponent = mapboxMap.getLocationComponent();
                locationComponent.activateLocationComponent(this, style);
                locationComponent.setLocationComponentEnabled(true);
                locationComponent.setRenderMode(RenderMode.COMPASS);
                locationComponent.setCameraMode(CameraMode.TRACKING);
                loginToFireBase();
                addFireBaseSymbolayer(style);
            }
            else {
                permissionsManager = new PermissionsManager(this);
                permissionsManager.requestLocationPermissions(this);
            }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    private void markDatabaseCoordinates(){
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference ref = database.getReference("location");

// Attach a listener to read the data at our posts reference
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                dock_latitude =(double) dataSnapshot.child("latitude").getValue();
                dock_longitude = (double) dataSnapshot.child("longitude").getValue();
                LatLng dock_location = new LatLng(dock_latitude, dock_longitude);
                addMarkerToLayer(dock_location);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.out.println("The read failed: " + databaseError.getCode());
            }
        });

    }
    private void loginToFireBase(){
        firebaseAuth = FirebaseAuth.getInstance();
        String email = "publicusr@mail.com" ;
        String password = "speakwater";
        firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                logSuccess = true;
                if(task.isSuccessful()){
                    markDatabaseCoordinates();
                }
                else{
                    Log.d(TAG, "Firebase Auth Failed");
                    logSuccess = false;
                }
            }
        });
        if(logSuccess){
            Toast.makeText(this, "Connected to Firebase", Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(this, "Firebase authentication failed", Toast.LENGTH_SHORT).show();
        }
    }
    private void addFireBaseSymbolayer(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addImage("firebase-marker",
                BitmapFactory.decodeResource(this.getResources(), R.drawable.mapbox_marker_icon_default));
        GeoJsonSource geoJsonSource = new GeoJsonSource("firebase-coordinates");
        loadedMapStyle.addSource(geoJsonSource);
        SymbolLayer firebaseSymbolayer = new SymbolLayer("firebase-coordinates-layer", "firebase-coordinates");
        firebaseSymbolayer.withProperties(
                iconImage("firebase-marker"),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
        );
        loadedMapStyle.addLayer(firebaseSymbolayer);
    }
    private void addMarkerToLayer(@NonNull LatLng location){
        Point firebaseCoords = Point.fromLngLat(location.getLongitude(), location.getLatitude());
        GeoJsonSource source = mapboxMap.getStyle().getSourceAs("firebase-coordinates");
        if(source != null){
            source.setGeoJson(Feature.fromGeometry(firebaseCoords));
        }
    }
    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, "tap on allow to let the app do its thing", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    enableLocationMarker(style);
                }
            });
        } else {
            Toast.makeText(this, "tap on allow to let the app do its thing", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    @SuppressWarnings( {"MissingPermission"})
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}

