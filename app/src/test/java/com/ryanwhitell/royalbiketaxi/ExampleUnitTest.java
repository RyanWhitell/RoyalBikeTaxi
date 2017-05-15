package com.ryanwhitell.royalbiketaxi;

import android.graphics.Color;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest {

    public boolean withinBounds(LatLng marker) {

        LatLng A = new LatLng(32.082932, -81.096341);
        LatLng B = new LatLng(32.079433, -81.083713);
        LatLng C = new LatLng(32.062920, -81.089982);
        LatLng D = new LatLng(32.066280, -81.102650);

        LatLng P = marker;

        Double AREA_BOUND = Math.sqrt((Math.pow(A.latitude - B.latitude, 2) + Math.pow(A.longitude - B.longitude, 2))) *
                Math.sqrt((Math.pow(A.latitude - D.latitude, 2) + Math.pow(A.longitude - D.longitude, 2)));

        Double AREA_TOTAL = calculateArea(A, B, P) +
                calculateArea(A, D, P) +
                calculateArea(D, C, P) +
                calculateArea(C, B, P);

        if (AREA_TOTAL > AREA_BOUND) {
            return false;
        } else {
            return true;
        }

    }
    public double calculateArea(LatLng A, LatLng B, LatLng C) {
        Double a = Math.sqrt((Math.pow(A.latitude - B.latitude, 2) + Math.pow(A.longitude - B.longitude, 2)));
        Double b = Math.sqrt((Math.pow(A.latitude - C.latitude, 2) + Math.pow(A.longitude - C.longitude, 2)));
        Double c = Math.sqrt((Math.pow(B.latitude - C.latitude, 2) + Math.pow(B.longitude - C.longitude, 2)));
        Double s = (a+b+c) / 2;
        return Math.sqrt(s*(s-a)*(s-b)*(s-c));
    }

    @Test
    public void testWithinBounds() throws Exception {

        LatLng mLocationMarkerIN = new LatLng(32.073940, -81.093933);
        LatLng mLocationMarkerOUT = new LatLng(32.071840, -81.086369);
        LatLng mLocationMarkerON = new LatLng(32.082932, -81.096341);

        assertEquals(true, withinBounds(mLocationMarkerIN));
        assertEquals(true, withinBounds(mLocationMarkerON));
        assertEquals(false, withinBounds(mLocationMarkerOUT));
    }


}