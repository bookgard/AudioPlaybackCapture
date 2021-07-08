package com.bjnet.android10;


import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MediaCaptureService extends Service {
    public static final String ACTION_ALL="ALL";
    public static final String ACTION_START="ACTION_START";
    public static final String ACTION_STOP="ACTION_STOP";
    public static final String EXTRA_ACTION_NAME="ACTION_NAME";
    public static final String EXTRA_RESULT_CODE="EXTRA_RESULT_CODE";

    private static final int RECORDER_SAMPLERATE=44100;
    private static final int RECORDER_CHANNELS= AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;


    int BufferElement2Rec=1024;
    int BytesPerElement=2;

    AudioRecord recorder;
    AudioRecord recorderMic;
    private boolean isRecording=false;

    NotificationCompat.Builder notificationBuilder;
    NotificationManager notificationManager;
    private String NOTIFICATION_CHANNEL_ID ="ChannelId";
    private String NOTIFICATION_CHANNEL_NAME="Channel";
    private String NOTIFICATION_CHANNEL_DESC="ChannelDescription";
    private int NOTIFICATION_ID=1000;
    private static final String ONGING_NOTIFICATION_TICKER = "RecorderApp";

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;

    Intent callingIntent;

    public void MediaCaptureService(){

    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            Intent notification=new Intent(this,MediaCaptureService.class);
            PendingIntent pendingIntent=PendingIntent.getActivity(this,0,notification,0);
            notificationBuilder=new NotificationCompat.Builder(this,NOTIFICATION_CHANNEL_ID)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.drawable.ic_launcher_foreground))
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Starting Service")
                    .setContentText("Starting monitoring service")
                    .setTicker(ONGING_NOTIFICATION_TICKER)
                    .setContentIntent(pendingIntent);
            Notification notification1= notificationBuilder.build();
            NotificationChannel channel=new NotificationChannel(NOTIFICATION_CHANNEL_ID,NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(NOTIFICATION_CHANNEL_DESC);
            notificationManager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
            startForeground(NOTIFICATION_ID,notification1);
        }
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
            mediaProjectionManager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        }
    }
    public int onStartCommand(Intent intent,int flags,int startId){
        callingIntent=intent;

        IntentFilter filter=new IntentFilter();
        filter.addAction(ACTION_ALL);
        registerReceiver(actionReceiver,filter);
        return START_STICKY;
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startRecording(Intent intent){
        mediaProjection=mediaProjectionManager.getMediaProjection(-1,intent);
        startRecording(mediaProjection);
    }
    @TargetApi(29)
    private void startRecording(MediaProjection mediaProjection){
        AudioPlaybackCaptureConfiguration config=
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build();
        AudioFormat audioFormat=new AudioFormat.Builder()
                .setEncoding(RECORDER_AUDIO_ENCODING)
                .setSampleRate(RECORDER_SAMPLERATE)
                .setChannelMask(RECORDER_CHANNELS)
                .build();
        recorder =new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(BufferElement2Rec * BytesPerElement)
                .setAudioPlaybackCaptureConfig(config)
                .build();

        isRecording=true;
        recorder.startRecording();
        if(recorderMic!=null){
            recorderMic.startRecording();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                writeAudioDataToFile();
                if(recorderMic!=null){
                    writeAudioDataToFileMic();
                }
            }
        }).start();

    }
    private byte[] short2byte(short[] sData){
        int shortArrsize=sData.length;
        byte[] bytes=new byte[shortArrsize*2];
        for(int i=0;i<shortArrsize;i++){
            bytes[i*2]=(byte)(sData[i]&0x00FF);
            bytes[(i*2)+1]=(byte)(sData[i]>>8);
            sData[i]=0;
        }
        return bytes;
    }
    private void writeAudioDataToFileMic(){
        if(recorderMic==null) return;
        Log.i(MainActivity.TAG,"Recording started.Computing output file name");
        File sampleDir=new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),"TestRecordingDasa1Mic");
        if(!sampleDir.exists()){
            sampleDir.mkdirs();
        }
        String fileName="Record-"+new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss").format(new Date())+".pcm";
        String filePath=sampleDir.getAbsolutePath()+"/"+fileName;
        short sData[]=new short[BufferElement2Rec];
        FileOutputStream os=null;
        try {
            os=new FileOutputStream(filePath);
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }
        while (isRecording){
            recorderMic.read(sData,0,BufferElement2Rec);
            Log.i(MainActivity.TAG,"Short wirting to file"+sData.toString());
            try{
                byte bData[]=short2byte(sData);
                os.write(bData,0,BufferElement2Rec*BytesPerElement);
            }catch (IOException e){
                e.printStackTrace();
                Log.i(MainActivity.TAG,"record error:"+e.getMessage());
            }
        }
        try{
            os.close();
        }catch (IOException e){
            e.printStackTrace();
        }
        Log.i(MainActivity.TAG,String.format("Recording finished.File saved to '%s",filePath));
    }
    private void writeAudioDataToFile(){
        if(recorder==null) return;
        Log.i(MainActivity.TAG,"Recording started. Computing output file name");
        File sampleDir=new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),"test.pcm");
        if(!sampleDir.mkdirs()){
            Log.e(MainActivity.TAG,"Directory not created");
        }
        if(sampleDir.exists()){
            sampleDir.delete();
        }
       /* String fileName="Recrod-"+new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss").format(new Date())+".pcm";
        String filePath=sampleDir.getAbsolutePath()+"/"+fileName;*/
       String filePath=sampleDir.getAbsolutePath();
        short sData[]=new short[BufferElement2Rec];

        FileOutputStream os=null;
        try{
            os=new FileOutputStream(sampleDir);
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }
        while (isRecording){
            recorder.read(sData,0,BufferElement2Rec);
            Log.i(MainActivity.TAG,"Short wirting to file"+sData.toString());
            try{
                byte bData[]=short2byte(sData);
                os.write(bData,0,BufferElement2Rec*BytesPerElement);
            }catch (IOException e){
                e.printStackTrace();
                Log.i(MainActivity.TAG,"record error:"+e.getMessage());
            }
        }
        try{
            os.close();
        }catch (IOException e){
            e.printStackTrace();
        }
        Log.i(MainActivity.TAG,String.format("Recording finished.File saved to '%s'",filePath));
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            private void stopRecording(){
        if(null!=recorder){
            isRecording=false;
            recorder.stop();
            recorder.release();
            recorder=null;
        }
        if(null!=recorderMic){
            isRecording=false;
            recorderMic.stop();
            recorderMic.release();
            recorderMic=null;
        }
        mediaProjection.stop();

        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(actionReceiver);
    }

    BroadcastReceiver actionReceiver=new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(action.equalsIgnoreCase(ACTION_ALL)){
                String actionName=intent.getStringExtra(EXTRA_ACTION_NAME);
                if(actionName!=null&&!actionName.isEmpty()){
                    if(actionName.equalsIgnoreCase(ACTION_START)){
                        startRecording(callingIntent);
                    }else if(actionName.equalsIgnoreCase(ACTION_STOP)){
                        stopRecording();
                    }
                }
            }
        }
    };
}
