package com.ryanwhitell.royalbiketaxi.main.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ryanwhitell.royalbiketaxi.R;
import com.ryanwhitell.royalbiketaxi.main.services.DriverService;

import java.util.Map;

public class DriverActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    //******* VARIABLES *******//
    // Debugging
    private final String DEBUG_SIGN_IN = "SignIn";
    private final String DEBUG_ACTIVITY_LC = "Lifecycle";
    private final String DEBUG_DISPATCH_REQUEST = "DispatchRequest";
    private final String DEBUG_ON_CANCEL = "Cancelled";

    // Alerts
    private AlertDialog.Builder mIncomingDispatchAlert;
    private AlertDialog mIncomingDispatchAlertInstance;
    private Toast mToast;

    // Driver Information
    private String mName;
    private String mNumber;
    private Location mLastKnownLocation;

    // Firebase
    private DatabaseReference mFirebaseAvailableDrivers;
    private DatabaseReference mFirebaseLocationRequest;
    private DatabaseReference mFirebaseUserDispatchRequest;
    private String mDispatchRequestKey;
    private ValueEventListener mLocationRequestListener;
    private ValueEventListener mMyDispatchRequestListener;
    private ValueEventListener mTrackUserListener;
    private ValueEventListener mUserConnectionListener;

    //Flow Control
    private enum State {
        UNAVAILABLE, AVAILABLE, ON_DISPATCH
    }
    private State mDispatchState;
    private String mAction;

    // Google Api - Location, Map
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Marker mLocationMarker;
    private Marker mUserLocationMarker;

    //Navigation
    private Button mEndButton;
    private FloatingActionButton mRefreshFab;
    private MenuItem mLoginButton;
    private MenuItem mLogoutButton;


    /******* ACTIVITY LIFECYCLE *******/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);

        // onCreate()
        Log.d(DEBUG_ACTIVITY_LC,"onCreate()");

        // DEBUGGING keep screen alive
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        //******* Initialize Alerts *******//
        mIncomingDispatchAlert = new AlertDialog.Builder(this)
                .setTitle("Accept Incoming Dispatch")
                .setMessage(
                        "You have 10 seconds to accept the incoming " +
                                "dispatch. Please ACCEPT or DECLINE.")
                .setPositiveButton("ACCEPT", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        incomingDispatchAlert(which);
                    }
                })
                .setNegativeButton("DECLINE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        incomingDispatchAlert(which);
                    }
                });


        //******** Initialize Driver Information *******//
        mDispatchState = State.AVAILABLE;

        mName = DriverService.sDriverName;
        mNumber = DriverService.sDriverNumber;


        //******* Initialize Firebase *******//
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        mFirebaseAvailableDrivers = database.getReference("Available Drivers");
        mFirebaseUserDispatchRequest = database.getReference("Dispatch Request");
        mFirebaseLocationRequest = database.getReference("Location Request");


        //******* Initialize Google Api - Location, Map *******//
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


        //******* Initialize Navigation *******//
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        assert toolbar != null;
        toolbar.setTitle("You are logged in as " + mName);
        setSupportActionBar(toolbar);

        mRefreshFab = (FloatingActionButton) findViewById(R.id.fab_request_dispatch);
        assert mRefreshFab != null;
        mRefreshFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showToast("Refreshing driver locations");
                requestDriverLocations();
            }
        });

        mEndButton = (Button) findViewById(R.id.end_button);
        assert mEndButton != null;
        mEndButton.setVisibility(View.GONE);

    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = getIntent();

        if (intent.getAction() != null) {
            mAction = intent.getAction();
        } else {
            mAction = "";
        }


        if (DriverService.sIsActive) {
            Intent startForegroundIntent = new Intent(DriverActivity.this, DriverService.class);
            startForegroundIntent.setAction("Stop");
            startService(startForegroundIntent);
        }

        // onStart()
        Log.d(DEBUG_ACTIVITY_LC, "onStart()");

        // 1. Add value event listener for "Dispatch Request"
        Log.d(DEBUG_SIGN_IN, "1. Add value event listener for \"Dispatch Request\"");
        mMyDispatchRequestListener = mFirebaseAvailableDrivers.child(mName).child("Dispatch Request").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (mDispatchState == State.AVAILABLE) {
                    if (dataSnapshot.getValue() != null) {
                        if (dataSnapshot.getValue().equals("Connected")) {
                            // 3. User has confirmed and connected
                            Log.d(DEBUG_DISPATCH_REQUEST, "3. User has confirmed and connected");
                            connectedToUser();

                        } else if (!mAction.equals("Connect to User")){
                            // 1. Incoming dispatch request
                            Log.d(DEBUG_DISPATCH_REQUEST, "1. Incoming dispatch request");
                            mDispatchRequestKey = dataSnapshot.getValue().toString();
                            mIncomingDispatchAlertInstance = mIncomingDispatchAlert.create();
                            mIncomingDispatchAlertInstance.show();
                        }
                    } else {
                        if (mIncomingDispatchAlertInstance != null) {
                            mIncomingDispatchAlertInstance.dismiss();
                            updateMyMarker(mLastKnownLocation);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                showToast("Dispatch cancelled. There was a database error!");
                // 0. Dispatch cancelled from database error 1
                Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from database error 1");
                disconnectFromUser();
            }
        });

        // 2. Add value event listener for "Location Request"
        Log.d(DEBUG_SIGN_IN, "2. Add value event listener for \"Location Request\"");
        mLocationRequestListener = mFirebaseLocationRequest.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (mDispatchState == State.AVAILABLE) {
                    // 4. Driver location request received
                    Log.d(DEBUG_SIGN_IN, "4. Driver location request received");
                    driverLocationRequestReceived();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                showToast("Could not handle location request. There was a database error!");
            }
        });

        // 3. Set phoneNumber in database
        Log.d(DEBUG_SIGN_IN, "3. Set phoneNumber in database");
        mFirebaseAvailableDrivers.child(mName).child("phoneNumber").setValue(mNumber);

        if (mAction.equals("Connect to User")) {
            mDispatchRequestKey = DriverService.sDispatchRequestKey;
            mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").setValue(mName);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // onStop()
        Log.d(DEBUG_ACTIVITY_LC, "onStop()");

        // Remove listeners
        mFirebaseAvailableDrivers.child(mName).child("Dispatch Request").removeEventListener(mMyDispatchRequestListener);
        mFirebaseLocationRequest.removeEventListener(mLocationRequestListener);

        // 0. Dispatch cancelled from onStop()
        Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from onStop()");
        disconnectFromUser();

        if ((!DriverService.sIsActive) && (mDispatchState == State.AVAILABLE)) {
            Intent startForegroundIntent = new Intent(DriverActivity.this, DriverService.class);
            startForegroundIntent.setAction("Start");
            startService(startForegroundIntent);
        }

        mGoogleApiClient.disconnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.driver, menu);

        mLogoutButton = menu.findItem(R.id.action_logout);
        mLoginButton = menu.findItem(R.id.action_login);

        mLogoutButton.setVisible(false);
        mLoginButton.setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_logout) {
            if (mDispatchState == State.AVAILABLE) {
                showToast("You are now offline");
                mDispatchState = State.UNAVAILABLE;
                mLoginButton.setVisible(true);
                mLogoutButton.setVisible(false);
                mFirebaseAvailableDrivers.child(mName).removeValue();
                return true;
            }
        } else if (id == R.id.action_login) {
            showToast("You are now online");
            mDispatchState = State.AVAILABLE;
            mLoginButton.setVisible(false);
            mLogoutButton.setVisible(true);
            driverLocationRequestReceived();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    //******* Alerts *******//
    public void showToast(String message) {
        if ((mToast != null) && (mToast.getView().getWindowVisibility() == View.VISIBLE)) {
            mToast.cancel();
            mToast = Toast.makeText(getApplicationContext(),
                    message, Toast.LENGTH_LONG);
            mToast.show();
        } else {
            mToast = Toast.makeText(getApplicationContext(),
                    message, Toast.LENGTH_LONG);
            mToast.show();
        }
    }


    //******* DRIVER LOGIC *******//
    // Updating Driver Map
    public void requestDriverLocations() {
        String key = mFirebaseLocationRequest.push().getKey();
        mFirebaseLocationRequest.child(key).setValue("REQUEST");
        mFirebaseLocationRequest.child(key).removeValue();
    }

    public void driverLocationRequestReceived() {
        // 5. Connect to API client
        Log.d(DEBUG_SIGN_IN, "5. Connect to API client");

        mGoogleApiClient.connect();

        if ((mLastKnownLocation != null) && (mDispatchState == State.AVAILABLE)) {
            mFirebaseAvailableDrivers.child(mName).child("latitude").setValue(mLastKnownLocation.getLatitude());
            mFirebaseAvailableDrivers.child(mName).child("longitude").setValue(mLastKnownLocation.getLongitude());
            mFirebaseAvailableDrivers.child(mName).child("phoneNumber").setValue(mNumber);
        }

        // 6. Update the map
        Log.d(DEBUG_SIGN_IN, "6. Update the map");
        updateMap();
    }

    public void updateMap(){
        mFirebaseAvailableDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    Map<String, Object> driverLocations = (Map<String, Object>) dataSnapshot.getValue();

                    // 7. Clear the current map and update it
                    Log.d(DEBUG_SIGN_IN, "7. Clear the current map and update it");
                    mMap.clear();

                    for (Map.Entry<String, Object> driver : driverLocations.entrySet()) {

                        if (!driver.getKey().equals(mName)) {
                            mMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(Double.parseDouble(((Map<String, Object>) driver.getValue()).get("latitude").toString()),
                                            Double.parseDouble(((Map<String, Object>) driver.getValue()).get("longitude").toString())))
                                    .title(driver.getKey())
                                    .snippet(((Map<String, Object>) driver.getValue()).get("phoneNumber").toString())
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                showToast("Could not update map. There was a database error!");
            }
        });
    }

    // Dispatch Logic
    public void incomingDispatchAlert(int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // 2. Dispatch request accepted
            Log.d(DEBUG_DISPATCH_REQUEST, "2. Dispatch request accepted");
            mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").setValue(mName);
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            mFirebaseAvailableDrivers.child(mName).child("Dispatch Request").removeValue();
        }
    }

    public void connectedToUser() {

        // Reset mAction
        mAction = "";

        // 4. Clear map
        Log.d(DEBUG_DISPATCH_REQUEST, "4. Clear map");
        mMap.clear();

        // 5. Hide fab and show end ride button, prevent device sleep
        Log.d(DEBUG_DISPATCH_REQUEST, "5. Hide fab and show end ride button, prevent device sleep");
        mEndButton.setVisibility(View.VISIBLE);
        mRefreshFab.setVisibility(View.GONE);
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 6. Remove self from "Available Drivers," update information, set state to "On Dispatch"
        Log.d(DEBUG_DISPATCH_REQUEST, "6. Remove self from \"Available Drivers,\" update information, set state to \"On Dispatch\"");
        mFirebaseAvailableDrivers.child(mName).removeValue();
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver").child("name").setValue(DriverService.sDriverName);
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver").child("phoneNumber").setValue(DriverService.sDriverNumber);
        mDispatchState = State.ON_DISPATCH;
        mGoogleApiClient.connect();

        // 7. Initialize track user location listener
        Log.d(DEBUG_DISPATCH_REQUEST, "7. Initialize track user location listener");
        mTrackUserListener = mFirebaseUserDispatchRequest.child(mDispatchRequestKey).addValueEventListener(new ValueEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {

                    Map<String, Object> userInfo = (Map<String, Object>) dataSnapshot.getValue();


                    if (mUserLocationMarker != null) {
                        if ((mUserLocationMarker.getPosition().latitude != (Double) userInfo.get("latitude")) ||
                                (mUserLocationMarker.getPosition().longitude != (Double) userInfo.get("longitude"))) {
                            mUserLocationMarker.remove();

                            // User location changed
                            Log.d(DEBUG_DISPATCH_REQUEST, "User location changed");
                            mUserLocationMarker = mMap.addMarker(new MarkerOptions()
                                    .position(new LatLng((Double) userInfo.get("latitude"), (Double) userInfo.get("longitude")))
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                        }

                    } else {
                        // User location initialized
                        Log.d(DEBUG_DISPATCH_REQUEST, "User location initialized");
                        mUserLocationMarker = mMap.addMarker(new MarkerOptions()
                                .position(new LatLng((Double) userInfo.get("latitude"), (Double) userInfo.get("longitude")))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                    }

                } else {
                    showToast("Dispatch cancelled!");
                    // 0. Dispatch cancelled from deletion of User Dispatch Request node
                    Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from deletion of User Dispatch Request node");
                    disconnectFromUser();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                showToast("Dispatch cancelled. There was a database error!");
                // 0. Dispatch cancelled from database error 2
                Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from database error 2");
                disconnectFromUser();
            }
        });

        mUserConnectionListener = mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    if (dataSnapshot.getValue().equals("User Cancelled")) {
                        // 0. Dispatch cancelled from user
                        Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from user");
                        disconnectFromUser();
                    }
                } else {
                    // 0. Dispatch cancelled from deletion of User Dispatch Request "Connected" node
                    Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from deletion of User Dispatch Request \"Connected\" node");
                    disconnectFromUser();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                showToast("Dispatch cancelled. There was a database error!");
                // 0. Dispatch cancelled from database error 3
                Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from database error 3");
                disconnectFromUser();
            }
        });
    }

    public void disconnectFromUser() {
        if (mDispatchState == State.ON_DISPATCH) {

            // 2. Change state to "Available," show fab, hide end button, allow device sleep
            Log.d(DEBUG_ON_CANCEL, "2. Change state to \"Available,\" show fab, hide end button");
            mDispatchState = State.AVAILABLE;
            mEndButton.setVisibility(View.GONE);
            mRefreshFab.setVisibility(View.VISIBLE);
            // getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // 3. Remove event listeners, clear the map, request driver locations
            Log.d(DEBUG_ON_CANCEL, "3. Remove event listeners, clear the map, request driver locations");
            if (mTrackUserListener != null) {
                mFirebaseUserDispatchRequest.child(mDispatchRequestKey).removeEventListener(mTrackUserListener);

            }
            if (mFirebaseAvailableDrivers != null) {
                mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").removeEventListener(mUserConnectionListener);

            }
            mMap.clear();
            requestDriverLocations();
        }
    }

    public void onEndButtonClick(View view) {
        showToast("Dispatch ended");

        // 1. Notify user that the driver has ended the ride or cancelled
        Log.d(DEBUG_ON_CANCEL, "1. Notify user that the driver has ended the ride or cancelled");
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").setValue("Driver Cancelled");

        // 0. Dispatch cancelled by driver on button click
        Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled by driver on button click");
        disconnectFromUser();
    }


    //******* GOOGLE MAP *******//
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                marker.showInfoWindow();
                CameraPosition cp = new CameraPosition(marker.getPosition(), 14.9f, 0, 17.5f);
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cp), 500, null);
                return true;
            }
        });

        LatLng savannah = new LatLng(32.072219, -81.0933537);
        CameraPosition cp = new CameraPosition(savannah, 14.9f, 0, 17.5f);
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cp));
    }


    //******* LOCATION SERVICES *******//
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest locationRequest = LocationRequest.create();
        //TODO: look into HIGH ACCURACY vs BATTER and DATA saver
        // Consider, to safe driver data and battery, letting him choose the interval
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(2500);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling ActivityCompat#requestPermissions here to request the missing permissions
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
        LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }

    @Override
    public void onConnectionSuspended(int i) {
        //TODO: Handle Suspended Connection
    }

    @Override
    public void onLocationChanged(Location location) {

        mLastKnownLocation = location;

        // 8. Update my location on map and database
        Log.d(DEBUG_SIGN_IN, "8. Update my location on map and database");
        updateMyMarker(location);

        if (mDispatchState == State.AVAILABLE) {
            // 9a. Disconnect from the Api
            Log.d(DEBUG_SIGN_IN, "9a. Disconnect from the Api ");
            mGoogleApiClient.disconnect();
            mLogoutButton.setVisible(true);
        } else if (mDispatchState == State.ON_DISPATCH) {
            trackDriverLocation(location);
        } else {
            // 9b. Disconnect from the Api
            Log.d(DEBUG_SIGN_IN, "9b. Disconnect from the Api ");
            mGoogleApiClient.disconnect();
        }

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle Failed Connection
    }

    public void trackDriverLocation(Location location) {
        // Track driver....
        Log.d(DEBUG_DISPATCH_REQUEST, "Track driver....");
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver")
                .child("latitude")
                .setValue(location.getLatitude());
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver")
                .child("longitude")
                .setValue(location.getLongitude());

        updateMyMarker(location);
    }

    public void updateMyMarker(Location location) {

        if (mLocationMarker != null) {
            mLocationMarker.remove();
        }

        mLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .title("ME")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        if (mDispatchState == State.AVAILABLE) {
            mFirebaseAvailableDrivers.child(mName).child("latitude").setValue(location.getLatitude());
            mFirebaseAvailableDrivers.child(mName).child("longitude").setValue(location.getLongitude());
        }
    }

}
