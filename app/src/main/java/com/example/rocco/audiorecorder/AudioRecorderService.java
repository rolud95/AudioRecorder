package com.example.rocco.audiorecorder;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class AudioRecorderService extends Service {


    public enum Actions {
        START, STOP, PAUSE, RESUME
    }

    private static final int NOTIFICATION_ID = 101;

    private MediaRecorder recorder;
    private boolean mAudioFocusGranted = false;  // true if the focus is granted
    private boolean mAudioRecording = false;     // true if the app is recording
    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener;
    private boolean mAudioRecorderPaused = false; // true if the recorder is paused

    // ----- THREAD
    HandlerThread handlerThread = new HandlerThread("BackgroundThread");
    Handler mHandler;


    @Override
    public IBinder onBind(Intent intent) {
        // do not provide binding
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i("SERVICE", "In onCreate");

        // ----- CREATE AUDIO FOCUS LISTENER
        mOnAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch(focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.i("AUDIOFOCUS", "AUDIOFOCUS_GAIN");
                        if (recorder == null) {
                            // start
                        } else {
                            handleResume();
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        Log.i("AUDIOFOCUS", "AUDIOFOCUS_GAIN_TRANSIENT");
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        Log.i("AUDIOFOCUS", "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.e("AUDIOFOCUS", "AUDIOFOCUS_LOSS");
                        handlePause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.e("AUDIOFOCUS", "AUDIOFOCUS_LOSS_TRANSIENT");
                        handlePause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        Log.e("AUDIOFOCUS", "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                        break;
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                        Log.e("AUDIOFOCUS", "AUDIOFOCUS_REQUEST_FAILED");
                        break;
                    default:
                        // ---
                    }
                }
            };

        // ----- CREATE CAMERA CALLBACK AVAILABILITY
        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        camManager.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
            @Override
            public void onCameraAvailable(@NonNull String cameraId) {
                super.onCameraAvailable(cameraId);
                Log.i("CAMERA", "Camera is available");
                // resume if recorder is paused
                handleResume(); // pause check is in method
            }

            @Override
            public void onCameraUnavailable(@NonNull String cameraId) {
                super.onCameraUnavailable(cameraId);
                Log.i("CAMERA", "Camera is not available");
                //pause the recorder
                handlePause();
            }
        }, null);

        // ----- start thread
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {

            }
        };
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("SERVICE", "In onStartCommand");
        if (intent != null) {
            final String action = intent.getAction();
            Log.i("AudioRecorderService", action);
            if (Actions.START.name().equals(action)) {
//                handleStart();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleStart();
                    }
                });
            } else if (Actions.STOP.name().equals(action)) {
//                handleStop();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleStop();
                    }
                });
            } else if (Actions.PAUSE.name().equals(action)) {
//                handlePause();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        handlePause();
                    }
                });
            } else if (Actions.RESUME.name().equals(action)) {
//                handleResume();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleResume();
                    }
                });
            }
        }

//        return START_STICKY;
        return START_REDELIVER_INTENT;
    }

    // ----- HANDLE AUDIO FOCUS
    private boolean requestAudioFocus() {
        if(!mAudioFocusGranted) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int result = am.requestAudioFocus(mOnAudioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocusGranted = true;
            } else {
                //FAILED
                Log.e("AUDIOFOCUS", "FAILED TO GET AUDIO FOCUS");
            }
        }
        return mAudioFocusGranted;
    }

    private void abandonAudioFocus() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = am.abandonAudioFocus(mOnAudioFocusChangeListener);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mAudioFocusGranted = false;
        } else {
            // FAILED
            Log.e("AUDIOFOCUS", "FAILED TO ABANDON AUDIO FOCUS");
        }
        mOnAudioFocusChangeListener = null;
    }


    // ----- HANDLE ACTION METHOD
    private void handleStart() {
//        String mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();  // save file on external storage
        String mFileName = this.getFilesDir().getAbsolutePath();  // save file on internal storage
        Log.i("START", "file dir: " + mFileName);
//        String[] fs = this.getFilesDir().list().clone();
//        for (int i=0; i < fs.length; i++) {
//            Log.i("START", ""+fs[i]);
//        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
        mFileName += "/audiorecordtest_" + formatter.format(new Date()) + ".mp3";

        if (recorder == null) {
            recorder = new MediaRecorder();

            // set up
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                recorder.setOutputFile(mFileName);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            } catch (IllegalStateException e) {
                Log.e("START", "Setting resource failed: " + e);

            }

            // acquire audio focus
            if (!mAudioFocusGranted && requestAudioFocus()) {
                // audio focus granted
                Log.i("SERVICE", "AUDIO FOCUS GRANTED.");


                // start
                try {
                    recorder.prepare();   // inizializza
                    recorder.start();
                    Log.i("RECORDER", "The recorder is started.");
                    Toast.makeText(this, "Recorder Started", Toast.LENGTH_SHORT).show();

                    mAudioRecording = true;

                } catch (IllegalStateException e) {
                    // mic is unavaible
                    Log.e("Recorder", "recorder.start() failed, mic is unavaible: " + e);
                    recorder.release();
                    recorder = null;
                } catch (IOException e) {
                    Log.e("Recorder", "Prepare fails.");
                }


            }

            // ------ START FOREGROUND SERVICE
//            Intent notificationIntent = new Intent(this, AudioRecorder.class);
//            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
//
//            Notification notification = new Notification.Builder(this)
//                    .setContentTitle("AudioRecorder")
//                    .setContentText("AudioRecorder")
//                    .setTicker("AudioRecorder")
//                    .setContentIntent(pendingIntent)
//                    .setOngoing(true)
//                    .build();
//
//            startForeground(NOTIFICATION_ID, notification);
        }


    }

    private void handleStop() {
        if (mAudioRecording) {
            try {
                recorder.stop();
                recorder.reset();
            } catch (IllegalStateException e) {
                Log.e("Recorder", "Stop/reset failed: " + e);
            }
            recorder.release();
            recorder = null;
            Log.i("RECORDER", "The recorder is stopped.");
            Toast.makeText(this, "Recorder Stopped", Toast.LENGTH_SHORT).show();

            mAudioRecording = false;
            abandonAudioFocus();

//            stopForeground(true); // stop foreground service
            stopSelf();
        }
    }

    private void handlePause() {
        if (mAudioRecording) {
            recorder.pause();
            mAudioRecorderPaused = true;
            Log.i("RECORDER", "The recorder is paused.");
            Toast.makeText(this, "Recorder Paused", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleResume() {
        if (mAudioRecording && mAudioRecorderPaused) {
            recorder.resume();
            mAudioRecorderPaused = false;
            Log.i("RECORDER", "The recorder is resumed.");
            Toast.makeText(this, "Recorder Resumed", Toast.LENGTH_SHORT).show();
        }
    }


    // ----- STATIC ACTION METHOD
    public static void startRecording(Context context) {
        Intent intent = new Intent(context, AudioRecorderService.class);
        intent.setAction(Actions.START.name());
        context.startService(intent);
    }

    public static void stopRecording(Context context) {
        Intent intent = new Intent(context, AudioRecorderService.class);
        intent.setAction(Actions.STOP.name());
        //context.stopService(intent);
        context.startService(intent);
    }

    public static void pauseRecording(Context context) {
        Intent intent = new Intent(context, AudioRecorderService.class);
        intent.setAction(Actions.PAUSE.name());
        context.startService(intent);
    }

    public static void resumeRecording(Context context) {
        Intent intent = new Intent(context, AudioRecorderService.class);
        intent.setAction(Actions.RESUME .name());
        context.startService(intent);
    }

}
