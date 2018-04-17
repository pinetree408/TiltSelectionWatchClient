package com.pinetree408.research.tiltselection;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends WearableActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mAccel;
    private Sensor mGyro;
    private Sensor mLinear;
    private Sensor mRotate;

    private Button mButton;

    private int modeFlag = 0;

    Socket socket;

    String ip = "143.248.197.109";
    int port = 5000;

    float refAngle = 360;
    float preRefAngle;
    int count = 0;

    Vibrator vibrator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        IO.Options opts = new IO.Options();
        opts.forceNew = true;

        try {
            socket = IO.socket("http://" + ip + ":" + port + "/mynamespace", opts);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                modeFlag = 1;
                Log.d("Socket", "connect");
            }

        }).on("response", new Emitter.Listener() {

            @Override
            public void call(Object... args) {}

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.d("Socket", "disconnect");
            }

        });

        mButton = findViewById(R.id.recordButton);
        mButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                if (modeFlag == 0) {
                    socket.connect();
                    mButton.setText("Recording");
                } else {
                    mButton.setText("Ready");
                    modeFlag = 0;
                    finish();
                    System.exit(0);
                }
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mLinear = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mRotate = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mGyro, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mLinear, SensorManager.SENSOR_DELAY_FASTEST);

        mSensorManager.registerListener(this, mRotate, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        if (modeFlag == 1) {
            if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
                socket.emit("request",
                        Integer.toString(event.sensor.getType()),
                        event.timestamp / 1000000.0,
                        Float.toString(event.values[0]),
                        Float.toString(event.values[1]),
                        Float.toString(event.values[2]));
            }
            else
            {
                updateOrientation(event.values);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        socket.disconnect();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updateOrientation(float[] rotationVector) {
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);

        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);

        float pitch = (float) Math.toDegrees(orientation[1]);

        if (refAngle > 180) {
            refAngle = pitch;
            preRefAngle = refAngle;
        } else {
            if (Math.abs(preRefAngle - pitch) > 20) {
                if (pitch - preRefAngle < 0){
                    count = count - 1;
                    socket.emit("tilt", -1);
                } else {
                    count = count + 1;
                    socket.emit("tilt", 1);
                }
                preRefAngle = pitch;
                vibrator.vibrate(100);
            }
        }
    }

}
