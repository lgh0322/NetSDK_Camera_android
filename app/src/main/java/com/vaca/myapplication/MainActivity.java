package com.vaca.myapplication;

import android.Manifest;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.acodeclib.ADDefines;
import com.acodeclib.ADecodeChannel;
import com.acodeclib.ADecoderMng;
import com.acodeclib.ADecoderParam;
import com.acodeclib.Mp4Muxer;
import com.android.audio.AudioBuffer;
import com.android.audio.AudioPlayMng;
import com.android.audio.AudioRecordMng;
import com.xyuvshowlib.XYuvShowAgent;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import ipc.android.sdk.audio.AudioSender;
import ipc.android.sdk.audio.AudioSendersMng;
import ipc.android.sdk.com.FRAME_EXTDATA;
import ipc.android.sdk.com.NetSDK_AUDIO_PARAM_EXTEND;
import ipc.android.sdk.com.NetSDK_IPC_ENTRY;
import ipc.android.sdk.com.NetSDK_Media_Video_Config;
import ipc.android.sdk.com.NetSDK_MotionDetectAlarm;
import ipc.android.sdk.com.NetSDK_PersonDetectAlarm;
import ipc.android.sdk.com.NetSDK_RecordConfig;
import ipc.android.sdk.com.NetSDK_RecordList;
import ipc.android.sdk.com.NetSDK_SysControlString;
import ipc.android.sdk.com.NetSDK_SystemVersionInfo;
import ipc.android.sdk.com.NetSDK_USER_VIDEOINFO;
import ipc.android.sdk.com.NetSDK_WifiApInfos;
import ipc.android.sdk.com.NetSDK_Wifi_Config;
import ipc.android.sdk.com.NetSatatEvent;
import ipc.android.sdk.impl.Defines;
import ipc.android.sdk.impl.FunclibAgent;
import ipc.android.sdk.impl.SearchIPCEngine;

/**
 * [cn]
 * NetSDK及附属库使用示例。本库将功能分为几大块，均用单例模式的管理类提供接口
 *
 * 带下标的变量，0、1、2、3...分别对应播放通道索引
 * lUser：登录时返回的唯一用户句柄
 * lRealHandle：取流时返回的句柄
 * decHandle：对应播放通道的解码句柄，由视频与音频解码通道构成
 *
 * [en]
 * for quick test, just click "begin search" first, then "ipList" will fill with device' ip, then ,click first channel login and play button.
 *
 * some rules for this demo:
 * suffix(0、1、2、3... ) are multi play routines,respectively.
 * lUser: Unique handle when login.
 * lRealHandle: Unique handle when try to get video or audio data from camera
 * decHandle:Unique handle when decode video or audio
 *
 * this library was separated into several manager class and all coded with "Singleton Pattern"
 * brief usage:
 * +++++++ 1: Class SearchIPCEngine ++++++++
 * this class is used for searching ipc in Local network.
 *
 * code template:
 * SearchIPCEngine.initSearchEngine(this);//don't forget this.
 * searchIPCEngine.setListener(listener);
 * searchIPCEngine.startSearch();//when call this, you can get IP Camera's info in Local network from the listener.
 * searchIPCEngine.stopSearch();
 * searchIPCEngine.release();//only call when exit.
 *
 * +++++++ 2: Class XYuvShowAgent ++++++++
 * this class is used to rendering yuv data from video decoder to ui.
 *
 * code template:
 * XYuvShowAgent xYuvShowAgent = XYuvShowAgent.getInstance();
 * int showChn0 = xYuvShowAgent.CreateChannel(View);//bind with surface
 * xYuvShowAgent.StartRender(showChn0);
 * xYuvShowAgent.Render(showChn0, data, size, width, height, 1, 0, 0);//loop.., render picture
 * xYuvShowAgent.StopRender(showChn0);// when stop play please call this func, it will clear image which still remain on ui.
 * xYuvShowAgent.release();
 *
 * +++++++ 3:Class FunclibAgent ++++++++
 * this class is used for interaction between app and ip camera. get or modify ipc param, upgrade or some other functions.
 * some of the functions are asynchronous, and can be get result from callback or just timeout when network is not accessable.
 *
 * code template:
 * FunclibAgent funclibAgent = FunclibAgent.getInstance();
 * funclibAgent.setIDirectConnectCB(callBackProcess);//
 * ...(call more FunclibAgent's func)
 * funclibAgent.release();
 *
 * +++++++ 4: Class ADecoderMng ++++++++
 * this class is used for decode video and audio data(AAC or g711 ulaw format to pcm, h264/h265 to yuv420p)
 *
 * code template:
 * ADecoderMng aDecoderMng = ADecoderMng.getInstance();
 * aDecoderMng.setDecDataCBListener(callBackProcess);// all decoded data can be get from this callback
 * decHandle0 = aDecoderMng.getNewHandle();//note that, each decode handle manage a pair of decode routine(for video and audio),so when recv params you can init them as follow
 * aDecoderMng.CreateDecodeChannel(decHandle0, ffmpeg, video_codecType, video_param, -1);
 * aDecoderMng.CreateDecodeChannel(decHandle0, 0, audio_codecType, audio_param, -1);
 * //then when recv audio or video data, just fill the data to aDecoderMng with the unique dec handle.
 * aDecoderMng.InputDataBuf(...)// loop
 * aDecoderMng.release()
 *
 * +++++++ 5: Class  AudioPlayMng ++++++++
 * this class is used for playing pcm which was decoded from ADecoderMng.
 *
 * code template:
 * AudioPlayMng audioPlayMng = AudioPlayMng.getInstance();
 * audioPlayMng.StartPlay(ToPlayAudioParam.getSamplerate(), channelConfig, audioFormat);//init the audio.
 * //note that, it can only play one routing audio data now. when change to play other one, just stop and StartPlay again.
 * audioPlayMng.AddPcmDataToPlay(buffer);//loop
 * audioPlayMng.StopPlay();
 * audioPlayMng.release();
 *
 * +++++++ 6: Class AudioRecordMng ++++++++
 * this class is used for record audio from android.
 *
 * code template:
 * AudioRecordMng audioRecordMng = AudioRecordMng.getInstance();
 * audioRecordMng.setAudioEncodedDataCBListener(callBack);
 * audioRecordMng.StartRecord(...)//then audio data(aac or g711u format) can be get from callback.
 * audioRecordMng.StopRecord();
 * audioRecordMng.release();
 *
 * +++++++ 7: Class AudioSendersMng ++++++++
 * this class is used for app to talk with ip camera, it can send encoded audio data from AudioRecordMng.
 * note that IP camera now can only recv audio data with g711u or aac format, and this data's params(sample rate and channel num) should be the same with device's settings.
 *
 * code template:
 * AudioSendersMng audioSendersMng = AudioSendersMng.getInstance();
 * audioSendersMng.AddSender(..)// who should I send audio data to
 * audioSendersMng.RemoveSender(..)
 * audioSendersMng.release();
 *
 * ====================
 * relationship of manager classes :
 * -------------
 * IP Camera video data --->  FunclibAgent receive them --->  ADecoderMng decode them --->  XYuvShowAgent render video
 * IP Camera audio data --->                            --->                          --->  AudioPlayMng  play  audio
 * -------------
 * AudioRecordMng record audio --->  AudioSendersMng send them to IP Camera
 * -------------
 *
 * at last, sorry for the mess demo ╮( ￣▽ ￣)╭
 * any more question? contact us.
 */

public class MainActivity extends Activity implements View.OnClickListener{
    private static String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1001;

    List<String> ipList = new ArrayList<>();

    Button btn_Search_Start;
    Button btn_Search_Stop;
    TextView tv_ipc_count;
    Button btn_exit;
    Button btn_modify_ip;
    Button btn_LoginDev0;
    Button btn_RealPlay0;
    Button btn_GetDevConfig0;
    Button btn_Set_bright_normal;
    Button btn_Set_bright_highest;
    Button btn_Set_volume_full;
    Button btn_Set_volume_half;
    Button btn_upload_config;
    Button btn_upgrade0;
    Button btn_restore_config0;
    Button btn_reboot0;
    Button btn_sound0;
    Button btn_talk0;
    Button btn_RealPlay1;
    Button btn_StopPlay1;
    Button btn_Record_start1;
    Button btn_Record_stop1;
    Button btn_PTZ_Ctrl;

    SearchIPCEngine searchIPCEngine = SearchIPCEngine.getInstance();
    ADecoderMng aDecoderMng = null;//will load local library
    XYuvShowAgent xYuvShowAgent = null;
    FunclibAgent funclibAgent = null;
    AudioPlayMng audioPlayMng = null;
    AudioRecordMng audioRecordMng = null;
    AudioSendersMng audioSendersMng = null;

    CallBackProcess callBackProcess = new CallBackProcess();//回调。

    long lUser0 = 0;
    long lRealHandle0 = 0;
    long lUser1 = 0;
    long lRealHandle1 = 0;

    int decHandle0 = -1;
    int decHandle1 = -1;
    //
    SurfaceView mView0;
    SurfaceView mView1;

    long audioPlayRealHandle = -1;
    int audioPlayDecHandle = -1;
    long talkLUser = -1;//current talk user
    ADecoderParam.VideoParam videoParam0;
    ADecoderParam.AudioParam ToPlayAudioParam;
    ADecoderParam ToRecordParam;

    int renderChnIndex0 = -1;//create by xYuvShowAgent
    int renderChnIndex1 = -1;

    int play_stream_no = 0;//0、main stream， 1、sub stream. Our device can get low and high resolution stream.Some low resource mobile may not work well with main stream.

    NetSDK_Media_Video_Config m_video_config0 = null;
    NetSDK_AUDIO_PARAM_EXTEND m_audio_config0 = null;

    NetSDK_IPC_ENTRY ToModifyIPC = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try{
            getActionBar().hide();
            getActionBar().setTitle("android NetSDK sample");
        }catch (Exception e)
        {
            e.printStackTrace();
        }

        searchIPCEngine = SearchIPCEngine.getInstance();
        aDecoderMng = ADecoderMng.getInstance();
        xYuvShowAgent = XYuvShowAgent.getInstance();
        funclibAgent = FunclibAgent.getInstance();
        audioPlayMng = AudioPlayMng.getInstance();
        audioRecordMng = AudioRecordMng.getInstance();
        audioSendersMng = AudioSendersMng.getInstance();

        btn_Search_Start = (Button)findViewById(R.id.btn_Search_Start);
        btn_Search_Stop = (Button)findViewById(R.id.btn_Search_Stop);
        tv_ipc_count = (TextView)findViewById(R.id.tv_ipc_count);
        btn_exit = (Button)findViewById(R.id.btn_exit);
        btn_modify_ip = (Button)findViewById(R.id.btn_modify_ip);
        btn_LoginDev0 = (Button)findViewById(R.id.btn_LoginDev);
        btn_RealPlay0 = (Button)findViewById(R.id.btn_RealPlay);
        btn_GetDevConfig0 = (Button)findViewById(R.id.btn_GetDevConfig);
        btn_Set_bright_normal = (Button)findViewById(R.id.btn_Set_bright_normal);
        btn_Set_bright_highest = (Button)findViewById(R.id.btn_Set_bright_highest);
        btn_Set_volume_full = (Button)findViewById(R.id.btn_Set_volume_full);
        btn_Set_volume_half = (Button)findViewById(R.id.btn_Set_volume_half);
        btn_upload_config = (Button)findViewById(R.id.btn_upload_config);
        btn_upgrade0 = (Button)findViewById(R.id.btn_upgrade);
        btn_restore_config0 = (Button)findViewById(R.id.btn_restore_config);
        btn_reboot0 = (Button)findViewById(R.id.btn_reboot);
        btn_RealPlay1 = (Button)findViewById(R.id.btn_RealPlay1);
        btn_StopPlay1 = (Button)findViewById(R.id.btn_StopPlay1);
        btn_Record_start1 = (Button)findViewById(R.id.btn_Record_start1);
        btn_Record_stop1 = (Button)findViewById(R.id.btn_Record_stop1);
        btn_PTZ_Ctrl = (Button)findViewById(R.id.btn_PTZ_Ctrl);
        mView0 = (SurfaceView)findViewById(R.id.surfaceView0) ;
        mView1 = (SurfaceView)findViewById(R.id.surfaceView1) ;
        btn_sound0 = (Button)findViewById(R.id.btn_sound) ;
        btn_talk0 = (Button)findViewById(R.id.btn_talk);

        renderChnIndex0 = xYuvShowAgent.CreateChannel(mView0);
        renderChnIndex1 = xYuvShowAgent.CreateChannel(mView1);

        xYuvShowAgent.SetClearColorRGB(renderChnIndex1, 240, 240, 240);

        btn_Search_Start.setOnClickListener(this);
        btn_Search_Stop.setOnClickListener(this);
        btn_exit.setOnClickListener(this);
        btn_modify_ip.setOnClickListener(this);
        btn_LoginDev0.setOnClickListener(this);
        btn_RealPlay0.setOnClickListener(this);
        btn_GetDevConfig0.setOnClickListener(this);
        btn_Set_bright_normal.setOnClickListener(this);
        btn_Set_bright_highest.setOnClickListener(this);
        btn_Set_volume_half.setOnClickListener(this);
        btn_upload_config.setOnClickListener(this);
        btn_Set_volume_full.setOnClickListener(this);
        btn_upgrade0.setOnClickListener(this);
        btn_restore_config0.setOnClickListener(this);
        btn_reboot0.setOnClickListener(this);
        btn_RealPlay1.setOnClickListener(this);
        btn_StopPlay1.setOnClickListener(this);
        btn_Record_start1.setOnClickListener(this);
        btn_Record_stop1.setOnClickListener(this);
        btn_PTZ_Ctrl.setOnClickListener(this);
        btn_sound0.setOnClickListener(this);
        btn_talk0.setOnClickListener(this);

        searchIPCEngine.setListener(callBackProcess);
        searchIPCEngine.initSearchEngine(this);//searchIPCEngine需要传入context，否则搜索功能不正常。
        funclibAgent.setIDirectConnectCB(callBackProcess);
        aDecoderMng.setDecDataCBListener(callBackProcess);
        audioRecordMng.setAudioEncodedDataCBListener(callBackProcess);

        aDecoderMng.setYuvDataBehavior(false);//if set this to false, yuv data will not come back from JNI. this can reduce a lot of memory usage.

        decHandle0 = aDecoderMng.getNewHandle();//
        decHandle1 = aDecoderMng.getNewHandle();

        mView0.setOnTouchListener(glViewTouchListener);

    }

    @Override
    protected void onStart(){
        super.onStart();
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
        int permission = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE,
                    1);
        }
    }

    /*  Test start (scale image at JNI, not works well, ignore this part now) */
    float scaleFactor = 1;
    int moveSpacingX = 0;
    int moveSpacingY = 0;

    int preMoveSpacingX = 0;
    int preMoveSpacingY = 0;
    float beginScaleFactor = 1;
    float beginScaleSpacing = 0;
    float beginMovePosX = 0;
    float beginMovePosY = 0;
    boolean isScaleIng = false;
    long FirstClickTime = 0;
    View.OnTouchListener glViewTouchListener = new View.OnTouchListener(){
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            //Log.d(TAG, "onTouch");
            int pointerCount = event.getPointerCount();
            Log.d(TAG, "ACTION_ pointerCount:" + pointerCount);
            float x = 0;
            float y = 0;
            switch (event.getAction() & MotionEvent.ACTION_MASK )
            {
                case MotionEvent.ACTION_DOWN:
                    Log.d(TAG, "ACTION_DOWN");

                    long now = System.currentTimeMillis();
                    if(now - FirstClickTime < 300){
                        //双击事件
                        scaleFactor = 1;
                        moveSpacingX = 0;
                        moveSpacingY = 0;
                        xYuvShowAgent.Update(renderChnIndex0, scaleFactor, moveSpacingX, moveSpacingY);
                    }
                    else
                    {
                        FirstClickTime = now;
                    }

                    beginMovePosX = event.getX(0);
                    beginMovePosY = event.getY(0);
                    preMoveSpacingX = moveSpacingX;
                    preMoveSpacingY = moveSpacingY;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    Log.d(TAG, "ACTION_MOVE");
                    if(pointerCount >= 2 )
                    {
                        if(!isScaleIng)
                        {
                            beginScaleFactor = scaleFactor;
                            x = event.getX(0)-event.getX(1);
                            y = event.getY(0)-event.getY(1);
                            beginScaleSpacing =(float)Math.sqrt(x*x + y*y);
                        }
                        isScaleIng = true;
                        x = event.getX(0)-event.getX(1);
                        y = event.getY(0)-event.getY(1);
                        float endSpacing = (float)Math.sqrt(x*x+y*y);
                        scaleFactor = beginScaleFactor + (endSpacing - beginScaleSpacing)/40 * (float)0.1;
                        if(scaleFactor < 1) scaleFactor = 1;

                        /*
                        TODO 针对某点缩放时，该点在屏幕上不动
                        x = (event.getX(0)+event.getX(1))/2 - mView0.getWidth()/2;
                        y = (event.getY(0)-event.getY(1))/2 - mView0.getHeight()/2;
                        moveSpacingX = (int)(x * scaleFactor) ;
                        moveSpacingY = (int)(y* scaleFactor);
                        */

                        xYuvShowAgent.Update(renderChnIndex0, scaleFactor, moveSpacingX, moveSpacingY);
                    }
                    else
                    {
                        if(!isScaleIng)//非缩放条件下的移动，避免与缩放的干扰
                        {
                            x = event.getX(0) - beginMovePosX;
                            y = event.getY(0) - beginMovePosY;

                            moveSpacingX = preMoveSpacingX + (int)(x * scaleFactor)*2;
                            moveSpacingY = preMoveSpacingY - (int)(y * scaleFactor)*2;//gl底层绘图是倒立的，这里用减

                            xYuvShowAgent.Update(renderChnIndex0, scaleFactor, moveSpacingX, moveSpacingY);
                            Log.d(TAG, "ACTION_ Update");
                        }
                    }
                    FirstClickTime = 0;
                    return true;

                case MotionEvent.ACTION_UP:
                    Log.d(TAG, "ACTION_UP");
                    try{
                        //检查图片四个角是否超出范围(进入屏幕)，回弹
                        float scaleW = mView0.getWidth()/(float)videoParam0.getWidth();
                        float scaleH = mView0.getHeight()/(float)videoParam0.getHeight();
                        float scale = Math.min(scaleW, scaleH);

                        float maxSpacingX = videoParam0.getWidth()*scale*scaleFactor/2;
                        float maxSpacingY = videoParam0.getHeight()*scale*scaleFactor/2;
                        int mvSpacingX = (moveSpacingX < 0 ? -moveSpacingX: moveSpacingX);
                        int mvSpacingY = (moveSpacingY < 0 ? -moveSpacingY: moveSpacingY);
                        Log.i(TAG, "scale:" + scale
                                + " mView0.width:" + mView0.getWidth() + " mView0.height:" + mView0.getHeight()
                                + " img.width:" + videoParam0.getWidth() + " img.height:" + videoParam0.getHeight()
                                + " maxSpacingX:" + maxSpacingX + " maxSpacingY:" + maxSpacingY
                                + " mvSpacingX:" + mvSpacingX + " mvSpacingY:" + mvSpacingY );
                        if(mvSpacingX > maxSpacingX || mvSpacingY > maxSpacingY)
                        {
//                            if(moveSpacingX < 0 ) maxSpacingX = -maxSpacingX;
//                            if(moveSpacingY < 0 ) maxSpacingY = -maxSpacingY;
                            final int maxSpacingX_f = (int)(moveSpacingX < 0 ? maxSpacingX : -maxSpacingX);
                            final int maxSpacingY_f = (int)(moveSpacingY < 0 ? maxSpacingY : -maxSpacingY);
                            final int startSpacingX = moveSpacingX;
                            final int startSpacingY = moveSpacingY;
                            //动画使moveSpacingX从startSpacingX变到maxSpacingX_f
                            Log.i(TAG, "Picture Out Of Range.");
                            ValueAnimator valueAnimator = new ValueAnimator();
                            valueAnimator.setDuration(1000);
                            valueAnimator.setObjectValues(new PointF(startSpacingX, startSpacingY),
                                    new PointF(maxSpacingX, maxSpacingY));
                            valueAnimator.setInterpolator(new LinearInterpolator());
                            valueAnimator.setEvaluator(new TypeEvaluator<PointF>()
                            {
                                // fraction = t / duration
                                @Override
                                public PointF evaluate(float fraction, PointF startValue,
                                                       PointF endValue)
                                {
                                    PointF point = new PointF();
                                    point.x = startSpacingX + fraction*(maxSpacingX_f - startSpacingX);
                                    point.y = startSpacingX + fraction*(maxSpacingY_f - startSpacingY);
                                    return point;
                                }
                            });

                            valueAnimator.start();
                            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
                            {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation)
                                {
                                    PointF point = (PointF) animation.getAnimatedValue();
                                    Log.i(TAG, "pos. x:" + point.x + " y" + point.y);

                                }
                            });
                        }

                    }catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    isScaleIng = false;
                    beginMovePosX = 0;
                    beginMovePosX = 0;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    Log.d(TAG, "ACTION_CANCEL");

                    return true;
            }
            return false;
        }
    };
    /*  Test end */

    @Override
    protected void onResume(){
        super.onResume();
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        switch (keyCode)
        {
            case KeyEvent.KEYCODE_BACK:
                exit_app();
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI|AudioManager.FLAG_PLAY_SOUND);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI|AudioManager.FLAG_PLAY_SOUND);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    boolean isSoundOpened = false;
    boolean isTalkOpened = false;
    static boolean isUpgrading = false;
    @Override
    public void onClick(View v){
        int ret = 0;
        NetSDK_USER_VIDEOINFO videoinfo = new NetSDK_USER_VIDEOINFO();
        videoinfo.setnVideoPort(554);
        videoinfo.setbIsTcp(1);
        videoinfo.setnVideoChannle(play_stream_no);

        int vId = v.getId();
        switch (vId)
        {
            case R.id.btn_Search_Start:
                searchIPCEngine.stopSearch();
                searchIPCEngine.startSearch();
                ipList.clear();
                btn_Search_Start.setEnabled(false);
                btn_Search_Stop.setEnabled(true);
                break;
            case R.id.btn_Search_Stop:
                searchIPCEngine.stopSearch();
                //ipList.add(0, "192.168.1.100");
                if(ipList.size() >= 1)
                {
                    btn_LoginDev0.setEnabled(true);
                }
                if(ipList.size() >= 2)
                {
                    btn_RealPlay1.setEnabled(true);
                }
                btn_Search_Start.setEnabled(true);
                btn_Search_Stop.setEnabled(false);
                break;
            case R.id.btn_modify_ip: {
                if(ToModifyIPC == null)
                {
                    Toast.makeText(MainActivity.this,
                            "search first.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                final EditText editText = new EditText(MainActivity.this);
                editText.setText("192.168.1.136");
                AlertDialog.Builder inputDialog =
                        new AlertDialog.Builder(MainActivity.this);
                String title = "modify ip of device(" + ToModifyIPC.getIpc_sn() + ").";
                inputDialog.setTitle(title).setView(editText);
                inputDialog.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                inputDialog.setPositiveButton("Ok",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ToModifyIPC.getLanCfg().setDhcpEnable(0);
                                ToModifyIPC.getLanCfg().setIPAddress(editText.getText().toString());
                                //ToModifyIPC.getLanCfg().setGateWay("");
                                searchIPCEngine.modifyIPByBroadcast(ToModifyIPC.getIpc_sn(), ToModifyIPC.getLanCfg());
                                Toast.makeText(MainActivity.this,
                                        "Send modify cmd success",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }).show();
                break;
            }
            case R.id.btn_exit:
                exit_app();
                break;
            case R.id.btn_LoginDev:
                if(lUser0 >0)
                {
                    Toast.makeText(this, "login before", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(ipList.size() < 1)
                {
                    Toast.makeText(this, "ip not set, search fisrt.", Toast.LENGTH_SHORT).show();
                    return;
                }

                lUser0 = funclibAgent.LoginDev(ipList.get(0), 8091, "admin", "123456");//
                Log.d(TAG, "handletest lUser0:" + lUser0);
                //lUser0 = funclibAgent.LoginDev("192.168.1.110", 8091, "admin", "123456");//
                //if searched info struct not provide username or password, provide a dialog request user to input.Not needed most of the time;
                if(lUser0 == 0)
                {
                    Log.e(TAG, "login error..");
                    return;
                }
                Toast.makeText(this, "login success", Toast.LENGTH_SHORT).show();
                btn_RealPlay0.setEnabled(true);
                btn_upgrade0.setEnabled(true);
                btn_restore_config0.setEnabled(true);
                btn_upload_config.setEnabled(true);
                btn_reboot0.setEnabled(true);
                btn_GetDevConfig0.setEnabled(true);
                btn_sound0.setEnabled(true);
                btn_talk0.setEnabled(true);
                btn_PTZ_Ctrl.setEnabled(true);
                break;
            case R.id.btn_RealPlay:
                if(lUser0 == 0)
                {
                    Toast.makeText(this, "please login first", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(lRealHandle0 != 0)
                {
                    Toast.makeText(this, "stop play", Toast.LENGTH_SHORT).show();
                    int re = funclibAgent.StopRealPlay(lRealHandle0);
                    Log.i(TAG, "stop play return " + re);
                    if(re != 0)
                    {
                        Log.e(TAG, "### fault:video stream close fail! 视频流关闭失败!!");
                    }
                    lRealHandle0 = 0;
                    aDecoderMng.ReleaseChannelPair(decHandle0);
                    btn_RealPlay0.setText("start");
                    return;
                }
                lRealHandle0 = funclibAgent.RealPlay(1, ipList.get(0), "admin", "123456", videoinfo.objectToByteBuffer(ByteOrder.nativeOrder()).array());
                if(lRealHandle0 == 0)
                {
                    Log.e(TAG, "RealPlay error..");
                }
//                long timestamp = System.currentTimeMillis()/1000 - 60 * 60;
//                funclibAgent.ReplayStartOrSeek(lUser0, timestamp);//按时间回放
                xYuvShowAgent.StartRender(renderChnIndex0);
                //btn_RealPlay0.setEnabled(false);
                btn_RealPlay0.setText("stop");
                break;
            case R.id.btn_GetDevConfig:
                if(lUser0 == 0)
                {
                    Toast.makeText(this, "login first", Toast.LENGTH_SHORT).show();
                    return;
                }
                ret = funclibAgent.GetDevConfig(lUser0, Defines.CMD_GET_MEDIA_VIDEO_CONFIG);
//                ret |= funclibAgent.GetDevConfig(lUser0, Defines.CMD_GET_MEDIA_AUDIO_CONFIG);
//                ret |= funclibAgent.GetDevConfig(lUser0, Defines.CMD_GET_ALARM_MOTIONDETECT_CONFIG);
//                ret |= funclibAgent.GetDevConfig(lUser0, Defines.CMD_GET_ALARM_PD);
//                ret |= funclibAgent.GetDevConfig(lUser0, Defines.CMD_GET_RECORD_CONFIG);
//                ret |= funclibAgent.SystemControl(lUser0, Defines.CMD_GET_WIFI_AP_INFO, "");
//                ret |= funclibAgent.GetDevConfig(lUser0, Defines.CMD_GET_NETWORK_WIFI_CONFIG);

//                ret |= funclibAgent.SystemControl(lUser0, Defines.CMD_GET_SYSTEMCONTROLSTRING, "");
                /*
                String getRecReq = new NetSDK_RecordList().makeReqXml(System.currentTimeMillis(), Locale.getDefault());
                ret |= funclibAgent.SystemControl(lUser0, Defines.CMD_GET_RECORD_FILE_LIST, getRecReq);
                if(ret != 0)
                {
                    Log.e(TAG, "Get Dev Config fail. error code:"+ret);
                }*/
//                ret |= funclibAgent.SystemControl(lUser0, Defines.CMD_SET_SYSTEM_TIME, new NetSDK_SyncTime(System.currentTimeMillis(), TimeZone.getTimeZone("GMT-3")).toXmlString());
                break;
            case R.id.btn_Set_bright_normal:
                try{
                    m_video_config0.capture.Brightness = "128";
                    m_video_config0.capture.Contrast = "128";
                    m_video_config0.capture.Saturation = "128";
                    m_video_config0.capture.Sharpness = "128";
                    m_video_config0.addHead(false);//most of config not need xml header.
                    funclibAgent.SetDevConfig(lUser0, Defines.CMD_SET_MEDIA_VIDEO_CAPTURE, m_video_config0.getCaptureXMLString());
                }catch (Exception e)
                {
                    e.printStackTrace();
                }
                break;
            case R.id.btn_Set_bright_highest:
                try{
                    m_video_config0.capture.Brightness = "200";
                    //m_video_config0.capture.Contrast = "200";
                    //m_video_config0.capture.Saturation = "200";
                    //m_video_config0.capture.Sharpness = "200";
                    m_video_config0.addHead(false);
                    funclibAgent.SetDevConfig(lUser0, Defines.CMD_SET_MEDIA_VIDEO_CAPTURE, m_video_config0.getCaptureXMLString());
                    Log.i(TAG, "Video capture config:" + m_video_config0.getCaptureXMLString());
                }catch (Exception e)
                {
                    e.printStackTrace();
                }
                break;
            case R.id.btn_Set_volume_full:
                try{
                    //m_audio_config0.setVolume("100");//capture volume, device receive sound from environment
                    m_audio_config0.setVolumePlay("100");//play volume, device output sound
                    //m_audio_config0.setAmplify("1");//Whether Power amplifier is opened
                    funclibAgent.SetDevConfig(lUser0, Defines.CMD_SET_MEDIA_AUDIO_CAPTURE, m_audio_config0.getCaptureXMLString());
                    Log.i(TAG, "Video capture config:" + m_video_config0.getCaptureXMLString());
                }catch (Exception e)
                {
                    e.printStackTrace();
                }
                break;
            case R.id.btn_Set_volume_half:
                try{
                    //m_audio_config0.setVolume("100");//capture volume
                    m_audio_config0.setVolumePlay("50");//play volume
                    //m_audio_config0.setAmplify("1");//if Power amplifier open
                    funclibAgent.SetDevConfig(lUser0, Defines.CMD_SET_MEDIA_AUDIO_CAPTURE, m_audio_config0.getCaptureXMLString());
                    Log.i(TAG, "Video capture config:" + m_video_config0.getCaptureXMLString());
                }catch (Exception e)
                {
                    e.printStackTrace();
                }
                break;
            case R.id.btn_upload_config:
                //you can check upload configure process from StatusEvent callback
                String config_file = Environment.getExternalStorageDirectory() + "/" + "config_MY200D_V0.xml";
                if(!config_file.contains("MY200D_V0"))
                {
                    //don't forget to check xml file
                    Toast.makeText(this, "wrong xml file", Toast.LENGTH_SHORT).show();
                    return;
                }
                ret = funclibAgent.UploadConf(lUser0, config_file);
                if(ret != 0)
                {
                    Toast.makeText(this, "Upload config file failed", Toast.LENGTH_SHORT).show();
                    if(ret == -1)
                    {
                        Toast.makeText(this, "xml file not found.", Toast.LENGTH_SHORT).show();
                    }
                }
                else
                {
                    Toast.makeText(this, "start upload config file success", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btn_upgrade:
                //you can check upload firmware process from StatusEvent callback
                if(lUser0 == 0)
                {
                    Toast.makeText(this, "login first", Toast.LENGTH_SHORT).show();
                    return;
                }
                //upload progress or error can be found from the callback,if upload is success, it will take some time to write the firmware to flash.
                if(!isUpgrading)
                {
                    String firmwarePath = Environment.getExternalStorageDirectory() + "/" + "firmware_MY200D_V0_V2.3.7_201706231734.bin";
                    ret = funclibAgent.Upgrade(lUser0, firmwarePath);
                    if(ret != 0)
                    {
                        Log.e(TAG, "Start upgrade failed with:"+ ret);
                        if(ret == -8999982)
                        {
                            Toast.makeText(this, "firmware file not found.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else
                    {
                        Log.i(TAG, "Start upgrade success.");
                        isUpgrading = true;
                        Toast.makeText(this, "begin upload " + firmwarePath, Toast.LENGTH_SHORT).show();
                    }
                }
                else
                {
                    Toast.makeText(this, "Cancel upgrade.", Toast.LENGTH_SHORT).show();
                    ret = funclibAgent.CancelUpgrade(lUser0);
                    if(ret != 0)
                    {
                        Log.e(TAG, "Cancel upgrade failed:"+ret);
                        if(ret == -8999985)
                        {
                            Log.e(TAG, "upload task not exist.");
                        }
                    }
                    isUpgrading = false;
                }

                break;
            case R.id.btn_restore_config:
                //restore to default configure
                //after receive result of success, call reboot command to bring the configure into effect.
                funclibAgent.SystemControl(lUser0, Defines.CMD_CONFIG_RESTORE, "");
                break;
            case R.id.btn_reboot:
                funclibAgent.SystemControl(lUser0, Defines.CMD_CONFIG_REBOOT_DEVICE, "");
                break;
            case R.id.btn_RealPlay1:
                if(lRealHandle1 != 0)
                {
                    Toast.makeText(this, "have retrieve stream", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(ipList.size() < 2)
                {
                    Toast.makeText(this, "ip not set, search first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                lUser1 = funclibAgent.LoginDev(ipList.get(1), 8091, "admin", "123456");//
                if(lUser1 == 0)
                {
                    Log.e(TAG, "login error..");
                    return;
                }
                lRealHandle1 = funclibAgent.RealPlay(0, ipList.get(0), "admin", "123456", videoinfo.objectToByteBuffer(ByteOrder.nativeOrder()).array());
                if(lRealHandle1 == 0)
                {
                    Log.e(TAG, "RealPlay error..");
                }
                xYuvShowAgent.StartRender(renderChnIndex1);
                btn_RealPlay1.setEnabled(false);
                btn_StopPlay1.setEnabled(true);
                btn_Record_start1.setEnabled(true);
                btn_Record_stop1.setEnabled(false);
                break;
            case R.id.btn_StopPlay1:
                Log.d(TAG, "stop play 1");
                if(Mp4Muxer.isWorking())
                {
                    Toast.makeText(this, "stop record", Toast.LENGTH_SHORT).show();
                    Mp4Muxer.stop();
                }
                int re = funclibAgent.StopRealPlay(lRealHandle1);
                Log.d(TAG, "stop play 1 return " + re);
                lRealHandle1 = 0;

                aDecoderMng.ReleaseChannelPair(decHandle1);
                xYuvShowAgent.StopRender(decHandle1);
                //xYuvShowAgent.ClearScreen(decHandle1);
                btn_RealPlay1.setEnabled(true);
                btn_StopPlay1.setEnabled(false);
                btn_Record_start1.setEnabled(false);
                btn_Record_stop1.setEnabled(false);

                break;
            case R.id.btn_Record_start1:
                if(lRealHandle1 != 0 || ToRecordParam == null)
                {
                    Toast.makeText(this, "realplay first", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(Mp4Muxer.isWorking())
                {
                    Toast.makeText(this, "is Recording now", Toast.LENGTH_SHORT).show();
                    return;
                }
                String fileSavePath = Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + "_record.mp4";
                int ret4 = Mp4Muxer.start(fileSavePath, ToRecordParam.objectToByteBuffer(ByteOrder.nativeOrder()).array());
                if(ret4 != Mp4Muxer.MP4MUXER_ERROR_NONE)
                {
                    Log.e(TAG, "start record failed：" + ret4);
                    Toast.makeText(this, "start record failed：" +  ret4, Toast.LENGTH_SHORT).show();
                }else
                {
                    Toast.makeText(this, "Begin recording", Toast.LENGTH_SHORT).show();
                }
                btn_Record_start1.setEnabled(false);
                btn_Record_stop1.setEnabled(true);
                break;
            case R.id.btn_Record_stop1:
                if(Mp4Muxer.isWorking())
                {
                    Toast.makeText(this, "Finish recording", Toast.LENGTH_SHORT).show();
                    Mp4Muxer.stop();
                }
                else
                {
                    Toast.makeText(this, "not start record before", Toast.LENGTH_SHORT).show();
                }
                btn_Record_start1.setEnabled(true);
                btn_Record_stop1.setEnabled(false);
                break;
            case R.id.btn_PTZ_Ctrl:
                if(lUser0 == 0)
                {
                    Toast.makeText(this, "login first", Toast.LENGTH_SHORT).show();
                    return;
                }
                ret = funclibAgent.PTZControlEx(lUser0, Defines.PTZ_CTRL_MOVE_LEFT_STR);//
                if(ret != 0)
                {
                     Log.e(TAG, "PTZControlEx error." + ret);
                }
                else
                {
                    Log.w(TAG, "PTZControlEx success.");
                }
                break;

            case R.id.btn_sound:
                Log.d(TAG, "btn_sound0.click..");
                if(isSoundOpened)
                {
                    audioPlayMng.StopPlay();
                    aDecoderMng.ReleaseAudioChannel(audioPlayDecHandle);
                    isSoundOpened = false;
                    audioPlayDecHandle = -1;
                    audioPlayRealHandle = -1;
                    Log.d(TAG, "btn_sound0.click..ReleaseAudioChannel");
                    Toast.makeText(this, "stop sound.", Toast.LENGTH_SHORT).show();
                    break;
                }
                if(ToPlayAudioParam == null)
                {
                    Log.e(TAG, "ToPlayAudioParam is null..");
                    break;
                }
                Log.i(TAG, "sound audio param:" + ToPlayAudioParam.toString());
                String format = ToPlayAudioParam.getCodec();
                int ret1 = 0;
                if(format.equals("PCMU")){//G.711U
                    Log.w(TAG, "Sound data PCMU");
                    ToPlayAudioParam.setChannels(1);//
                    ADecoderParam.AudioParam  ap = ToPlayAudioParam;
                    ret1 = aDecoderMng.CreateDecodeChannel(decHandle0, 0, ADDefines.FORMAT_G711_ULAW, ap, -1, 0, ADecodeChannel.DECODE_MODE_LAN);
                    if(ret1 < 0)
                    {
                        Log.e(TAG, "audio FORMAT_G711_ULAW CreateDecodeChannel failed, error code:" + ret1);
                    }
                    else
                    {
                        isSoundOpened = true;
                        audioPlayDecHandle = decHandle0;
                        audioPlayRealHandle = lRealHandle0;
                    }
                }
                else if(format.equals("PCMA"))
                {
                    Log.w(TAG, "Sound data PCMA");
                    ToPlayAudioParam.setChannels(1);//
                    ADecoderParam.AudioParam  ap = ToPlayAudioParam;
                    ret1 = aDecoderMng.CreateDecodeChannel(decHandle0, 0, ADDefines.FORMAT_G711_ALAW, ap, -1, 0, ADecodeChannel.DECODE_MODE_LAN);
                    if(ret1 < 0)
                    {
                        Log.e(TAG, "audio FORMAT_G711_ALAW CreateDecodeChannel failed, error code:" + ret1);
                    }
                    else
                    {
                        isSoundOpened = true;
                        audioPlayDecHandle = decHandle0;
                        audioPlayRealHandle = lRealHandle0;
                    }
                }
                else if(format.equals("MPEG4-GENERIC"))//AAC
                {
                    Log.w(TAG, "Sound data MPEG4-GENERIC");
                    //our aac packet is single channel，faad will decode it and output tow channel!
                    ADecoderParam.AudioParam ap = ToPlayAudioParam;
                    ret1 = aDecoderMng.CreateDecodeChannel(decHandle0, 0, ADDefines.FORMAT_AAC, ap, -1, 0, ADecodeChannel.DECODE_MODE_LAN);
                    if(ret1 < 0)
                    {
                        Log.e(TAG, "audio FORMAT_AAC CreateDecodeChannel failed, error code:" + ret1);
                    }
                    else
                    {
                        isSoundOpened = true;
                        audioPlayDecHandle = decHandle0;
                        audioPlayRealHandle = lRealHandle0;
                    }
                }
                else
                {
                    Log.e(TAG, "audio format not found.." + format);
                    break;
                }
                Log.w(TAG, "Audio sound samplerate:" + ToPlayAudioParam.getSamplerate() + " channels:" + ToPlayAudioParam.getChannels() + " bitspersample:" + ToPlayAudioParam.getBitspersample());
                int channelConfig = ToPlayAudioParam.getChannels()== 2?AudioFormat.CHANNEL_OUT_STEREO:AudioFormat.CHANNEL_OUT_MONO;
                audioPlayMng.StartPlay(ToPlayAudioParam.getSamplerate(), channelConfig, AudioFormat.ENCODING_PCM_16BIT);
                Toast.makeText(this, "start sound.", Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_talk:
                if(!hasRecordAudioPermission())
                {
                    requestRecordAudioPermission();
                    return;
                }
                if(ToPlayAudioParam == null)
                {
                    Log.e(TAG, "ToPlayAudioParam is null..");
                    break;
                }
                if(!isTalkOpened)
                {
                    if(ipList.size() < 1)
                    {
                        Toast.makeText(this, "ip not set, search fisrt.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String aFormat = ToPlayAudioParam.getCodec();
                    int ret3;
                    int audio_format = ADDefines.FORMAT_G711_ULAW;
                    String audio_format_str = AudioSender.AudioForamtG711;
                    if(aFormat.equals("MPEG4-GENERIC"))
                    {
                        audio_format = ADDefines.FORMAT_AAC;
                        audio_format_str = AudioSender.AudioForamtAAC;
                    }
                    else if(aFormat.equals("PCMA"))
                    {
                        audio_format = ADDefines.FORMAT_G711_ALAW;
                        audio_format_str = AudioSender.AudioForamtG711;
                    }

                    Log.i(TAG, "StartRecord params: " +
                            " audio_format:" + audio_format +
                            " sampleRate:" + ToPlayAudioParam.getSamplerate() +
                            " channels:" + ToPlayAudioParam.getChannels());
                    ret3 = audioRecordMng.StartRecord(audio_format, ToPlayAudioParam.getSamplerate(), ToPlayAudioParam.getChannels(), ToPlayAudioParam.getBitspersample(), ToPlayAudioParam.getBitrate());
                    if(ret3 != 0)
                    {
                        Log.e(TAG, "StartRecord failed...");
                        break;
                    }
                    talkLUser = lUser0;
                    audioSendersMng.AddSender(talkLUser, ipList.get(0), 8091, "admin", "123456",
                            audio_format_str,
                            String.valueOf(ToPlayAudioParam.getChannels()),
                            String.valueOf(ToPlayAudioParam.getSamplerate()),
                            String.valueOf(ToPlayAudioParam.getBitrate()));
                    Log.i(TAG, "audio record had start..");

                    Toast.makeText(this, "start talk.", Toast.LENGTH_SHORT).show();
                    isTalkOpened = true;
                    break;
                }
                else
                {
                    audioRecordMng.StopRecord();
                    isTalkOpened = false;
                    audioSendersMng.RemoveSender(talkLUser);
                    talkLUser = -1;
                    Toast.makeText(this, "stop talk.", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private boolean hasRecordAudioPermission(){
        boolean hasPermission = (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);

        //log("Has RECORD_AUDIO permission? " + hasPermission);
        return hasPermission;
    }

    private void requestRecordAudioPermission(){

        String requiredPermission = Manifest.permission.RECORD_AUDIO;

        // If the user previously denied this permission then show a message explaining why
        // this permission is needed
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                requiredPermission)) {

            //showToast("This app needs to record audio through the microphone....");
        }

        // request the permission.
        ActivityCompat.requestPermissions(this,
                new String[]{requiredPermission},
                PERMISSIONS_REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        // This method is called when the user responds to the permissions dialog
    }

    private void exit_app(){
        searchIPCEngine.release();
        xYuvShowAgent.release();
        funclibAgent.release();
        aDecoderMng.release();
        audioPlayMng.release();
        audioRecordMng.release();
        audioSendersMng.release();
        this.finish();
        // If you find some problem when restart app, system.exit(0) will help you
    }

    class CallBackProcess implements  SearchIPCEngine.searchIPCListener, FunclibAgent.IDirectConnectCB, ADecoderMng.DataCBListener, AudioRecordMng.AudioEncodedDataCBListener{
        @Override
        public void getSearchIPC(NetSDK_IPC_ENTRY entry){
            Log.d(TAG, "Search callback, New IPC:"+entry.getLanCfg().getIPAddress() + " OSD:" + entry.getOSD());
            if(ToModifyIPC == null)
            {
                ToModifyIPC = entry;
            }
            if(!ipList.contains(entry.getLanCfg().getIPAddress()))
            {
                ipList.add(entry.getLanCfg().getIPAddress());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String textString = "  "+ ipList.size() + "  ";
                        tv_ipc_count.setText(textString);
                    }
                });
            }
        }

        public int AUXResponse(long lUser, int nType, String pResponse)
        {
            //you can get some settings result for camera from this function.

            int cmd_ex = nType & 0x00ffffff;
            final int flag = nType & 0xff000000;// == 0, success
            Log.i(TAG, "AUXResponse -lUser:" +  lUser + "-cmd:" + String.valueOf(cmd_ex));
            Log.i(TAG, "AUXResponse pResponse"+ pResponse);
            try{
                switch (cmd_ex)
                {
                    case Defines.CMD_GET_MEDIA_VIDEO_CONFIG:
                        NetSDK_Media_Video_Config video_config = (NetSDK_Media_Video_Config)new NetSDK_Media_Video_Config().fromXML(pResponse);
                        Log.d(TAG, "NetSDK_Media_Video_Config:" + video_config.toXMLString());
                        if(lUser == lUser0)
                        {
                            //save it
                            m_video_config0 = video_config;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    btn_Set_bright_highest.setEnabled(true);
                                    btn_Set_bright_normal.setEnabled(true);
                                }
                            });
                        }
                        break;
                    case Defines.CMD_GET_MEDIA_AUDIO_CONFIG:
                        NetSDK_AUDIO_PARAM_EXTEND audio_config = (NetSDK_AUDIO_PARAM_EXTEND)new NetSDK_AUDIO_PARAM_EXTEND().fromXML(pResponse);
                        Log.d(TAG, "NetSDK_AUDIO_PARAM_EXTEND:" + audio_config.toXml());
                        if(lUser == lUser0)
                        {
                            m_audio_config0 = audio_config;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    btn_Set_volume_full.setEnabled(true);
                                    btn_Set_volume_half.setEnabled(true);
                                }
                            });
                        }
                        break;
                    case Defines.CMD_SET_MEDIA_VIDEO_CAPTURE:
                    {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(flag == 0)
                                {
                                    Toast.makeText(MainActivity.this, "set video capture success", Toast.LENGTH_SHORT).show();
                                }
                                else
                                {
                                    Toast.makeText(MainActivity.this, "set video capture fail", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        break;
                    }
                    case Defines.CMD_SET_MEDIA_AUDIO_CAPTURE:
                    {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(flag == 0)
                                {
                                    Toast.makeText(MainActivity.this, "set audio capture success", Toast.LENGTH_SHORT).show();
                                }
                                else
                                {
                                    Toast.makeText(MainActivity.this, "set audio capture fail", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        break;
                    }
                    case Defines.CMD_CONFIG_REBOOT_DEVICE:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(flag == 0)
                                {
                                    Toast.makeText(MainActivity.this, "reboot success", Toast.LENGTH_SHORT).show();
                                    //our sdk will retry connect to device, when reboot finished. play action will continue.
                                }
                                else
                                {
                                    Toast.makeText(MainActivity.this, "reboot fail", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        break;
                    case Defines.CMD_CONFIG_RESTORE:
                    {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(flag == 0)
                                {
                                    Toast.makeText(MainActivity.this, "restore configure success, device will reboot now", Toast.LENGTH_SHORT).show();
                                    //device will reboot automatically.
                                }
                                else
                                {
                                    Toast.makeText(MainActivity.this, "restore configure fail", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        break;
                    }
                    case Defines.CMD_GET_SYSTEM_VERSION_INFO:
                        NetSDK_SystemVersionInfo info = (NetSDK_SystemVersionInfo)new NetSDK_SystemVersionInfo().fromXML(pResponse);
                        Log.d(TAG, "Get System Version info. KernelVersion:" + info.KernelVersion + " FileSystemVersion:" + info.FileSystemVersion);
                        break;
                    case Defines.CMD_GET_ALARM_MOTIONDETECT_CONFIG:
                    {
                        NetSDK_MotionDetectAlarm motionDetectAlarm = (NetSDK_MotionDetectAlarm)NetSDK_MotionDetectAlarm.fromXML(pResponse);

                        Log.d(TAG, "Get md info:" + motionDetectAlarm.toXMLString());
                        break;
                    }
                    case Defines.CMD_GET_ALARM_PD:
                    {
                        NetSDK_PersonDetectAlarm personDetectAlarm = (NetSDK_PersonDetectAlarm) NetSDK_PersonDetectAlarm.fromXML(pResponse);
                        Log.d(TAG, "Get pd info:" + personDetectAlarm.toXMLString());
                        break;
                    }
                    case Defines.CMD_GET_RECORD_CONFIG:
                    {
                        NetSDK_RecordConfig cfg = (NetSDK_RecordConfig)NetSDK_RecordConfig.fromXML(pResponse);

                        Log.d(TAG, "Get record cfg:" + cfg.toXMLString());
                        break;
                    }
                    case Defines.CMD_GET_WIFI_AP_INFO:
                    {
                        NetSDK_WifiApInfos cfg = (NetSDK_WifiApInfos)NetSDK_WifiApInfos.fromXML(pResponse);
                        for (NetSDK_WifiApInfos.WifiAp ap:
                            cfg.apList) {
                            Log.d(TAG, "find wifi:" + ap.ssid + "-quality:" + ap.quality + "-authMode:" + ap.authMode);
                        }
                        break;
                    }
                    case Defines.CMD_GET_NETWORK_WIFI_CONFIG:
                    {
                        NetSDK_Wifi_Config cfg = (NetSDK_Wifi_Config)new NetSDK_Wifi_Config().fromXML(pResponse);
                        Log.i(TAG, "current wifi ssid:" + cfg.ESSID);
                        cfg.addHead(false);
                        cfg.ESSID = "中文SSID";
                        Log.i(TAG, "current wifi config:" + cfg.toXMLString());
                        FunclibAgent.getInstance().SetDevConfig(lUser,
                                Defines.CMD_SET_NETWORK_WIFI_CONFIG,
                                cfg.toXMLString());
                        break;
                    }
                    case Defines.CMD_GET_RECORD_FILE_LIST:
                    {
                        NetSDK_RecordList recordList = NetSDK_RecordList.fromXML(pResponse);
                        for (NetSDK_RecordList.Record ap:
                                recordList.getRecordList()) {
                            Log.d(TAG, "find record item type:" + ap.recType + "-startMinute:" + ap.startMinute + "-endMinute:" + ap.endMinute);
                        }
                        break;
                    }
                    case Defines.CMD_GET_SYSTEMCONTROLSTRING:
                    {
                        NetSDK_SysControlString str = (NetSDK_SysControlString)new NetSDK_SysControlString().fromXML(pResponse);
                        Log.i(TAG, "get sys ability:" + str.SystemConfigString);
                        break;
                    }
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }

            return 0;
        }

        public String bytesToHexString(byte[] src){
            StringBuilder stringBuilder = new StringBuilder("");
            if (src == null || src.length <= 0) {
                return null;
            }
            for (int i = 0; i < src.length; i++) {
                int v = src[i] & 0xFF;
                String hv = Integer.toHexString(v);
                if (hv.length() < 2) {
                    stringBuilder.append(0);
                }
                stringBuilder.append(hv);
            }
            return stringBuilder.toString();
        }

        FileOutputStream stream;
        int outcount = 0;
        public int RealData(long lRealHandle, int dwDataType, byte[] pBuffer, int dwBufSize, int isKey, int timestamp)
        {
            //real data from ip camera
            long  startTime = System.currentTimeMillis();
            int ret =0;
            Log.d(TAG, "RealData -lRealHandle:" + String.valueOf(lRealHandle) + "-dwDataType:" + String.valueOf(dwDataType) + "dwBufSize:" + dwBufSize);

            if(dwDataType == 0)//means pBuffer is video data
            {
                //Log.d(TAG,"RealData video dwBufSize:"+ dwBufSize);

                if(lRealHandle == lRealHandle0)
                {
                    byte[] userBuffer = "Test Byte Buffer".getBytes();
//                    ByteBuffer byteBuffer = ByteBuffer.wrap(userBuffer);
//                    byteBuffer.order(ByteOrder.nativeOrder());
                    String src_bytes = bytesToHexString(userBuffer);
                    //Log.i(TAG, "src bytes:" + src_bytes);
                    ret = aDecoderMng.InputDataBuf(decHandle0, true, isKey != 0, pBuffer, (int)dwBufSize, userBuffer, userBuffer.length);
                }
                else if(lRealHandle == lRealHandle1)
                {
                    ret = aDecoderMng.InputDataBuf(decHandle1, true, isKey != 0, pBuffer, (int)dwBufSize, null, 0);
                }
                if(ret<0)
                {
                    Log.e(TAG, "video InputDataBuf failed. error no:" + ret);
                }
            }
            else if(dwDataType == 1)//audio data
            {
                if (dwBufSize <= 0 || null == pBuffer) return 0;

                //Log.d(TAG, "RealData audio dwBufSize:" + dwBufSize + "-lRealHandle:" + lRealHandle + "-audioPlayRealHandle:" + audioPlayRealHandle);
                if(lRealHandle == audioPlayRealHandle)
                {
                    byte[] userBuffer = "abc Test Byte Buffer1".getBytes();
                    String src_bytes = bytesToHexString(userBuffer);
                    Log.i(TAG, "abc src bytes:" + src_bytes);
                    ret = aDecoderMng.InputDataBuf(audioPlayDecHandle, false, true, pBuffer, (int)dwBufSize, userBuffer, userBuffer.length);
                    if(ret<0)
                    {
                        Log.e(TAG, "audio InputDataBuf failed. error no:" + ret);
                    }
                    //Log.d(TAG, "RealData audio had input a packet.");
                }

            }
            else if(dwDataType == 2)//video and audio params from ip camera.
            {
                ByteBuffer byteBuffer = ByteBuffer.allocate((int) dwBufSize);
                byteBuffer.order(ByteOrder.nativeOrder());
                byteBuffer.put(pBuffer, 0, (int) dwBufSize);
                byteBuffer.rewind();
                ADecoderParam param = (ADecoderParam) ADecoderParam.createObjectByByteBuffer(byteBuffer);

                //init video param
                Log.i(TAG, "VideoParam=" + param.getVideoParam().toString());
                byte[] paramBytes = param.objectToByteBuffer(ByteOrder.nativeOrder()).array();
                int codec = ADDefines.FORMAT_H264;
                if(param.getVideoParam().getCodec().equals("H264"))
                {
                    Log.i(TAG, "realData: get video codec...H264");
                    codec = ADDefines.FORMAT_H264;
                }
                else if(param.getVideoParam().getCodec().equals("H265"))
                {
                    Log.i(TAG, "realData: get video codec...H265");
                    codec = ADDefines.FORMAT_H265;
                }
                else
                {
                    Log.e(TAG, "realData: not support video codec...");
                    return -1;
                }

                if(lRealHandle == lRealHandle0)
                {
                    //you can choose multi decoder from ffmpeg, or other(hisi), don't use other type.
                    ret = aDecoderMng.CreateDecodeChannel(decHandle0, ADDefines.DECODE_VIDEO_LIB_FFMPEG, codec, param.getVideoParam(), renderChnIndex0, play_stream_no, ADecodeChannel.DECODE_MODE_LAN);
                    if(ret<0)
                    {
                        Log.e(TAG, "CreateDecodeChannel failed, error code:" + ret);
                        return 0;
                    }
                    videoParam0 = param.getVideoParam();
                    xYuvShowAgent.SetFrameRate(renderChnIndex0, videoParam0.getFramerate());
                }
                else if(lRealHandle == lRealHandle1)
                {
                    ret = aDecoderMng.CreateDecodeChannel(decHandle1, ADDefines.DECODE_VIDEO_LIB_FFMPEG, codec, param.getVideoParam(), renderChnIndex1, play_stream_no, ADecodeChannel.DECODE_MODE_LAN);
                    if(ret<0)
                    {
                        Log.e(TAG, "CreateDecodeChannel failed, error code:" + ret);
                        return 0;
                    }
                    xYuvShowAgent.SetFrameRate(renderChnIndex1, param.getVideoParam().getFramerate());
                    ToRecordParam = param;
                }

                //init audio param
                if (param.getbHaveAudio() != 0) {
                    Log.i(TAG, "AudioParam=" + param.getAudioParam().toString());
                    if(lRealHandle == lRealHandle0)
                    {
                        ToPlayAudioParam = param.getAudioParam();
                    }
                }

            }

            if(lRealHandle == lRealHandle1 && Mp4Muxer.isWorking())//Mp4Muxer can mix video and audio data to a file with suffix ".mp4"
            {
                //we record second routine video and audio.
                FRAME_EXTDATA pExtData = new FRAME_EXTDATA();
                pExtData.setbIsKey(isKey);
                pExtData.setTimestamp(timestamp);
                Mp4Muxer.write(dwDataType, pBuffer, dwBufSize, pExtData.objectToByteBuffer(ByteOrder.nativeOrder()).array());
            }
            //Log.d(TAG, "RealData callback,cost milli time:" + (System.currentTimeMillis() - startTime));
            return 0;
        }

        public int StatusEvent(long lUser, int nStateCode, String pResponse)
        {
            Log.d(TAG, "StatusEvent -lUser:" + String.valueOf(lUser) + "-nStateCode:" + String.valueOf(nStateCode));
            switch (nStateCode) {
                case NetSatatEvent.EVENT_CONNECTOK:
                    break;
                case NetSatatEvent.EVENT_CONNECTFAILED:
                    break;
                case NetSatatEvent.EVENT_SOCKETERROR:
                    break;
                case NetSatatEvent.EVENT_LOGINOK:
                    break;
                case NetSatatEvent.EVENT_LOGINFAILED:
                    break;
                case NetSatatEvent.EVENT_UPLOADOK:
                    Log.w(TAG, "StatusEvent Upload success:" + pResponse);
                    //check upload progress here, when progress reach 100, means upload success.
                    break;
                case NetSatatEvent.EVENT_UPLOADFAILED:
                    Log.w(TAG, "StatusEvent Upload fail:" + pResponse);

                    break;
                case NetSatatEvent.EVENT_UPLOAD_PROCESS:
                    Log.i(TAG, "StatusEvent Upload progress -pResponse:" + pResponse);
                    break;
                default:
            }
            return 0;
        }

        //回放时的数据
        public int ReplayData(long lRealHandle, int dwDataType, byte[] pBuffer, int dwBufSize, int isKey, int timestamp)
        {
            Log.d(TAG, "ReplayData:" + lRealHandle + "-dwDataType:" + dwDataType);
            return 0;
        }

        boolean shooted = false;

        /**
         * callback of decoded data
         */
        public void OnDecDataCB(int handle, int format, ByteBuffer data, int size, int width, int height, int userIndex, ByteBuffer userExtraBuf, int extraSize){
            Log.d(TAG, "OnDecDataCB -handle:" + handle + "-format:" + format + "-dataSize:" + size + "-width:" + width + "-height:" + height + "-extraSize:" + extraSize);
            //
            if(format>ADDefines.FORMAT_SEP && handle == audioPlayDecHandle)
            {
                //Log.d(TAG, "OnDecDataCB AddPcmDataToPlay");
                AudioBuffer buffer = new AudioBuffer(data, size);
                audioPlayMng.AddPcmDataToPlay(buffer);
                if(userExtraBuf == null)
                    Log.i(TAG, "userExtraBuf == null !!");
                String des_bytes = bytesToHexString(userExtraBuf.array());
                Log.i(TAG, "audio des bytes:" + des_bytes);
/*
                try{
                    if(outcount == 0)
                    {
                        String filename = Environment.getExternalStorageDirectory() + "/record_" + System.currentTimeMillis() + ".pcm";
                        File file = new File(filename);
                        stream = new FileOutputStream(file);
                    }else if(outcount < 1000)
                    {
                        stream.write(data.array(), 0, size);
                    }
                    else
                    {
                        stream.close();
                    }
                }catch (Exception e)
                {
                    e.printStackTrace();
                }
                outcount ++;
*/
                return;
            }
//            String des_bytes = bytesToHexString(userExtraBuf.array());
//            Log.i(TAG, "des bytes:" + des_bytes);
            long  startTime = System.currentTimeMillis();
            if(handle == decHandle0)
            {
                //xYuvShowAgent.Render(renderChnIndex0, data, size, width, height, 1, 0, 0);
                xYuvShowAgent.Render(renderChnIndex0, data, size, width, height, scaleFactor, moveSpacingX, moveSpacingY);
            }else if(handle == decHandle1)
            {
                xYuvShowAgent.Render(renderChnIndex1, data, size, width, height, 1, 0, 0);
            }

            if(!shooted)
            {
                String fileName = Environment.getExternalStorageDirectory() + "/" + System.currentTimeMillis() + "_" + width + "x" + height +".jpg";
                if(xYuvShowAgent.ShotScreen(renderChnIndex0, fileName) == 0)
                {
                    Log.i(TAG, "shot success。");
                }
                shooted = true;
            }

            //Log.d(TAG, "video on decoded data callback cost time:" + (System.currentTimeMillis() - startTime));
        }

        public void OnEncodedAudioDataCallBack(int format, byte[] data, int size, long timeStampInMillis)
        {
            //Log.d(TAG, "get audio data timeStampInMillis:" + String.valueOf(timeStampInMillis));
            //Log.d(TAG, "audio encode cost time:" + String.valueOf(System.currentTimeMillis() - timeStampInMillis));
            //Log.d(TAG, "OnEncodedAudioDataCallBack -format:" + format + "-size:" + size);

            int ret = audioSendersMng.SendAudioData(talkLUser, data, size, timeStampInMillis);//, send audio data to camera for playing
            if(ret != 0)
            {
                Log.w(TAG, "audio data not send error...code:" + ret);
            }
        }
        public void onAudioRecordError(){
            Log.e(TAG, "onAudioRecordError");
        }

    }
}
