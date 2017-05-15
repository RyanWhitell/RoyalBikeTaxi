package com.ryanwhitell.royalbiketaxi.main.model;

import android.support.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

public class DriverLocation implements Comparable {
    public String name;
    private LatLng location;
    private Double distance;

    public DriverLocation() {
    }

    public DriverLocation(LatLng location, String name) {
        this.location = location;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LatLng getLocation() {
        return location;
    }

    public void setLocation(LatLng location) {
        this.location = location;
    }

    private Double getDistance() {
        return distance;
    }

    public void setDistance(LatLng customerLocation) {
        this.distance = Math.sqrt((Math.pow(this.location.latitude - customerLocation.latitude, 2) +
                Math.pow(this.location.longitude - customerLocation.longitude, 2)));
    }

    @Override
    public int compareTo(@NonNull Object another) {
        DriverLocation other = (DriverLocation) another;
        if (this.distance <= other.getDistance()) return -1;
        else return 1;
    }
}
