package com.ryanwhitell.royalbiketaxi.main.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.NotificationCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ryanwhitell.royalbiketaxi.R;
import com.ryanwhitell.royalbiketaxi.main.activities.DriverActivity;

// TODO:
// This backround service is incomplete. Theoretically, to save battery and data, the drivers should be able
// exit the app and still be listening for incoming dispatches.
public class DriverService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    //******* VARIABLES *******//
    // Firebase
    private DatabaseReference mFirebaseAvailableDrivers;
    private ValueEventListener mMyDispatchRequestListener;
    private DatabaseReference mFirebaseLocationRequest;
    private ValueEventListener mLocationRequestListener;

    // Global
    public static boolean sIsActive;
    public static String sDriverName;
    public static String sDriverNumber;
    public static String sDispatchRequestKey;

    // Google Api - Location
    private GoogleApiClient mGoogleApiClient;
    private Location mLastKnownLocation;

    // Notifications
    private Notification mNotification;

    //******* SERVICE *******//
    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Let it continue running until it is stopped.

        String action = intent.getAction();

        if (action.equals("Start") && (!sIsActive)) {
            onStartService();

            Intent notificationIntent = new Intent(this, DriverActivity.class);
            notificationIntent.setAction("Main");
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this,
                    0,
                    notificationIntent,
                    0);

            mNotification = new NotificationCompat.Builder(this)
                    .setContentTitle("You are online as " + sDriverName)
                    .setContentText("Currently awaiting a dispatch...")
                    .setSmallIcon(R.drawable.ic_directions_bike_white)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true).build();

            startForeground(7, mNotification);

        } else if (action.equals("DECLINE")) {
            mFirebaseAvailableDrivers.child(sDriverName).child("Dispatch Request").removeValue();
            makeNotification(1);

        } else if (action.equals("Stop") && (sIsActive)) {
            stopListening();
            stopForeground(true);
            stopSelf();
            sIsActive = false;

        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopListening();
        sIsActive = false;
    }

    // Intent Control
    public void onStartService() {
        sIsActive = true;

        //Google API CLIENT
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        //Firebase
        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAvailableDrivers = firebaseDatabase.getReference().child("Available Drivers");
        mFirebaseLocationRequest = firebaseDatabase.getReference("Location Request");

        startListening();
    }

    public void makeNotification(int which) {

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(this, DriverActivity.class);
        notificationIntent.setAction("Main");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0,
                notificationIntent,
                0);

        Intent acceptIntent = new Intent(getBaseContext(), DriverActivity.class);
        acceptIntent.setAction("Connect to User");
        acceptIntent.putExtra("Accepted Request", "Accepted");
        acceptIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pAcceptIntent = PendingIntent.getActivity(getBaseContext(),
                0,
                acceptIntent,
                0);

        Intent declineIntent = new Intent(this, DriverService.class);
        declineIntent.setAction("DECLINE");
        PendingIntent pDeclineIntent = PendingIntent.getService(this, 0,
                declineIntent, 0);

        if (which == 1) {
            mNotification = new NotificationCompat.Builder(this)
                    .setContentTitle("You are online as " + sDriverName)
                    .setContentText("Currently awaiting a dispatch...")
                    .setSmallIcon(R.drawable.ic_directions_bike_white)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOngoing(true).build();

            mNotificationManager.notify(7, mNotification);
        } else if (which == 2) {
            mNotification = new NotificationCompat.Builder(this)
                    .setContentTitle("Incoming dispatch!")
                    .setContentText("Please accept or decline")
                    .setSmallIcon(R.drawable.ic_directions_bike_white)
                    .setContentIntent(null)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .addAction(R.drawable.ic_done_black_24dp,
                            "ACCEPT", pAcceptIntent)
                    .addAction(R.drawable.ic_clear_black_24dp, "DECLINE",
                            pDeclineIntent).build();

            mNotificationManager.notify(7, mNotification);
        }

    }

    //******* FIREBASE *******//
    public void startListening() {

        mMyDispatchRequestListener = mFirebaseAvailableDrivers.child(sDriverName).child("Dispatch Request").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    makeNotification(2);
                    sDispatchRequestKey = dataSnapshot.getValue().toString();

                } else {
                    makeNotification(1);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        mLocationRequestListener = mFirebaseLocationRequest.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                driverLocationRequestReceived();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //TODO: Handle database error
            }
        });
    }

    private void stopListening() {
        mFirebaseAvailableDrivers.child(sDriverName).child("Dispatch Request").removeEventListener(mMyDispatchRequestListener);
        mFirebaseLocationRequest.removeEventListener(mLocationRequestListener);
        mGoogleApiClient.disconnect();
    }

    public void driverLocationRequestReceived() {

        mGoogleApiClient.connect();

        if ((mLastKnownLocation != null)) {
            mFirebaseAvailableDrivers.child(sDriverName).child("latitude").setValue(mLastKnownLocation.getLatitude());
            mFirebaseAvailableDrivers.child(sDriverName).child("longitude").setValue(mLastKnownLocation.getLongitude());
            mFirebaseAvailableDrivers.child(sDriverName).child("phoneNumber").setValue(sDriverNumber);
        }
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

        mFirebaseAvailableDrivers.child(sDriverName).child("latitude").setValue(location.getLatitude());
        mFirebaseAvailableDrivers.child(sDriverName).child("longitude").setValue(location.getLongitude());
        mFirebaseAvailableDrivers.child(sDriverName).child("phoneNumber").setValue(sDriverNumber);

        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //TODO: Handle failed connection
    }

}