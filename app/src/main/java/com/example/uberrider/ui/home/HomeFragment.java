package com.example.uberrider.ui.home;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.uberrider.Callback.IFirebaseDriverInforListener;
import com.example.uberrider.Callback.IFirebaseFailedListener;
import com.example.uberrider.Common.Common;
import com.example.uberrider.Model.AnimationModel;
import com.example.uberrider.Model.DriverGeoModel;
import com.example.uberrider.Model.DriverInfoModel;
import com.example.uberrider.Model.GeoQueryModel;
import com.example.uberrider.R;
import com.example.uberrider.Remote.IGoogleAPI;
import com.example.uberrider.Remote.RetrofitClient;
import com.example.uberrider.databinding.FragmentHomeBinding;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.internal.operators.observable.ObservableFromIterable;
import io.reactivex.schedulers.Schedulers;

public class HomeFragment extends Fragment implements OnMapReadyCallback, IFirebaseFailedListener, IFirebaseDriverInforListener {

    private GoogleMap mMap;
    private SupportMapFragment mapFragment;
    private FragmentHomeBinding binding;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private Location previousLocation, currentLocation;

    private double distance = 1.0;
    private static final double LIMIT_RANGE = 10.0;
    private boolean firstTime = true;
    private String cityName;

    // load driver info
    IFirebaseDriverInforListener iFirebaseDriverInforListener;
    IFirebaseFailedListener iFirebaseFailedListener;

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private IGoogleAPI iGoogleAPI;


    // moving marker
    private List<LatLng> polylineList = new ArrayList<LatLng>();
    private Handler handler;
    private int index,next;
    private LatLng start,end;
    private  float v;
    private double lat, lng;

    @Override
    public void onStop() {
        compositeDisposable.clear();
        super.onStop();
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync((OnMapReadyCallback) this);
        init();
        return root;
    }

    @SuppressLint("MissingPermission")
    private void init() {

        iGoogleAPI = RetrofitClient.getInstance().create(IGoogleAPI.class);

        iFirebaseDriverInforListener = this;
        iFirebaseFailedListener = this;

        locationRequest = new LocationRequest();
        locationRequest.setSmallestDisplacement(10f);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                LatLng newPosition = new LatLng(locationResult.getLastLocation().getLatitude(),
                        locationResult.getLastLocation().getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPosition,18f));
                if (firstTime) {
                previousLocation = currentLocation = locationResult.getLastLocation();
                firstTime = false;
                }
                else {
                    previousLocation = currentLocation;
                    currentLocation = locationResult.getLastLocation();
                }
                if (previousLocation.distanceTo(currentLocation)/1000 <= LIMIT_RANGE)
                    loadAvailableDrivers();
                else {
                    //do nothing
                }
            }
        };
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getContext());
        fusedLocationProviderClient.requestLocationUpdates(locationRequest,locationCallback, Looper.myLooper());
        loadAvailableDrivers();
    }

    @SuppressLint("MissingPermission")
    private void loadAvailableDrivers() {

        fusedLocationProviderClient.getLastLocation()
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Snackbar.make(getView(),e.getMessage(),Snackbar.LENGTH_LONG).show();
                    }
                }).addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Load driver in city
                        Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
                        List<Address>addressList;
                        try {
                            addressList = geocoder.getFromLocation(location.getLatitude(),location.getLongitude(),1);
                            cityName = addressList.get(0).getLocality();
                            //Query
                            DatabaseReference driver_location_ref = FirebaseDatabase.getInstance()
                                    .getReference(Common.DRIVER_LOCATION_REFERENCES)
                                    .child(cityName);

                            GeoFire gf = new GeoFire(driver_location_ref);
                            GeoQuery geoQuery = gf.queryAtLocation(new GeoLocation(location.getLatitude(),location.getLongitude()),distance);
                            geoQuery.removeAllListeners();
                            geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                                @Override
                                public void onKeyEntered(String key, GeoLocation location) {
                                    Common.driversFound.add(new DriverGeoModel(key,location));
                                }

                                @Override
                                public void onKeyExited(String key) {

                                }

                                @Override
                                public void onKeyMoved(String key, GeoLocation location) {

                                }

                                @Override
                                public void onGeoQueryReady() {
                                    if (distance <= LIMIT_RANGE) {
                                        distance++;
                                        loadAvailableDrivers(); //Continue search
                                    }
                                    else {
                                        distance = 1.0;
                                        addDriverMarker();
                                    }

                                }

                                @Override
                                public void onGeoQueryError(DatabaseError error) {
                                    Snackbar.make(getView(),error.getMessage(),Snackbar.LENGTH_LONG).show();
                                }
                            });

                            driver_location_ref.addChildEventListener(new ChildEventListener() {
                                @Override
                                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                                    GeoQueryModel geoQueryModel = snapshot.getValue(GeoQueryModel.class);
                                   GeoLocation geoLocation = new GeoLocation(geoQueryModel.getL().get(0),geoQueryModel.getL().get(1));
                                   DriverGeoModel driverGeoModel = new DriverGeoModel(snapshot.getKey(), geoLocation);
                                   Location newDriverLocation = new Location("");
                                   newDriverLocation.setLatitude(geoLocation.latitude);
                                   newDriverLocation.setLongitude(geoLocation.longitude);
                                   float newDistance = location.distanceTo(newDriverLocation)/1000;

                                   if (newDistance <= LIMIT_RANGE)
                                       findDriverByKey(driverGeoModel);
                                }

                                @Override
                                public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                                }

                                @Override
                                public void onChildRemoved(@NonNull DataSnapshot snapshot) {

                                }

                                @Override
                                public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {

                                }
                            });

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                });
    }

    private void addDriverMarker() {

        if (Common.driversFound.size()>0) {
         Observable.fromIterable(Common.driversFound)
                 .subscribeOn(Schedulers.newThread())
                 .observeOn(AndroidSchedulers.mainThread())
                 .subscribe(driverGeoModel -> {
                     //on next
                     findDriverByKey(driverGeoModel);
                 },throwable -> {
                     Snackbar.make(getView(),throwable.getMessage(),Snackbar.LENGTH_LONG).show();
                 },()-> {

                 } );
        }
        else {
            Snackbar.make(getView(),"Drivers not found ",Snackbar.LENGTH_LONG).show();
        }

    }

    public void findDriverByKey(DriverGeoModel driverGeoModel){

        FirebaseDatabase.getInstance()
                .getReference(Common.DRIVER_INFOR_REFERENCE)
                .child(driverGeoModel.getKey())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.hasChildren()) {
                            driverGeoModel.setDriverInfoModel(snapshot.getValue(DriverInfoModel.class));
                            iFirebaseDriverInforListener.onDriverInfoSuccess(driverGeoModel);
                        }
                        else {
                        iFirebaseFailedListener.onFirebaseLoadFailed("Driver key not found" + driverGeoModel.getKey());
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        iFirebaseFailedListener.onFirebaseLoadFailed(error.getMessage());


                    }
                });
    }

    @Override
    public void onDestroyView() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        mMap = googleMap;

        //check permission

        Dexter.withContext(getContext())
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                    mMap.setMyLocationEnabled(true);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);
                        mMap.setOnMyLocationButtonClickListener(() -> {
                            fusedLocationProviderClient.getLastLocation()
                                    .addOnFailureListener(e -> Toast.makeText(getContext(), ""+ e.getMessage(), Toast.LENGTH_SHORT).show())
                                    .addOnSuccessListener(location -> {
                                        LatLng userLatLng = new LatLng(location.getLatitude(),location.getLongitude());
                                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng,18f));
                                    });
                            return true;
                        });

                        // Set layout button
                        View locationButton = ((View)mapFragment.getView().findViewById(Integer.parseInt("1"))
                                .getParent())
                                .findViewById(Integer.parseInt("2"));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
                        params.addRule(RelativeLayout.ALIGN_PARENT_TOP,0);
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                        params.setMargins(0,0,0,250);

                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        Snackbar.make(getView(),permissionDeniedResponse.getPermissionName() + "need enable", Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {

                    }
                }).check();

        mMap.getUiSettings().setZoomControlsEnabled(true);

        try {
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(),R.raw.uber_maps_style));
            if (!success)
                Snackbar.make(getView(),"Load map style failed", Snackbar.LENGTH_LONG).show();
        } catch (Resources.NotFoundException e) {
            Snackbar.make(getView(),e.getMessage(), Snackbar.LENGTH_LONG).show();

        }


    };

    @Override
    public void onFirebaseLoadFailed(String message) {
        Snackbar.make(getView(),message,Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDriverInfoSuccess(DriverGeoModel driverGeoModel) {
        //If key already has marker don't set up again

        if (!Common.markerList.containsKey(driverGeoModel.getKey()))
            Common.markerList.put(driverGeoModel.getKey(),
        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(driverGeoModel.getGeoLocation().latitude
                ,driverGeoModel.getGeoLocation().longitude))
                .flat(true)
                .title(Common.buildName(driverGeoModel.getDriverInfoModel().getFirstName()
                        ,driverGeoModel.getDriverInfoModel().getLastName()))
                        .snippet(driverGeoModel.getDriverInfoModel().getPhoneNumber())
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))));

        if (!TextUtils.isEmpty(cityName)) {
            DatabaseReference driverLocation = FirebaseDatabase.getInstance()
                    .getReference(Common.DRIVER_LOCATION_REFERENCES)
                    .child(cityName)
                    .child(driverGeoModel.getKey());

            driverLocation.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.hasChildren()) {
                        if (Common.markerList.get(driverGeoModel.getKey())!=null)
                            Common.markerList.get(driverGeoModel.getKey()).remove();
                        Common.markerList.remove(driverGeoModel.getKey());
                        Common.driverLocationSubscribe.remove(driverGeoModel.getKey()); //remove driver information
                        driverLocation.removeEventListener(this);
                    }
                    else {
                        if (Common.markerList.get(driverGeoModel.getKey())!=null) {
                            GeoQueryModel geoQueryModel = snapshot.getValue(GeoQueryModel.class);
                            AnimationModel animationModel = new AnimationModel(false,geoQueryModel);
                            if (Common.driverLocationSubscribe.get(driverGeoModel.getKey()) != null)  {
                            Marker currentMarker = Common.markerList.get(driverGeoModel.getKey());
                            AnimationModel oldPosition = Common.driverLocationSubscribe.get(driverGeoModel.getKey());

                            String from = new StringBuilder()
                                    .append(oldPosition.getGeoQueryModel().getL().get(0))
                                    .append(",")
                                    .append(oldPosition.getGeoQueryModel().getL().get(1))
                                    .toString();

                                String to = new StringBuilder()
                                        .append(animationModel.getGeoQueryModel().getL().get(0))
                                        .append(",")
                                        .append(animationModel.getGeoQueryModel().getL().get(1))
                                        .toString();
                                moveMarkerAnimation(driverGeoModel.getKey(),animationModel,currentMarker,from,to);
                            }

                            else {
                                // first location init
                                Common.driverLocationSubscribe.put(driverGeoModel.getKey(),animationModel);
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                Snackbar.make(getView(),error.getMessage(),Snackbar.LENGTH_LONG).show();
                }
            });
        }
    }

    private void moveMarkerAnimation(String key, AnimationModel animationModel, Marker currentMarker, String from, String to) {
        if (!animationModel.isRun()) {
            //Request API
            compositeDisposable.add(iGoogleAPI.getDirections("driving",
                    "less_driving",
                    from,to,
                    getString(R.string.google_api_key_map))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(returnResult -> {
                        Log.d("API_RETURN", returnResult);

                        try {
                            //parse json
                            JSONObject jsonObject = new JSONObject(returnResult);
                            JSONArray jsonArray = jsonObject.getJSONArray("routes");

                            for (int i = 0; i< jsonArray.length(); i++)  {
                                JSONObject route = jsonArray.getJSONObject(i);
                                JSONObject poly = route.getJSONObject("overview_polyline");
                                String polyline = poly.getString("points");
                                polylineList = Common.decodePoly(polyline);
                            }

                            handler = new Handler();
                            index = -1;
                            next = 1;

                            Runnable runnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (polylineList.size() > 1)  {
                                      if(index < polylineList.size() - 2)  {
                                          index ++;
                                          next = index + 1;
                                          start = polylineList.get(index);
                                          end = polylineList.get(next);
                                      }
                                    }
                                    ValueAnimator valueAnimator = ValueAnimator.ofInt(0,1);
                                    valueAnimator.setDuration(3000);
                                    valueAnimator.setInterpolator(new LinearInterpolator());
                                    valueAnimator.addUpdateListener(value -> {
                                        v = value.getAnimatedFraction();
                                            lat = v * end.latitude + (1 - v) * start.latitude;
                                            lng = v * end.longitude + (1 - v) * start.longitude;
                                            LatLng newPos = new LatLng(lat, lng);
                                            currentMarker.setPosition(newPos);
                                            currentMarker.setAnchor(0.5f, 0.5f);
                                            currentMarker.setRotation(Common.getBearing(start, newPos));
                                    });
                                    valueAnimator.start();
                                    if (index < polylineList.size() -2 ) //reach destination
                                        handler.postDelayed(this,1500);
                                    else if (index <polylineList.size() -1) //done
                                    {
                                        animationModel.setRun(false);
                                        Common.driverLocationSubscribe.put(key,animationModel);
                                    }
                                }
                            };
                            // run handler
                            handler.postDelayed(runnable,1500);
                        }
                        catch (Exception e) {
                            Snackbar.make(getView(),e.getMessage(),Snackbar.LENGTH_LONG).show();
                        }
                    }));
        }
    }
}