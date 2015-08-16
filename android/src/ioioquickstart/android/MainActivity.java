package ioioquickstart.android;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import net.openspatial.ButtonEvent;
import net.openspatial.OpenSpatialEvent;
import net.openspatial.OpenSpatialException;
import net.openspatial.OpenSpatialService;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.AbstractIOIOActivity;
import ioioquickstart.android.simpledigitaloutput.R;


public class MainActivity extends AbstractIOIOActivity implements DialogInterface.OnClickListener, View.OnClickListener {

    private final int LED1_PIN = 34;
    private final int PWM1_PIN = 13;
    private Button mLed1Button;
    private Button gpsStateButton;
    private Button initializeButton;
    private TextView Pwm1Text;
    private boolean mLed1State = false;
    private boolean gpsState = true;
    private boolean GPSstateCheck = true;
    private boolean isForward = true;

    boolean attachIOIO = true; //SET TRUE IF OPENSKATE IS USING IOIO COM PROTOCOL;
    boolean attachRPi2 = false;  //SET TRUE IF OPENSKATE IS UGING RPI2 COM PROTOCOL;

    String eventTypeString;
    float unitSpeed = 0; //NORMALIZED SPEED ON A SCALE OF -100% to 100%
    float unitSpeedIncrement = 5; //INCREMENTATION OR DECREMENTATION STEP SIZE
    float maxSpeed = 100;
    float minSpeed = 0;

    float boardVelocity;
    float xNeutralPosition;
    float yNeutralPosition;
    float yNeutralMax;
    float yNeutralMin;
    float NeutralRange = 30;
    float yCurrentPosition;
    float yPositionMax;
    float yPositionMin;
    float dyPosition;
    float dyPositionMax = 150;

    int[] buttonArray;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice = null;

    // keep track of unit speed changes so we only update the displayed text if it changes
    private float previousUnitSpeed = -1;
    private float previousboardVelocity = -1;

    private Thread textThread;
    private TextView statusText;
    private TextView velocityText;
    LocationListener gpsVelocity;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)

    //RPi2 opens and transmits Bluetooth communications
    public void sendBtMsg(String msg2send) {
        if (attachRPi2) {
            UUID uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"); //Standard SerialPortService ID
            try {
                if (mmSocket == null) {
                    Log.e("OpenSkate", "null");
                    mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
                }
                if (!mmSocket.isConnected()) {
                    mmSocket.connect();
                }
                OutputStream mmOutputStream = mmSocket.getOutputStream();
                mmOutputStream.write(msg2send.getBytes());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //call GPS speed
    public class calcVelocity implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            // Called when a new location is found by the network location provider.
            if (gpsState) {
                if (GPSstateCheck) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            velocityText.setText("GPS Speed:\n" + "Connecting...");
                        }
                    });
                    toggleGPS();
                    GPSstateCheck = false;
                }
                boardVelocity = location.getSpeed();
            }
            if (!gpsState) {
                if (GPSstateCheck)
                    toggleGPS();
                GPSstateCheck = false;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }


    //update unit speed (variable being transmitted)
    //used for volume buttons, not used for finger sliding
    public void incrementSpeed() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        unitSpeed = unitSpeed + unitSpeedIncrement;
        if (unitSpeed > maxSpeed) {
            unitSpeed = maxSpeed;
        } else {
            v.vibrate(150);
        }
    }

    //see above
    public void decrementSpeed() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        unitSpeed = unitSpeed - unitSpeedIncrement;
        if (unitSpeed < minSpeed) {
            unitSpeed = minSpeed;
        } else {
            v.vibrate(150);
        }
    }

    //tie volume buttons to action
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (attachRPi2) {
                        sendBtMsg("HIGH");
                    }
                    incrementSpeed();
                    v.vibrate(150);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (attachRPi2) {
                        sendBtMsg("LOW");
                    }
                    decrementSpeed();
                    v.vibrate(150);
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    //ignore
    public static final String TAG = "NodTest";
    OpenSpatialService mOpenSpatialService;


    @TargetApi(Build.VERSION_CODES.ECLAIR)

    //Android
    //runs when app opened
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mLed1Button = (Button) findViewById(R.id.btn1);
        mLed1Button.setOnClickListener(this);

        gpsStateButton = (Button) findViewById(R.id.btn2);
        gpsStateButton.setOnClickListener(this);

        initializeButton = (Button) findViewById(R.id.start_up);
        initializeButton.setOnClickListener(this);

        Pwm1Text = (TextView) findViewById(R.id.text1);

        statusText = (TextView) findViewById(R.id.statusText);
        statusText.setText("Board Status:\nDisconnected");

        velocityText = (TextView) findViewById(R.id.velocityText);
        velocityText.setText("Start Moving");

        if (gpsState) {
            if (GPSstateCheck) {
                toggleGPS();
                GPSstateCheck = false;
            }
        }

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (attachRPi2) {
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals("OpenSkate")) {
                        Log.e("OpenSkate", device.getName());
                        mmDevice = device;
                        break;
                    }
                }
            }
        }

        //change text to new speed if speed changed
        textThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    if (unitSpeed != previousUnitSpeed || boardVelocity != previousboardVelocity) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (unitSpeed == 70) {
                                    Pwm1Text.setText("Throttle:\n" + 0 + "%");
                                } else if (isForward) {
                                    Pwm1Text.setText("Throttle:\n" + (int) ((unitSpeed - 73) / 27 * 100) + "%");
                                } else {
                                    Pwm1Text.setText("Throttle:\n" + (int) ((unitSpeed - 67) / 10 * 100) + "%");
                                }
                                velocityText.setText("GPS Speed:\n" + boardVelocity + "m/s");
                            }
                        });
                        previousUnitSpeed = unitSpeed;
                        previousboardVelocity = boardVelocity;
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        });
        textThread.start();

        //Keep screen on to make sure commands are always sent
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        bindService(new Intent(this, OpenSpatialService.class), mOpenSpatialServiceConnection, BIND_AUTO_CREATE); //nod

//        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//        gpsVelocity = new calcVelocity();
//        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsVelocity);
    }

    //turns GPS on/off
    public void toggleGPS() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        gpsVelocity = new calcVelocity();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsVelocity);
        Log.d("GPS", "gps tag1");
        if (!gpsState && GPSstateCheck) {
            locationManager.removeUpdates(gpsVelocity);
            Log.d("GPS", "gps tag2");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    velocityText.setText("GPS Speed:\n" + "GPS Off");
                }
            });
        }
    }


    public void initializeESC() throws InterruptedException {
        unitSpeed = 50;
       // wait(2000);
        unitSpeed = 70; //above 70 accelerates, 50-70 brakes, (45-95 usable)
    }

    @Override
    //Throttle method:
    //A "neutral zone" is established to make it easier for the user to find the neutral position
    //Touch down determines the center of the neutral position
    //Sliding finger beyond the bounds of the neutral zone adjust the duty cycle of the throttle
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                yNeutralPosition = event.getY(); //Neutral Position: Point on screen that corresponds to 0% duty cycle
                yNeutralMax = yNeutralPosition - NeutralRange; //Establishes upper limit of the neutral zone
                yNeutralMin = yNeutralPosition + NeutralRange; //Establishes lower limit of the neutral zone
                yPositionMax = yNeutralMax - dyPositionMax; //Establishes where 100% duty cycle is on the screen (this is relative to the yNeutralMax)
                yPositionMin = yNeutralMin + dyPositionMax; //Establishes where -100% duty cycle is on the screen
                break;
            case MotionEvent.ACTION_MOVE:
                yCurrentPosition = event.getY(); //current position of finger
                dyPosition = yNeutralPosition - yCurrentPosition;
                Log.d("yPosition", String.valueOf(yCurrentPosition));
                Log.d("dY Position", String.valueOf(dyPosition));
                if (yCurrentPosition <= yNeutralMax && yCurrentPosition >= yPositionMax) {
                    unitSpeed = ((dyPosition - NeutralRange) / (dyPositionMax * 100/27)) * maxSpeed + 73;
                    isForward = true;
                }
                if (yCurrentPosition >= yNeutralMin && yCurrentPosition <= yPositionMin) {
                    unitSpeed = ((dyPosition + NeutralRange) / (dyPositionMax * 100/10)) * maxSpeed + 67 ;
                    isForward = false;
                }
                Log.d("unitSpeed", String.valueOf(unitSpeed));
                break;
            case MotionEvent.ACTION_UP:
                //Reset the throttle to zero when touch is released
                //Throttle "springing" back to zero
                unitSpeed = 70;
                break;
        }

        return true;
    }

    //android, app quit
    @Override
    public void onDestroy() {
        super.onDestroy();

        textThread.interrupt();
        try {
            textThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        unbindService(mOpenSpatialServiceConnection);
    }

    //nod
    private ServiceConnection mOpenSpatialServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mOpenSpatialService = ((OpenSpatialService.OpenSpatialServiceBinder) service).getService();
            mOpenSpatialService.initialize(TAG, new OpenSpatialService.OpenSpatialServiceCallback() {
                @Override
                public void deviceConnected(final BluetoothDevice bluetoothDevice) {

                    try {
                        mOpenSpatialService.registerForButtonEvents(bluetoothDevice, new OpenSpatialEvent.EventListener() {
                            @Override
                            public void onEventReceived(OpenSpatialEvent openSpatialEvent) {
                                buttonArray = new int[10];
                                ButtonEvent event = (ButtonEvent) openSpatialEvent;
//                                Log.d(TAG, event.buttonEventType.toString());
                                eventTypeString = event.buttonEventType.toString();
                                if (attachIOIO) {
                                    if (eventTypeString.equals("TACTILE0_DOWN")) {
                                        //IOIO METHOD CALLS GO HERE TO INCREMENT THE SPEED UPON A TACTILE0 BUTTON PRESS
                                        incrementSpeed();
                                    }
                                    if (eventTypeString.equals("TACTILE1_DOWN")) {
                                        //IOIO METHOD CALLS GO HERE TO DECREMENT THE SPEED UPON A TACTILE1 BUTTON PRESS
                                        decrementSpeed();
                                    }
                                    if (eventTypeString.equals("TOUCH0_DOWN")) {
//                                        setNewPWM(unitSpeed);
                                    }
                                    if (eventTypeString.equals("TOUCH0_UP")) {
//                                        setNewPWM(minSpeed); //THIS IS A SAFETY TO MAKE SURE THERE IS NO UNINTENTIONAL ACCELERATION
                                    }
                                }
                                if (attachRPi2) {
                                    //METHOD CALL FOR SENDING BT MSG TO RPI
                                    sendBtMsg(eventTypeString);
                                }

                            }
                        });
                    } catch (OpenSpatialException e) {
                        Log.e(TAG, "Could not register for Button Press event" + e);
                    }
                }


                @Override
                public void deviceDisconnected(BluetoothDevice bluetoothDevice) {

                }

                @Override
                public void buttonEventRegistrationResult(BluetoothDevice bluetoothDevice, int i) {

                }

                @Override
                public void pointerEventRegistrationResult(BluetoothDevice bluetoothDevice, int i) {

                }

                @Override
                public void pose6DEventRegistrationResult(BluetoothDevice bluetoothDevice, int i) {

                }

                @Override
                public void gestureEventRegistrationResult(BluetoothDevice bluetoothDevice, int i) {

                }

                @Override
                public void motion6DEventRegistrationResult(BluetoothDevice bluetoothDevice, int i) {

                }
            });

            mOpenSpatialService.getConnectedDevices();
        }


        @Override
        public void onServiceDisconnected(ComponentName name) {
            mOpenSpatialService = null;
        }
    };

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {

    }


    class IOIOThread extends AbstractIOIOActivity.IOIOThread {
        private DigitalOutput mLed1;
        private PwmOutput mPwm1;

        //runs on connection with IOIO
        @Override
        protected void setup() throws ConnectionLostException {
            mLed1 = ioio_.openDigitalOutput(LED1_PIN, false); //to turn lights on
//            mPwm1 = ioio_.openPwmOutput(new DigitalOutput.Spec(PWM1_PIN, DigitalOutput.Spec.Mode.OPEN_DRAIN), 500);
            mPwm1 = ioio_.openPwmOutput(PWM1_PIN, 500); //initialize pwm pin, 500 frequency
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusText.setText("Board Status:\n" +
                            "Connected");

                }
            });
            // make sure to runOnUiThread
        }

        //same as arduino loop
        @Override
        protected void loop() throws ConnectionLostException {
            mLed1.write(mLed1State);
//            if (unitSpeed >= 0) {
////                mPwm1.setPulseWidth(1700); //This puts the board in drive
//                mPwm1 = ioio_.openPwmOutput(new DigitalOutput.Spec(PWM1_PIN, DigitalOutput.Spec.Mode.OPEN_DRAIN), 588);
//
//            } else {
////                mPwm1.setPulseWidth(1000); //This puts the board in reverse
//                mPwm1 = ioio_.openPwmOutput(new DigitalOutput.Spec(PWM1_PIN, DigitalOutput.Spec.Mode.OPEN_DRAIN), 1000);
//
//            }
            mPwm1.setDutyCycle(Math.abs(unitSpeed / 100)); //Sets the boards duty cycle
            //Log.d("PWM", String.valueOf(Math.abs(unitSpeed / 100)));

            try {
                sleep(100);
            } catch (InterruptedException e) {
                // silently ignore
            }
        }

        //runs on disconnected
        @Override
        protected void disconnected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusText.setText("Board Status:\n" +
                            "Disconnected");
                    previousUnitSpeed = -1;
                    unitSpeed = 0;
                }
            });
        }
    }

    @Override
    protected AbstractIOIOActivity.IOIOThread createIOIOThread() {
        return new IOIOThread();
    }

    //handles buttons
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn1: //LED
                if (mLed1State) {
                    mLed1State = false;
                    mLed1Button.setText("Power Off");
                } else {
                    mLed1State = true;
                    mLed1Button.setText("Power On");
                }
                break;
            case R.id.btn2: //GPS
                if (gpsState) {
                    gpsState = false;
                    GPSstateCheck = true;
                    gpsStateButton.setText("GPS On");

                } else {
                    gpsState = true;
                    GPSstateCheck = true;
                    gpsStateButton.setText("GPS Off");
                }
            case R.id.start_up:
                try {
                    initializeESC();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            default:
                break;
        }
    }
}