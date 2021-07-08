package com.bjnet.android10;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String TAG="CALL_FUNCTION_TEST";
    private Button btnInitCapture;
    private Button btnStartCapture;
    private Button btnStopCapture;
    private Button btnGetOkPermissions;
    private Button btnplay;
    private MediaProjectionManager manager;
    private static final int ALL_PERMISSIONS_PERMISSION_CODE=1000;
    private static final int CREATE_SCREE_CAPTURE=1001;


    private AudioTrack audioTrack;
    private FileInputStream fileInputStream;

    private String[] appPermission={
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE
    };
/*
* 检查权限获取
*
* */
    private boolean checkAndRequestPermissions(){
        List<String> listPermissionsNeeded=new ArrayList<>();
        for(String permission:appPermission){
            if(ContextCompat.checkSelfPermission(this,permission)!= PackageManager.PERMISSION_GRANTED){
               listPermissionsNeeded.add(permission);
            }
        }
        if(!listPermissionsNeeded.isEmpty()){
            ActivityCompat.requestPermissions(this,listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),ALL_PERMISSIONS_PERMISSION_CODE);
        return false;
        }
        return true;
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnGetOkPermissions=findViewById(R.id.btnGetOkPermissions);
        btnGetOkPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkAndRequestPermissions();
            }
        });
        btnInitCapture=findViewById(R.id.btnInitCapture);
        btnInitCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initAudioCapture();
            }
        });
        btnStartCapture =findViewById(R.id.btnStartCapture);
        btnStartCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRecording();
            }
        });
        btnStopCapture =findViewById(R.id.btnStopAudioCapture);
        btnStopCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopRecording();
            }
        });
        btnplay=findViewById(R.id.btnplay);
        btnplay.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View view) {
                playInModeStream();
            }
        });
    }
    /*
    * 播放，使用stream模式
    * */

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void playInModeStream(){
        int channelConfig= AudioFormat.CHANNEL_OUT_MONO;
        final int minBufferSize = AudioTrack.getMinBufferSize(44100,channelConfig,AudioFormat.ENCODING_PCM_16BIT);
        audioTrack=new AudioTrack(
                new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
                new AudioFormat.Builder().setSampleRate(44100)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(channelConfig)
                .build(),
                minBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );
        audioTrack.play();

        File file=new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),"test.pcm");

        try{
            fileInputStream=new FileInputStream(file);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        byte[] tempBuffer=new byte[minBufferSize];
                        while(fileInputStream.available()>0){
                            int readCount=fileInputStream.read(tempBuffer);
                            if(readCount==AudioTrack.ERROR_BAD_VALUE){
                                continue;
                            }
                            if(readCount!=0&& readCount!=-1){
                                audioTrack.write(tempBuffer,0,readCount);
                            }
                        }
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }).start();
        }catch (IOException e){
            e.printStackTrace();
        }

    }
    /*
    * 捕获音频权限
    *
    * */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initAudioCapture(){
        manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent intent=manager.createScreenCaptureIntent();
        startActivityForResult(intent,CREATE_SCREE_CAPTURE);
    }
    /*
    *
    * 停止录制
    * */
    private void stopRecording(){
        Intent broadCastIntent=new Intent();
        broadCastIntent.setAction(MediaCaptureService.ACTION_ALL);
        broadCastIntent.putExtra(MediaCaptureService.EXTRA_ACTION_NAME,MediaCaptureService.ACTION_STOP);
        this.sendBroadcast(broadCastIntent);
    }
    /*

    * 开始录制
    * */
    private void startRecording(){
        Intent broadCastIntent =new Intent();
        broadCastIntent.setAction(MediaCaptureService.ACTION_ALL);
        broadCastIntent.putExtra(MediaCaptureService.EXTRA_ACTION_NAME,MediaCaptureService.ACTION_START);
        this.sendBroadcast(broadCastIntent);

    }
    /*
    * 处理权限未授权回调
    * */

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode==ALL_PERMISSIONS_PERMISSION_CODE){
            HashMap<String,Integer> permissionResults=new HashMap<>();
            int deniedCount=0;
            for(int permissionIndx=0;permissionIndx<permissions.length;permissionIndx++){
                if(grantResults[permissionIndx]!=PackageManager.PERMISSION_GRANTED){
                    permissionResults.put(permissions[permissionIndx],grantResults[permissionIndx]);
                    deniedCount++;
                }
            }
            if(deniedCount==0){

            }else {
                Log.e(TAG,"Permission Denied! Now you must allow permission for settings.");
            }
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(CREATE_SCREE_CAPTURE==requestCode){
            if(resultCode==RESULT_OK){
                Intent i=new Intent(this,MediaCaptureService.class);
                i.setAction(MediaCaptureService.ACTION_START);
                i.putExtra(MediaCaptureService.EXTRA_RESULT_CODE,resultCode);
                i.putExtras(data);
                this.startService(i);
            }else{

            }
        }
    }
}
