package io.github.donggi.project07.example;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;

public class AccelerometerExample {
    private static final String TAG = AccelerometerExample.class.getCanonicalName();

    public AccelerometerExample(Context context) {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        for (Method method : Sensor.class.getMethods()) {
            if (method.getName().startsWith("get")) {
                try {
                    Log.d(TAG, String.format("%s : %s", method.getName(), method.invoke(accel)));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        manager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                Log.d(TAG, Arrays.toString(event.values));
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        }, accel, SensorManager.SENSOR_DELAY_NORMAL);
    }
}
/*
getClass : class android.hardware.Sensor
getFifoMaxEventCount : 40940
getFifoReservedEventCount : 3000
getHandle : 1
getMaxDelay : 1000000
getMaximumRange : 78.4532
getMinDelay : 5000
getName : LGE Accelerometer
getPower : 0.18
getReportingMode : 0
getRequiredPermission :
getResolution : 0.0023956299
getStringType : android.sensor.accelerometer
getType : 1
getVendor : BOSCH
getVersion : 1173100


// 순서대로 x, y, z
[-0.48815918, -0.32209778, 9.877182]
[-2.1306, -0.7949524, 10.694809]
[-8.392639, -1.9992371, 5.1989136]
[-9.092941, -1.9740906, 5.971039]
[-9.001953, 0.6152344, 7.9701996]
[-3.3624115, 5.005005, 6.445099]
[-1.1561584, 8.076767, 4.6003723]
[0.12954712, 9.149368, -1.6772461]
[-6.239044, 5.3545685, -1.3097229]
[-6.5203705, 1.0090942, -5.930542]
[-5.063492, -3.4405518, -11.820267]
[-8.0742035, -3.9696655, -6.0981293]
[-7.3870697, -1.1109924, 2.330658]
 */