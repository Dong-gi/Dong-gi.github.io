package io.github.donggi.project07.example;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

public class LocationExample implements LocationListener {
    private static final String TAG = LocationExample.class.getCanonicalName();

    public LocationExample(Activity activity) {
        LocationManager manager = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
        PermissionChecker.getInstance(activity).requestPermission("위치 권한 주세요", Manifest.permission.ACCESS_FINE_LOCATION);
        if(!PermissionChecker.getInstance(activity).checkPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            return;
        Log.d(TAG, String.format("Available providers : %s", Arrays.toString(manager.getProviders(true).toArray())));
        manager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, this);
        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }
    /*
Available providers : [passive, gps, network]
onLocationChanged : Location[network 37.483804,126.882368 acc=700 et=+3d8h39m14s559ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.483804,126.882368 acc=700 et=+3d8h39m14s559ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.485081,126.882032 acc=1700 et=+3d8h39m34s582ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.485081,126.882032 acc=1700 et=+3d8h39m34s582ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.483804,126.882368 acc=700 et=+3d8h39m39s199ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.483804,126.882368 acc=700 et=+3d8h39m39s199ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.483804,126.882368 acc=700 et=+3d8h39m44s227ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.483804,126.882368 acc=700 et=+3d8h39m44s227ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.485081,126.882032 acc=1700 et=+3d8h39m49s257ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.485081,126.882032 acc=1700 et=+3d8h39m49s257ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.485081,126.882032 acc=1700 et=+3d8h39m54s273ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.485081,126.882032 acc=1700 et=+3d8h39m54s273ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
onLocationChanged : Location[network 37.485081,126.882032 acc=1700 et=+3d8h40m0s905ms alt=0.0 vel=0.0 bear=0.0 {Bundle[mParcelledData.dataSize=80]}]
     */

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, String.format("onLocationChanged : %s", location));
    }
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, String.format("onStatusChanged : %s, %d", provider, status));
    }
    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, String.format("onProviderEnabled : %s", provider));
    }
    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, String.format("onProviderDisabled : %s", provider));
    }
}
