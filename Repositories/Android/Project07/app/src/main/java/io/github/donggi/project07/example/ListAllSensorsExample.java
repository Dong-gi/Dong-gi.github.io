package io.github.donggi.project07.example;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

public class ListAllSensorsExample {
    private static final String TAG = ListAllSensorsExample.class.getCanonicalName();

    public ListAllSensorsExample(Context context) {
        SensorManager manager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        for(Sensor sensor : manager.getSensorList(Sensor.TYPE_ALL))
            Log.d(TAG, sensor.toString());
    }
/*
{Sensor name="LGE Accelerometer", vendor="BOSCH", version=1173100, type=1, maxRange=78.4532, resolution=0.0023956299, power=0.18, minDelay=5000}
{Sensor name="LGE Magnetometer", vendor="AKM", version=1, type=2, maxRange=4911.9995, resolution=0.14953613, power=1.1, minDelay=10000}
{Sensor name="LGE Magnetometer Uncalibrated", vendor="AKM", version=1, type=14, maxRange=4911.9995, resolution=0.14953613, power=1.1, minDelay=10000}
{Sensor name="LGE Gyroscope", vendor="BOSCH", version=1173100, type=4, maxRange=34.906586, resolution=0.0010681152, power=0.9, minDelay=5000}
{Sensor name="LGE Gyroscope Uncalibrated", vendor="BOSCH", version=1173100, type=16, maxRange=34.906586, resolution=0.0010681152, power=0.9, minDelay=5000}
{Sensor name="LGE Proximity", vendor="PARTRON", version=2, type=8, maxRange=5.0, resolution=500.0, power=0.06, minDelay=0}
{Sensor name="LGE Knock-on Proximity Sensor", vendor="LGE", version=2, type=499898103, maxRange=5.0, resolution=0.0, power=0.0, minDelay=0}
{Sensor name="LGE Virtual Proximity Sensor", vendor="LGE", version=1, type=499898105, maxRange=5.0, resolution=0.0, power=0.1, minDelay=0}
{Sensor name="LGE WakeUp Light Sensor", vendor="LGE", version=2, type=499898108, maxRange=5.0, resolution=0.0, power=0.0, minDelay=0}
{Sensor name="Health Activity Pattern Sensor", vendor="LGE", version=2, type=499898109, maxRange=1.0, resolution=1.0, power=0.0029907227, minDelay=0}
{Sensor name="LGE Light", vendor="PARTRON", version=1, type=5, maxRange=32768.0, resolution=1.0, power=0.115, minDelay=0}
{Sensor name="LGE Pressure", vendor="ALPS ELECTRIC CO., ", version=1, type=6, maxRange=1100.0, resolution=0.009994507, power=0.003, minDelay=33333}
{Sensor name="LGE Accelerometer -Wakeup Secondary", vendor="BOSCH", version=1173100, type=1, maxRange=78.4532, resolution=0.0023956299, power=0.18, minDelay=5000}
{Sensor name="LGE Magnetometer -Wakeup Secondary", vendor="AKM", version=1, type=2, maxRange=4911.9995, resolution=0.14953613, power=1.1, minDelay=10000}
{Sensor name="LGE Magnetometer Uncalibrated -Wakeup Secondary", vendor="AKM", version=1, type=14, maxRange=4911.9995, resolution=0.14953613, power=1.1, minDelay=10000}
{Sensor name="LGE Gyroscope -Wakeup Secondary", vendor="BOSCH", version=1173100, type=4, maxRange=34.906586, resolution=0.0010681152, power=0.9, minDelay=5000}
{Sensor name="LGE Gyroscope Uncalibrated -Wakeup Secondary", vendor="BOSCH", version=1173100, type=16, maxRange=34.906586, resolution=0.0010681152, power=0.9, minDelay=5000}
{Sensor name="LGE Proximity -Non Wakeup Secondary", vendor="PARTRON", version=2, type=8, maxRange=5.0, resolution=500.0, power=0.06, minDelay=0}
{Sensor name="LGE Light -Wakeup Secondary", vendor="PARTRON", version=1, type=5, maxRange=32768.0, resolution=1.0, power=0.115, minDelay=0}
{Sensor name="LGE Pressure -Wakeup Secondary", vendor="ALPS ELECTRIC CO., ", version=1, type=6, maxRange=1100.0, resolution=0.009994507, power=0.003, minDelay=33333}
{Sensor name="LGE Gravity Sensor", vendor="QTI", version=2, type=9, maxRange=78.4532, resolution=0.0023956299, power=1.0799866, minDelay=5000}
{Sensor name="LGE Linear Acceleration Sensor", vendor="QTI", version=2, type=10, maxRange=78.4532, resolution=0.0023956299, power=1.0799866, minDelay=5000}
{Sensor name="LGE Rotation Vector Sensor", vendor="QTI", version=2, type=11, maxRange=1.0, resolution=5.9604645E-8, power=1.9999847, minDelay=5000}
{Sensor name="LGE Step Detector Sensor", vendor="QTI", version=2, type=18, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="LGE Step Counter Sensor", vendor="QTI", version=2, type=19, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="LGE Significant Motion Detector Sensor", vendor="QTI", version=2, type=17, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=-1}
{Sensor name="LGE Game Rotation Vector Sensor", vendor="QTI", version=2, type=15, maxRange=1.0, resolution=5.9604645E-8, power=1.0799866, minDelay=5000}
{Sensor name="LGE GeoMagnetic Rotation Vector Sensor", vendor="QTI", version=2, type=20, maxRange=1.0, resolution=5.9604645E-8, power=1.2890625, minDelay=10000}
{Sensor name="LGE Orientation Sensor", vendor="QTI", version=2, type=3, maxRange=360.0, resolution=0.1, power=1.9999847, minDelay=5000}
{Sensor name="LGE Tilt Detector Sensor", vendor="QTI", version=2, type=22, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="Gravity -Wakeup Secondary", vendor="QTI", version=2, type=9, maxRange=78.4532, resolution=0.0023956299, power=1.0799866, minDelay=5000}
{Sensor name="Linear Acceleration -Wakeup Secondary", vendor="QTI", version=2, type=10, maxRange=78.4532, resolution=0.0023956299, power=1.0799866, minDelay=5000}
{Sensor name="Rotation Vector -Wakeup Secondary", vendor="QTI", version=2, type=11, maxRange=1.0, resolution=5.9604645E-8, power=1.9999847, minDelay=5000}
{Sensor name="Step Detector -Wakeup Secondary", vendor="QTI", version=2, type=18, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="Step Counter -Wakeup Secondary", vendor="QTI", version=2, type=19, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="Game Rotation Vector -Wakeup Secondary", vendor="QTI", version=2, type=15, maxRange=1.0, resolution=5.9604645E-8, power=1.0799866, minDelay=5000}
{Sensor name="GeoMagnetic Rotation Vector -Wakeup Secondary", vendor="QTI", version=2, type=20, maxRange=1.0, resolution=5.9604645E-8, power=1.2890625, minDelay=10000}
{Sensor name="Orientation -Wakeup Secondary", vendor="QTI", version=2, type=3, maxRange=360.0, resolution=0.1, power=1.9999847, minDelay=5000}
{Sensor name="LGE Absolute Motion Detector Sensor", vendor="QTI", version=2, type=33171006, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="LGE Relative Motion Detector Sensor", vendor="QTI", version=2, type=33171007, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="LGE Modified PAM Sensor", vendor="LGE", version=6, type=33171010, maxRange=65535.0, resolution=1.0, power=11.79599, minDelay=5000}
{Sensor name="LG Motion Accel", vendor="QTI", version=1, type=499898101, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="LGE Coarse Motion Classifier Sensor", vendor="QTI", version=3, type=33171012, maxRange=2.1474836E9, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="LGE Absolute Motion Detector Sensor - Wake Up Secondary", vendor="QTI", version=2, type=33171006, maxRange=1.0, resolution=1.0, power=0.17999268, minDelay=0}
{Sensor name="LGE OnHand Detector", vendor="QTI", version=1, type=499898111, maxRange=1.0, resolution=1.0, power=0.24000001, minDelay=0}
{Sensor name="LGE PickUp Sensor", vendor="LGE", version=1, type=499898110, maxRange=1.0, resolution=1.0, power=0.24000001, minDelay=1000000}
{Sensor name="LGE VR Accelerometer", vendor="LGE", version=1, type=65537, maxRange=19.6133, resolution=0.0047884034, power=0.23, minDelay=1000}
{Sensor name="LGE VR Accelerometer Uncalibrated", vendor="LGE", version=1, type=65538, maxRange=19.6133, resolution=0.0047884034, power=0.23, minDelay=1000}
{Sensor name="LGE VR Gyroscope", vendor="LGE", version=1, type=65539, maxRange=2000.0, resolution=1.0, power=0.5, minDelay=1000}
{Sensor name="LGE VR Gyroscope Uncalibrated", vendor="LGE", version=1, type=65540, maxRange=2000.0, resolution=1.0, power=0.5, minDelay=1000}
{Sensor name="LGE VR Magnetic Field", vendor="LGE", version=1, type=65541, maxRange=10240.0, resolution=1.0, power=0.5, minDelay=10000}
{Sensor name="LGE VR Magnetic Field Uncalibrated", vendor="LGE", version=1, type=65542, maxRange=10240.0, resolution=1.0, power=0.5, minDelay=10000}
{Sensor name="LGE VR Proximity", vendor="LGE", version=1, type=65543, maxRange=10240.0, resolution=1.0, power=0.5, minDelay=0}
 */
}
