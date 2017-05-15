package com.ryanwhitell.royalbiketaxi.main.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ProgressBar;
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
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ryanwhitell.royalbiketaxi.main.model.DriverLocation;
import com.ryanwhitell.royalbiketaxi.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import android.os.Handler;

public class UserActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    //******* VARIABLES *******//
    // Debugging
    private final String DEBUG_REQUEST_DISPATCH = "RequestDispatch";
    private final String DEBUG_DRIVER_LOCATIONS = "RequestLocations";
    private final String DEBUG_ON_CONNECTED = "ConnectedToDispatch";
    private final String DEBUG_ON_CANCEL = "Cancelled";
    private final String DEBUG_ACTIVITY_LC = "Lifecycle";

    // Alerts
    private ProgressBar mActivityWheel;
    private Toast mToast;

    // Firebase
    private DatabaseReference mFirebaseUserDispatchRequest;
    private DatabaseReference mFirebaseAvailableDrivers;
    private DatabaseReference mFirebaseLocationRequest;
    private ValueEventListener mListenerUserDispatchRequest;
    private ValueEventListener mListenerTrackDriver;
    private ValueEventListener mListenerUserDriverConnection;
    private String mDispatchRequestKey;

    // Flow Control
    private boolean mBoundsDisplayed;
    private State mDispatchState;
    private int mDriverClickCounter;
    private enum State {
        IDLE, REQUESTING, SEARCHING, CONNECTED, WAITING
    }

    // Google Api - Location, Map
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Polygon mBounds;
    private Marker mLocationMarker;
    private Marker mDriverLocationMarker;
    private Location mLastKnownLocation;
    public ArrayList<DriverLocation> mDriverLocations;

    // Navigation
    private FloatingActionButton mFabRequestDispatch;
    private FloatingActionButton mFabCancelDispatch;
    private Boolean mActionBarButtonState;

    // Runnable, Handler
    private int mNumberOfDrivers;
    private int mIndex;
    private Handler mHandler;
    private Runnable mRunnableWaitForResponse;

    //******* ACTIVITY LIFECYCLE *******//
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        // 1. onCreate()
        Log.d(DEBUG_ACTIVITY_LC, "1. onCreate()");


        // DEBUGGING keep screen alive
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        //******* Initialize Firebase *******//
        // 2. Initialize Firebase
        Log.d(DEBUG_ACTIVITY_LC, "2. Initialize Firebase");
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        mFirebaseAvailableDrivers = database.getReference("Available Drivers");
        mFirebaseLocationRequest = database.getReference("Location Request");
        mFirebaseUserDispatchRequest = database.getReference("Dispatch Request");
        mDispatchRequestKey = mFirebaseUserDispatchRequest.push().getKey();


        //******* Initialize Flow Control *******//
        // 3. Initialize Flow Control
        Log.d(DEBUG_ACTIVITY_LC, "3. Initialize Flow Control");
        mBoundsDisplayed = false;
        mDriverClickCounter = 1;
        mDispatchState = State.IDLE;


        //******* Initialize Google Api - Location, Map *******//
        // 4. Initialize Google Api - Location, Map
        Log.d(DEBUG_ACTIVITY_LC, "4. Initialize Google Api - Location, Map");
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mDriverLocations = new ArrayList<>();

        // 5. Build Api Client
        Log.d(DEBUG_ACTIVITY_LC, "5. Build Api Client");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


        //******* Initialize Navigation *******//
        // 6. Initialize Navigation

        Log.d(DEBUG_ACTIVITY_LC, "6. Initialize Navigation");
        mActivityWheel = (ProgressBar) findViewById(R.id.progress_bar);
        assert mActivityWheel != null;
        mActivityWheel.setVisibility(View.GONE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mFabRequestDispatch = (FloatingActionButton) findViewById(R.id.fab_request_dispatch);
        assert mFabRequestDispatch != null;
        mFabRequestDispatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickFabRequestDispatch(view);
            }
        });

        mFabCancelDispatch = (FloatingActionButton) findViewById(R.id.fab_cancel_dispatch);
        assert mFabCancelDispatch != null;
        mFabCancelDispatch.setVisibility(View.GONE);
        mFabCancelDispatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                destroyDispatchRequest();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        assert navigationView != null;
        navigationView.setNavigationItemSelectedListener(this);

        mActionBarButtonState = true;

    }

    @Override
    protected void onStart() {
        super.onStart();

        // 7. onStart()
        Log.d(DEBUG_ACTIVITY_LC, "7. onStart()");

        if (!mGoogleApiClient.isConnected()) {
            // 8. Connect to the api client
            Log.d(DEBUG_ACTIVITY_LC, "8. Connect to the api client");
            mGoogleApiClient.connect();
        }

        if (mListenerUserDispatchRequest == null) {
            // 9. listen for dispatch request
            Log.d(DEBUG_ACTIVITY_LC, "9. listen for dispatch request");
            mListenerUserDispatchRequest = mFirebaseUserDispatchRequest.child(mDispatchRequestKey).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    driverHandshake(dataSnapshot);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    toastBuilder("Dispatch cancelled. There was a database error!");
                    // 0. Dispatch cancelled from database error 1
                    Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from database error 1");
                    destroyDispatchRequest();
                }
            });
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        // 10. onPause()
        Log.d(DEBUG_ACTIVITY_LC, "10. onPause()");

        if (mDispatchState != State.IDLE) {
            // 0. Dispatch cancelled from onPause()
            Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from onPause()");
            // Destroying dispatch request from onPause()!
            Log.d(DEBUG_ACTIVITY_LC, "Destroying dispatch request from onPause()!");
            destroyDispatchRequest();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();

        // 11. onStop()
        Log.d(DEBUG_ACTIVITY_LC, "11. onStop()");

        if (mFirebaseUserDispatchRequest != null) {
            if (mListenerUserDispatchRequest != null) {
                // 12. Remove user dispatch request listener
                Log.d(DEBUG_ACTIVITY_LC, "12. Remove user dispatch request listener");
                mFirebaseUserDispatchRequest.removeEventListener(mListenerUserDispatchRequest);
                mListenerUserDispatchRequest = null;
            }
        }

        if (mGoogleApiClient.isConnected()) {
            // 13. Disconnect the api client
            Log.d(DEBUG_ACTIVITY_LC, "13. Disconnect the api client");
            mGoogleApiClient.disconnect();
        }

    }


    //******* ALERTS *******//
    public void toastBuilder(String message) {
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

    public void alertPicker(int which) {
        if (which == 1) {
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Dispatch to My Location")
                    .setMessage(
                            "By clicking CONFIRM you agree " +
                                    "to accepting a ride from our nearest " +
                                    "driver. A bike will be dispatched to your " +
                                    "location, please wait in a convenient pickup " +
                                    "location and do not navigate away " +
                                    "from the current screen. Thank you!")
                    .setPositiveButton("CONFIRM", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            confirmDispatch();
                        }
                    })
                    .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).create().show();
        } else if (which == 2) {
            new AlertDialog.Builder(this)
                    .setTitle("Out of Bounds Alert")
                    .setMessage(
                            "You are currently trying to request " +
                                    "a dispatch outside of our " +
                                    "operating boundaries. To view boundaries, click " +
                                    "TOGGLE BOUNDS")
                    .setPositiveButton("TOGGLE BOUNDS", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            toggleBoundaries();
                        }
                    })
                    .setNegativeButton("BACK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).create().show();
        } else if (which == 3) {
            new AlertDialog.Builder(this)
                    .setTitle("No Drivers Available")
                    .setMessage(
                            "No drivers are currently available, " +
                                    "please call dispatch to set up a " +
                                    "ride or try again in a few minuets."
                    ).setPositiveButton("CALL", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //TODO: Call dispatch
                        }
                    })
                    .setNegativeButton("BACK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).create().show();
        }
    }


    //******* NAVIGATION *******//
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        assert drawer != null;
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_toggle_bounds) {
            toggleBoundaries();
            return true;
        } else if ((id == R.id.action_refresh_drivers) && (mActionBarButtonState)) {
            // 1. Refresh driver locations button press
            Log.d(DEBUG_DRIVER_LOCATIONS, "1. Refresh driver locations button press");
            updateMap();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (mActionBarButtonState) {
            if (id == R.id.nav_book) {
                startActivity(new Intent(this, BookTourActivity.class));
            } else if (id == R.id.nav_info) {
                startActivity(new Intent(this, TourInformationActivity.class));
            } else if (id == R.id.nav_help) {
                startActivity(new Intent(this, HelpAndRatesActivity.class));
            } else if (id == R.id.nav_call) {
                Intent dialNumberIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + "1234567890"));
                startActivity(dialNumberIntent);
            }
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        assert drawer != null;
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void toggleBoundaries() {
        if (!mBoundsDisplayed) {
            mBounds = mMap.addPolygon(new PolygonOptions()
                    .add(new LatLng(32.082932, -81.096341),
                            new LatLng(32.079433, -81.083713),
                            new LatLng(32.062920, -81.089982),
                            new LatLng(32.066280, -81.102650))
                    .strokeColor(Color.BLUE));
            mBoundsDisplayed = true;
        } else {
            mBounds.remove();
            mBoundsDisplayed = false;
        }
    }


    //******* USER DISPATCH LOGIC *******//
    // View available drivers
    public void updateMap(){
        mFirebaseAvailableDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    // 2. Get driver locations
                    Log.d(DEBUG_DRIVER_LOCATIONS, "2. Get driver locations");
                    Map<String, Object> driverLocations = (Map<String, Object>) dataSnapshot.getValue();

                    // 3. Clear current map
                    Log.d(DEBUG_DRIVER_LOCATIONS, "3. Clear current map");
                    mMap.clear();

                    // 4. Update my location marker
                    Log.d(DEBUG_DRIVER_LOCATIONS, "4. Update my location marker");
                    updateMyMarker(mLastKnownLocation);

                    // 5. Update drivers locations on map
                    Log.d(DEBUG_DRIVER_LOCATIONS, "5. Update drivers locations on map");
                    for (Map.Entry<String, Object> driver : driverLocations.entrySet()) {

                        mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(Double.parseDouble(((Map<String, Object>) driver.getValue()).get("latitude").toString()),
                                        Double.parseDouble(((Map<String, Object>) driver.getValue()).get("longitude").toString())))
                                .title(driver.getKey())
                                .snippet(((Map<String, Object>) driver.getValue()).get("phoneNumber").toString())
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                toastBuilder("Could not update map. There was a database error!");
            }
        });
    }

    // Request a dispatch
    public void onClickFabRequestDispatch(View view){
        if (mLocationMarker != null) {
            if (withinBounds()) {
                alertPicker(1);
            } else {
                // TODO: Change to 1 if testing outside the bounds of Savannah, 2 if not
                alertPicker(1);
            }
        }
    }

    public void confirmDispatch() {
        // 1. Hide fab and show dispatch request state views, prevent device sleep, disable action bar buttons
        Log.d(DEBUG_REQUEST_DISPATCH, "1. Hide fab and show dispatch request state views, prevent device sleep");
        mActivityWheel.setVisibility(View.VISIBLE);
        mFabRequestDispatch.setVisibility(View.GONE);
        mFabCancelDispatch.setVisibility(View.VISIBLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mActionBarButtonState = false;
        toastBuilder("Locating drivers...");

        // 2. Refresh all driver locations
        Log.d(DEBUG_REQUEST_DISPATCH, "2. Refresh all driver locations");
        String key = mFirebaseLocationRequest.push().getKey();
        mFirebaseLocationRequest.child(key).setValue("REQUEST");
        mFirebaseLocationRequest.child(key).removeValue();

        // 3 A. Update Driver Locations
        Log.d(DEBUG_REQUEST_DISPATCH, "3 A. Update Driver Locations");
        updateDriverLocations();

        // 4. Change dispatch state to "currently waiting for driver update"
        Log.d(DEBUG_REQUEST_DISPATCH, "4. Change dispatch state to \"currently waiting for driver update\"");
        mDispatchState = State.WAITING;
    }

    public void updateDriverLocations(){

        mFirebaseAvailableDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.getValue() != null) {

                    Map<String, Object> driverLocations = (Map<String, Object>) dataSnapshot.getValue();

                    mDriverLocations.clear();

                    // 5. Update driver locations
                    Log.d(DEBUG_REQUEST_DISPATCH, "5. Update driver locations");
                    for (Map.Entry<String, Object> driver : driverLocations.entrySet()) {

                        DriverLocation location = new DriverLocation(new LatLng(Double.parseDouble(((Map<String, Object>) driver.getValue()).get("latitude").toString()),
                                Double.parseDouble(((Map<String, Object>) driver.getValue()).get("longitude").toString())),
                                driver.getKey());
                        location.setDistance(new LatLng(mLocationMarker.getPosition().latitude, mLocationMarker.getPosition().longitude));

                        mDriverLocations.add(location);
                    }

                    // 6. Push dispatch request onto the database
                    Log.d(DEBUG_REQUEST_DISPATCH, "6. Push dispatch request onto the database");
                    mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("longitude").setValue(mLastKnownLocation.getLongitude());
                    mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("latitude").setValue(mLastKnownLocation.getLatitude());

                    // 7. Set state to "Requesting a dispatch"
                    Log.d(DEBUG_REQUEST_DISPATCH, "7. Set state to \"Requesting a dispatch\"");
                    mDispatchState = State.REQUESTING;


                } else {
                    // 3 B. No drivers are currently available
                    Log.d(DEBUG_REQUEST_DISPATCH, "3 B. No drivers are currently available");
                    mDispatchState = State.IDLE;
                    alertPicker(3);
                    // 0. Dispatch ended from unavailable drivers
                    Log.d(DEBUG_ON_CANCEL, "0. Dispatch ended from unavailable drivers");
                    destroyDispatchRequest();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                toastBuilder("Dispatch Cancelled. There was a database error!");
                // 0. Dispatch cancelled from database error 3
                Log.d(DEBUG_ON_CANCEL, "0. Dispatch cancelled from database error 3");
                destroyDispatchRequest();
            }
        });
    }

    public void searchForDriver() {
        if (mDispatchState == State.SEARCHING) {

            mHandler = new Handler();
            mNumberOfDrivers = mDriverLocations.size();
            mIndex = 0;

            mRunnableWaitForResponse = new Runnable() {
                @Override
                public void run() {
                    if (mDispatchState != State.IDLE) {
                        if ((mIndex < mNumberOfDrivers-1)) {
                            // 12 B. Check to see if the driver has responded, else try the next on the list
                            Log.d(DEBUG_REQUEST_DISPATCH, "12 B. Check to see if the driver has responded, else try the next on the list");

                            if (mDispatchState == State.CONNECTED) {
                                // 13. Connected to driver, quit runnable, track driver
                                Log.d(DEBUG_REQUEST_DISPATCH, "13. Connected to driver, quit runnable");
                                toastBuilder("Connected!");
                                onConnectedToDriver();
                            } else if (mDispatchState == State.SEARCHING) {
                                // 12 C1. Driver has not responded, request next closest driver
                                Log.d(DEBUG_REQUEST_DISPATCH, "12 C1. Driver has not responded, request next closest driver");
                                toastBuilder("Driver declined. Trying next closest driver...");
                                mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").removeValue();
                                mIndex++;
                                mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).getName()).child("Dispatch Request").setValue(mDispatchRequestKey);
                                mHandler.postDelayed(this, 15000);
                            }
                        } else {
                            if (mDispatchState == State.SEARCHING){
                                // 12 C2. No drivers are currently available
                                Log.d(DEBUG_REQUEST_DISPATCH, "12 C2. No drivers are currently available");
                                mDispatchState = State.IDLE;
                                alertPicker(3);
                                mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").removeValue();
                                // 0. Dispatch ended from unresponsive drivers
                                Log.d(DEBUG_ON_CANCEL, "0. Dispatch ended from unresponsive drivers");
                                destroyDispatchRequest();
                            }
                        }
                    }
                }
            };

            // 11. Request nearest available driver and provide 10 seconds for response
            Log.d(DEBUG_REQUEST_DISPATCH, "11. Request nearest available driver and provide 10 seconds for response");
            toastBuilder("Contacting nearest driver...");
            mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).getName()).child("Dispatch Request").setValue(mDispatchRequestKey);
            mHandler.postDelayed(mRunnableWaitForResponse, 15000);
        }
    }

    @SuppressWarnings("unchecked")
    public void driverHandshake(DataSnapshot dataSnapshot){
        if (mDispatchState == State.REQUESTING) {
            if (dataSnapshot.getValue() != null) {
                // 8. Sort available drivers by closest
                Log.d(DEBUG_REQUEST_DISPATCH, "8. Sort available drivers by closest");
                if (mDriverLocations != null) {
                    Collections.sort(mDriverLocations);
                }

                // 9. Change dispatch state to "Searching and contacting nearest driver"
                Log.d(DEBUG_REQUEST_DISPATCH, "9. Change dispatch state to \"Searching and contacting nearest driver\"");
                mDispatchState = State.SEARCHING;

                // 10. Find and contact nearest driver
                Log.d(DEBUG_REQUEST_DISPATCH, "10. Find and contact nearest driver");
                searchForDriver();

            } else {
                // 0. Dispatch ended from null database return 1
                Log.d(DEBUG_ON_CANCEL, "0. Dispatch ended from null database return 1");
                destroyDispatchRequest();
            }
        } else if (mDispatchState == State.SEARCHING) {
            if (dataSnapshot.getValue() != null) {
                Map<String, Object> dispatchRequest = (Map<String, Object>) dataSnapshot.getValue();
                boolean isConnected = dispatchRequest.containsKey("Connected");
                if (isConnected) {
                    // 12 A. Driver has responded, send connect request, update state to "connected"
                    Log.d(DEBUG_REQUEST_DISPATCH, "12 A. Driver has responded, send connect request, update state to \"connected\"");
                    String driverName = (String) dispatchRequest.get("Connected");
                    toastBuilder("Connecting to " + driverName + "...");
                    mFirebaseAvailableDrivers.child(driverName).child("Dispatch Request").setValue("Connected");
                    mDispatchState = State.CONNECTED;
                }
            } else {
                // 0. Dispatch ended from null database return 2
                Log.d(DEBUG_ON_CANCEL, "0. Dispatch ended from null database return 2");
                destroyDispatchRequest();
            }
        }
    }

    // Connected to dispatch
    public void onConnectedToDriver() {

        // 1. Hide dispatch request buttons
        Log.d(DEBUG_ON_CONNECTED, "1. Hide dispatch request buttons");
        mActivityWheel.setVisibility(View.GONE);

        // 2. Clear map
        Log.d(DEBUG_ON_CONNECTED, "2. Clear map");
        mMap.clear();

        // 3. Update my marker
        Log.d(DEBUG_ON_CONNECTED, "3. Update my marker");
        updateMyMarker(mLastKnownLocation);

        // 4. Listen for driver location updates
        Log.d(DEBUG_ON_CONNECTED, "4. Listen for driver location updates");
        mListenerTrackDriver = mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("driver").addValueEventListener(new ValueEventListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {

                    Map<String, Object> driverInfo = (Map<String, Object>) dataSnapshot.getValue();

                    if (mDriverLocationMarker != null) {
                        mDriverLocationMarker.remove();
                    }

                    // Tracking driver location...
                    Log.d(DEBUG_ON_CONNECTED, "Tracking driver location...");

                    if ((driverInfo.get("latitude") != null) &&
                    (driverInfo.get("longitude") != null) &&
                            (driverInfo.get("name") != null) &&
                            (driverInfo.get("phoneNumber") != null)) {
                        mDriverLocationMarker = mMap.addMarker(new MarkerOptions()
                                .position(new LatLng((Double) driverInfo.get("latitude"), (Double) driverInfo.get("longitude")))
                                .title((String) driverInfo.get("name"))
                                .snippet((String) driverInfo.get("phoneNumber"))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                    }
                } else {
                    // 0. Dispatch ended from null database return 3
                    Log.d(DEBUG_ON_CANCEL, "0. Dispatch ended from null database return 3");
                    destroyDispatchRequest();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                toastBuilder("Dispatch cancelled. There was a database error!");
                // 0 B. Dispatch cancelled from database error 4
                Log.d(DEBUG_ON_CANCEL, "0 B. Dispatch cancelled from database error 4");
                destroyDispatchRequest();
            }
        });

        // 5. Listen for driver ending dispatch
        Log.d(DEBUG_ON_CONNECTED, "5. Listen for driver ending dispatch");
        mListenerUserDriverConnection = mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    if (dataSnapshot.getValue().equals("Driver Cancelled")) {
                        // 6. Driver ended dispatch
                        Log.d(DEBUG_ON_CONNECTED, "6. Driver ended dispatch");
                        toastBuilder("Dispatch ended by driver");
                        mDispatchState = State.IDLE;

                        // 0 D. Dispatch ended from driver
                        Log.d(DEBUG_ON_CANCEL, "0 D. Dispatch ended from driver");
                        destroyDispatchRequest();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                toastBuilder("Dispatch cancelled. There was a database error!");
                // 0 B. Dispatch cancelled from database error 5
                Log.d(DEBUG_ON_CANCEL, "0 B. Dispatch cancelled from database error 5");
                destroyDispatchRequest();
            }
        });

    }

    public void destroyDispatchRequest() {

        // 1. Change dispatch state to "not requesting a dispatch"
        Log.d(DEBUG_ON_CANCEL, "1. Change dispatch state to \"not requesting a dispatch\"");
        if (mDispatchState != State.IDLE) {
            toastBuilder("Dispatch cancelled!");
        }

        // 2. Show fab and hide dispatch request state views, enable action bar buttons
        Log.d(DEBUG_ON_CANCEL, "2. Show fab and hide dispatch request state views, enable action bar buttons");
        mActivityWheel.setVisibility(View.GONE);
        mFabRequestDispatch.setVisibility(View.VISIBLE);
        mFabCancelDispatch.setVisibility(View.GONE);
        mActionBarButtonState = true;

        // 3. Let the device sleep, clear driver marker
        if (mDriverLocationMarker != null) {
            mDriverLocationMarker.remove();
        }
        Log.d(DEBUG_ON_CANCEL, "3. Let the device sleep");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (mDispatchState == State.SEARCHING) {
            // 4 A. Destroy current search query
            Log.d(DEBUG_ON_CANCEL, "4 A. Destroy current search query");
            mFirebaseAvailableDrivers.child(mDriverLocations.get(mIndex).name).child("Dispatch Request").removeValue();
        }
        if (mDispatchState == State.CONNECTED) {
            // 4 B. Set connected node to "Cancelled" if connected
            Log.d(DEBUG_ON_CANCEL, "4 B. Set connected node to \"Cancelled\" if connected");
            mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").setValue("User Cancelled");
        }
        if (mDispatchRequestKey != null) {
            // 4 C. Remove dispatch request from the database
            Log.d(DEBUG_ON_CANCEL, "4 C. Remove dispatch request from the database");
            mFirebaseUserDispatchRequest.child(mDispatchRequestKey).removeValue();

            if (mListenerUserDriverConnection != null) {
                // 4 D. Remove user driver connection listener
                Log.d(DEBUG_ON_CANCEL, "4 C. Remove dispatch request from the database");
                mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("Connected").removeEventListener(mListenerUserDriverConnection);
            }

        }

        if (mListenerTrackDriver != null) {
            // 4 E. Remove track driver listener
            Log.d(DEBUG_ON_CANCEL, "4 E. Remove track driver listener");
            mFirebaseUserDispatchRequest.removeEventListener(mListenerTrackDriver);
        }

        if ((mHandler != null) && (mRunnableWaitForResponse != null)) {
            mHandler.removeCallbacks(mRunnableWaitForResponse);
            mHandler = null;
            mRunnableWaitForResponse = null;
        }

        // 5. Change state to idle
        Log.d(DEBUG_ON_CANCEL, "5. Change state to idle");
        mDispatchState = State.IDLE;
    }

    // Boundaries logic
    public boolean withinBounds() {

        // Savannah Bike Taxi operating boundaries
        LatLng A = new LatLng(32.082932, -81.096341);
        LatLng B = new LatLng(32.079433, -81.083713);
        LatLng C = new LatLng(32.062920, -81.089982);
        LatLng D = new LatLng(32.066280, -81.102650);

        LatLng P = mLocationMarker.getPosition();

        Double AREA_BOUND = Math.sqrt((Math.pow(A.latitude - B.latitude, 2) + Math.pow(A.longitude - B.longitude, 2))) *
                Math.sqrt((Math.pow(A.latitude - D.latitude, 2) + Math.pow(A.longitude - D.longitude, 2)));

        Double AREA_TOTAL = calculateArea(A, B, P) +
                calculateArea(A, D, P) +
                calculateArea(D, C, P) +
                calculateArea(C, B, P);

        return (AREA_BOUND >= AREA_TOTAL);
    }

    public double calculateArea(LatLng A, LatLng B, LatLng C) {
        Double a = Math.sqrt((Math.pow(A.latitude - B.latitude, 2) + Math.pow(A.longitude - B.longitude, 2)));
        Double b = Math.sqrt((Math.pow(A.latitude - C.latitude, 2) + Math.pow(A.longitude - C.longitude, 2)));
        Double c = Math.sqrt((Math.pow(B.latitude - C.latitude, 2) + Math.pow(B.longitude - C.longitude, 2)));
        Double s = (a+b+c) / 2;
        return Math.sqrt(s*(s-a)*(s-b)*(s-c));
    }


    //******* NAVIGATE TO LOGIN VIEW *******//
    public void signInAsDriver(View view) {
        if (mDriverClickCounter < 7) {
            mDriverClickCounter++;
        } else {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            this.finish();
        }
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

        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                String phoneNumber = marker.getSnippet();
                if (phoneNumber != null) {
                    Intent dialNumberIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
                    startActivity(dialNumberIntent);
                }
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
        // TODO: look into HIGH ACCURACY vs BATTERY and DATA saver
        // Consider, to save driver data and battery, letting him choose the interval
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(500);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling ActivityCompat#requestPermissions here to request the missing permissions
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
        LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }

    @Override
    public void onConnectionSuspended(int i) {
        //TODO: Handle suspended connection
    }

    @Override
    public void onLocationChanged(Location location) {

        mLastKnownLocation = location;

        updateMyMarker(location);
        
        if (mDispatchState == State.CONNECTED) {
            trackUserLocation(location);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle failed connection
    }

    public void trackUserLocation(Location location) {
        // Tracking my location...
        Log.d(DEBUG_ON_CONNECTED, "Tracking my location...");

        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("longitude").setValue(location.getLongitude());
        mFirebaseUserDispatchRequest.child(mDispatchRequestKey).child("latitude").setValue(location.getLatitude());
    }

    public void updateMyMarker(Location location) {

        if (mLocationMarker != null) {
            mLocationMarker.remove();
        }

        mLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .title("My Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }
}
