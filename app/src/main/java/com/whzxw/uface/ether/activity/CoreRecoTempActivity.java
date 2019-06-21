package com.whzxw.uface.ether.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.constraint.Group;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.uniubi.faceapi.CvFace;
import com.uniubi.uface.ether.R;
import com.uniubi.uface.ether.base.UfaceEtherImpl;
import com.uniubi.uface.ether.config.ServiceOptions;
import com.uniubi.uface.ether.config.configenum.algorithm.FaceOrientation;
import com.uniubi.uface.ether.core.EtherFaceManager;
import com.uniubi.uface.ether.core.bean.AliveResult;
import com.uniubi.uface.ether.core.bean.CheckFace;
import com.uniubi.uface.ether.core.bean.IdentifyResult;
import com.uniubi.uface.ether.core.cvhandle.FaceHandler;
import com.uniubi.uface.ether.core.faceprocess.IdentifyResultCallBack;
import com.uniubi.uface.ether.outdevice.utils.FileNodeOperator;
import com.uniubi.uface.ether.utils.ImageUtils;
import com.whzxw.uface.ether.EtherApp;
import com.whzxw.uface.ether.bean.SettingMessageEvent;
import com.whzxw.uface.ether.database.PersonTable;
import com.whzxw.uface.ether.http.ApiService;
import com.whzxw.uface.ether.http.ResponseCabinetEntity;
import com.whzxw.uface.ether.http.ResponseEntity;
import com.whzxw.uface.ether.http.RetrofitManager;
import com.whzxw.uface.ether.utils.CameraUtils;
import com.whzxw.uface.ether.utils.NetHttpUtil;
import com.whzxw.uface.ether.utils.Voiceutils;
import com.whzxw.uface.ether.view.CountDownTimer;
import com.whzxw.uface.ether.view.FaceView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifTextView;

import static com.whzxw.uface.ether.activity.SplashActivity.INTENT_DEVCODE;
import static com.whzxw.uface.ether.activity.SplashActivity.INTENT_DEVNAME;
import static com.whzxw.uface.ether.http.ApiService.adUrl;
import static com.whzxw.uface.ether.schedule.AlarmManagerUtils.ACTION_ALRAM;

/**
 * @author qiaopeng
 * @date 2018/08/02
 * <p>
 * 这里是最新的界面的一些功能
 */
public class CoreRecoTempActivity extends AppCompatActivity implements IdentifyResultCallBack, EtherFaceManager.OnServerConnectListener {

    private FaceHandler faceHandler;


    private CameraUtils cameraRGB;

    private CameraUtils cameraIR;

    private EtherFaceManager etherFaceManager;

    private ServiceOptions serviceOptions;
    private String resultCode = "";
    private int deviceType = 2;
    /**
     * 跳转识别是哪个按钮
     */
    int recoFromWhichButton = -1;
    // 是否正在显示屏保
    private static boolean isPreViewCamera = true;
    private byte[] yuvByteData;
    @BindView(R.id.rgb_camera)
    TextureView textureRGBView;
    @BindView(R.id.ir_camera)
    TextureView textureIRView;

    @BindView(R.id.fvRGB)
    FaceView faceView;

    @BindView(R.id.adwebview)
    WebView adWebView;

    @BindView(R.id.layout)
    LinearLayout layoutContainer;

    @BindView(R.id.first_group)
    Group firstScreenGroup;
    @BindView(R.id.two_group)
    Group twoScreenGroup;

    @BindView(R.id.countdown_timer)
    CountDownTimer countDownTimer;

    @BindView(R.id.school_name)
    AppCompatTextView schoolNameView;

    @BindView(R.id.alert)
    GifTextView alertView;

    @BindView(R.id.camera_title)
    AppCompatTextView title;

    @BindView(R.id.operator_flow)
    AppCompatImageView operator_flow;

    AlarmBroadcastReceive alarmBroadcastReceive = new AlarmBroadcastReceive();

    private List<ResponseCabinetEntity.Cabinet> lockerList = new ArrayList<ResponseCabinetEntity.Cabinet>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 无title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // 全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_recognition);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        ButterKnife.bind(this);

        serviceOptions = UfaceEtherImpl.getServiceOptions();
        etherFaceManager = EtherFaceManager.getInstance();
        // 这个要提高堆叠的时候的登记，不然容易被后面的控件盖住。看不到。
        operator_flow.bringToFront();
        init();
        initCamera();
        initWebView();

        initWhiteYuvImage();
        etherFaceManager.startService(this, this, this);

        countDownTimer.setDeadlineListener(new CountDownTimer.DeadlineListener() {
            @Override
            public void deadline() {
                toMainScreen();
            }
        });

        registerReceiver(alarmBroadcastReceive, new IntentFilter(ACTION_ALRAM));
    }

    private void initWhiteYuvImage() {
        // 这里是读取assert的保存的一张空白的yuv的图片，保存下来，位了在屏保的时候推送到底层的识别。
        try {
            InputStream inputStream = getApplication().getResources().getAssets().open("white.yuv");
            yuvByteData = new byte[inputStream.available()];
            inputStream.read(yuvByteData);
            inputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化显示的空白页
     */
    private void initRecycleView() {
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());
//        lockerList.add(new ResponseCabinetEntity.Cabinet());

        // 列表的size 假设40个 每行显示10个
        int listSize = lockerList.size();
        int spanCount = 10;
        int rowSize = 0;
        if (listSize % spanCount == 0) {
            rowSize = listSize / spanCount;
        } else {
            rowSize = listSize / spanCount + 1;
        }
        // 每次进来都清空子控件
        layoutContainer.removeAllViews();
        for (int j = 1; j <= rowSize; j++) {
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < listSize; i++) {
                if (i < spanCount * j && i >= spanCount * (j - 1))
                    if (i % 2 == 0) {
                        View view = LayoutInflater.from(this).inflate(R.layout.locker_item, null);
                        ResponseCabinetEntity.Cabinet cabinet = lockerList.get(i);
                        if (cabinet.getUsed() == 1) {
                            ImageView v = ((ImageView) view.findViewById(R.id.item));
                            v.setImageResource(R.drawable.locker_used);
                            v.setBackgroundColor(getResources().getColor(R.color.hadLockerColor));
                        }
                        layout.addView(view);
                    } else {
                        View view = LayoutInflater.from(this).inflate(R.layout.locker_line_item, null);
                        ResponseCabinetEntity.Cabinet cabinet = lockerList.get(i);
                        if (cabinet.getUsed() == 1) {
                            ImageView v = ((ImageView) view.findViewById(R.id.item));
                            v.setImageResource(R.drawable.locker_used);
                            v.setBackgroundColor(getResources().getColor(R.color.hadLockerColor));
                        }
                        layout.addView(view);
                    }
            }
            layoutContainer.addView(layout);
        }

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice inputDevice = InputDevice.getDevice(event.getDeviceId());
        if (inputDevice.getName().equals("EHUOYAN.COM RfidLoginer")) {
            // 刷卡器事件  全部事件拦截
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_SHIFT_LEFT) {
                    // 开始刷卡
                    resultCode = "";
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    // 刷卡结束
                    Toast.makeText(CoreRecoTempActivity.this, resultCode, Toast.LENGTH_LONG).show();
                    final String cardNo = resultCode.toUpperCase();


                    if (!isPreViewCamera) {
                        cameraRGB.setScreenshotListener(new CameraUtils.OnCameraDataEnableListener() {
                            @Override
                            public void onCameraDataCallback(final byte[] data, int camId) {
//                                Bitmap bitmap = ImageUtils.rotateBitmap(ImageUtils.yuvImg2BitMap(data,640, 480), 90);
//                                    NetHttpUtil.sendMessage(personTable.getPseronId(), personTable.getFaceId(), 100f, personTable.getName(), personTable.getCardNO(), bitmap);
                                // 创建
                                Observable<byte[]> identifyResultObservable = Observable.just(data);
                                Observable<Integer> just = Observable.just(recoFromWhichButton);
                                Observable<Object[]> dataObservable = Observable.zip(identifyResultObservable, just, new BiFunction<byte[], Integer, Object[]>() {
                                    @Override
                                    public Object[] apply(byte[] identifyResult, Integer integer) throws Exception {
                                        Bitmap bitmap = ImageUtils.rotateBitmap(ImageUtils.yuvImg2BitMap(data, 640, 480), 90);
                                        return new Object[]{bitmap, integer};
                                    }
                                });

                                // 创建查数据的观察事件
                                Observable<PersonTable> personTableObservable = Observable.just(cardNo).map(new Function<String, List<PersonTable>>() {
                                    @Override
                                    public List<PersonTable> apply(String cardno) throws Exception {
                                        return EtherApp.daoSession.queryRaw(PersonTable.class, "where CARD_NO = ? ", cardno);
                                    }
                                }).filter(new Predicate<List<PersonTable>>() {
                                    @Override
                                    public boolean test(List<PersonTable> personTables) throws Exception {
                                        return personTables != null && personTables.size() != 0;
                                    }
                                }).map(new Function<List<PersonTable>, PersonTable>() {
                                    @Override
                                    public PersonTable apply(List<PersonTable> personTables) throws Exception {
                                        return personTables.get(0);
                                    }
                                });

                                Observable.zip(dataObservable, personTableObservable, new BiFunction<Object[], PersonTable, Object[]>() {
                                    @Override
                                    public Object[] apply(Object[] objects, PersonTable personTable) throws Exception {

                                        return new Object[]{objects[0], objects[1], personTable};
                                    }
                                })
                                        .flatMap(new Function<Object[], Observable<ResponseEntity>>() {
                                            @Override
                                            public Observable<ResponseEntity> apply(Object[] objects) throws Exception {
                                                Bitmap identifyResult = (Bitmap) objects[0];
                                                Integer type = (Integer) objects[1];
                                                PersonTable personTable = (PersonTable) objects[2];

                                                Map<String, String> params = new HashMap<>();
                                                params.put("personId", personTable.getPseronId());
                                                params.put("faceId", personTable.getFaceId());
                                                params.put("score", "-1");
                                                params.put("name", personTable.getName());
                                                params.put("cardNo", personTable.getCardNO());
                                                params.put("face", NetHttpUtil.bitmapToBase64(identifyResult));
                                                params.put("type", type + "");

                                                return RetrofitManager.getInstance()
                                                        .apiService
                                                        .sendRecoResult(ApiService.recoCallBackUrl, params);
                                            }
                                        })
                                        .flatMap(new Function<ResponseEntity, ObservableSource<?>>() {
                                            @Override
                                            public ObservableSource<?> apply(ResponseEntity responseEntity) throws Exception {
                                                Voiceutils.playRecoOver();
                                                showAlert(responseEntity.getMessage(), true);

                                                return Observable.just(1).delay(2, TimeUnit.SECONDS);
                                            }
                                        })
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .doOnSubscribe(new Consumer<Disposable>() {
                                            @Override
                                            public void accept(Disposable disposable) throws Exception {
                                                showAlert("快马加鞭开箱子！", true);

                                            }
                                        })
                                        .subscribeOn(AndroidSchedulers.mainThread()) // 指定线程之后，线程调用的是上游的回调在哪个线程中。

                                        .subscribe(new Consumer<Object>() {
                                            @Override
                                            public void accept(Object responseEntity) throws Exception {
                                                countDownTimer.stopCount();
                                                toMainScreen();
                                            }
                                        }, new Consumer<Throwable>() {
                                            @Override
                                            public void accept(Throwable throwable) throws Exception {
                                                showAlert("破网络失败了", true);
                                                toMainScreen();
                                            }
                                        });

                            }
                        });
                    }

                } else {
                    resultCode += Character.toString((char) event.getUnicodeChar());
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        final String cachePath = getApplicationContext().getDir("cache", Context.MODE_PRIVATE).getPath();
        adWebView.bringToFront();
        // WebView加载web资源
        adWebView.loadUrl(adUrl);
        // 使用硬件GPU加载
        adWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings webSettings = adWebView.getSettings();
        // Dom Storage（Web Storage）存储机制
        webSettings.setDomStorageEnabled(true);
        // Application Cache 存储机制 主要是对浏览器缓存的补充
        webSettings.setAppCacheEnabled(true);
        // 设置缓存的地址
        webSettings.setAppCachePath(cachePath);
        webSettings.setAppCacheMaxSize(5 * 1024 * 1024);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        // 设置与Js交互的权限
        webSettings.setJavaScriptEnabled(true);
        // 设置允许JS弹窗
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        adWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {

            }
        });
        // 覆盖WebView默认使用第三方或系统默认浏览器打开网页的行为，使网页用WebView打开
        adWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 返回值是true的时候控制去WebView打开，为false调用系统浏览器或第三方浏览器
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // 报错了，继续加载当前页
                view.loadUrl(request.getUrl().toString());
            }
        });


    }

    private void init() {
        faceHandler = new FaceHandler();
        faceHandler.init();

        Intent intent = getIntent();
        String schoolName = intent.getStringExtra(INTENT_DEVNAME);
        String deviceCode = intent.getStringExtra(INTENT_DEVCODE);
        schoolNameView.setText(schoolName + "\n" + deviceCode);

        try {

            GifDrawable gifFromAssets = new GifDrawable(getAssets(), "white.png");
            alertView.setBackground(gifFromAssets);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initCamera() {
        switch (deviceType) {
            case 0:
                faceView.initPaint(false, 0);

                cameraRGB = new CameraUtils(this, 0, 0);
                cameraIR = new CameraUtils(this, 1, 0);
                cameraIR.initCamera(1, new CameraUtils.OnCameraDataEnableListener() {
                    @Override
                    public void onCameraDataCallback(byte[] data, int camId) {
                        etherFaceManager.pushIRFrameData(data);
                    }
                });
                UfaceEtherImpl.getAlgorithmOptions().setFaceOrientation(FaceOrientation.CV_FACE_UP);
                break;
            case 1:
                faceView.initPaint(false, 0);

                cameraRGB = new CameraUtils(this, 0, 0);
                cameraIR = new CameraUtils(this, 1, 0);
                cameraIR.initCamera(1, new CameraUtils.OnCameraDataEnableListener() {
                    @Override
                    public void onCameraDataCallback(byte[] data, int camId) {
                        etherFaceManager.pushIRFrameData(data);
                    }
                });
                UfaceEtherImpl.getAlgorithmOptions().setFaceOrientation(FaceOrientation.CV_FACE_RIGHT);
                break;
            case 2:
                faceView.initPaint(false, 2);

                cameraRGB = new CameraUtils(this, 0, 90);
                cameraIR = new CameraUtils(this, 1, 90);
                cameraIR.initCamera(1, new CameraUtils.OnCameraDataEnableListener() {
                    @Override
                    public void onCameraDataCallback(byte[] data, int camId) {
                        etherFaceManager.pushIRFrameData(data);
                    }
                });
                UfaceEtherImpl.getAlgorithmOptions().setFaceOrientation(FaceOrientation.CV_FACE_LEFT);
                break;
            default:
                break;
        }

        cameraRGB.initCamera(0, new CameraUtils.OnCameraDataEnableListener() {
            @Override
            public void onCameraDataCallback(byte[] data, int camId) {
                // 判断是否屏保
                if (isPreViewCamera) {
                    if (yuvByteData != null)
                        etherFaceManager.pushRGBFrameData(yuvByteData);
                } else {
                    etherFaceManager.pushRGBFrameData(data);
                }

            }
        });

        textureRGBView.setSurfaceTextureListener(cameraRGB);
    }

    @Override
    public void onFaceIn(CvFace[] cvFaces) {
    }

    @Override
    public void onFaceNull() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                faceView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onTrackCallBack(final List<CheckFace> checkFaces) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (checkFaces == null) {
                    faceView.setVisibility(View.GONE);
                } else {
                    faceView.setVisibility(View.VISIBLE);
                    faceView.setFaces(checkFaces);
                }
            }
        });
    }

    @Override
    public void onIdentifySuccess(final IdentifyResult result) {

    }

    @Override
    public void onIdentifyFailed(final IdentifyResult result) {

    }

    @Override
    public void onAliveCallBack(final AliveResult result) {

    }

    @Override
    public void onIrFaceIn(CvFace[] cvFaces) {

    }

    @Override
    public void onWholeIdentifyResult(final IdentifyResult recognition) {
        // 人脸识别的回调
        Log.i("coreCall", "分数=" + recognition.getScore());
        if (isPreViewCamera) return;
        Log.i("coreCall", "分数=" + recognition.getScore());

        // 屏保的时候不让提交数据
        if (recognition.isAlivePass() && recognition.isVerifyPass()) {
            Log.i("coreCall", "通过正在启动柜门");

            // 创建
            Observable<IdentifyResult> identifyResultObservable = Observable.just(recognition);
            Observable<Integer> just = Observable.just(recoFromWhichButton);
            Observable<Object[]> dataObservable = Observable.zip(identifyResultObservable, just, new BiFunction<IdentifyResult, Integer, Object[]>() {
                @Override
                public Object[] apply(IdentifyResult identifyResult, Integer integer) throws Exception {
                    Bitmap bitmap = ImageUtils.rotateBitmap(ImageUtils.yuvImg2BitMap(recognition.getRgbYuvData(), 640, 480), 90);
                    identifyResult.setBitmap(bitmap);
                    return new Object[]{identifyResult, integer};
                }
            });
            // 创建查数据的观察事件
            Observable<PersonTable> personTableObservable = Observable.just(recognition).map(new Function<IdentifyResult, List<PersonTable>>() {
                @Override
                public List<PersonTable> apply(IdentifyResult identifyResult) throws Exception {
                    return EtherApp.daoSession.queryRaw(PersonTable.class, "where FACE_ID = ? and PSERON_ID = ?", recognition.getFaceId(), recognition.getPersonId());
                }
            }).filter(new Predicate<List<PersonTable>>() {
                @Override
                public boolean test(List<PersonTable> personTables) throws Exception {
                    return personTables != null && personTables.size() != 0;
                }
            }).map(new Function<List<PersonTable>, PersonTable>() {
                @Override
                public PersonTable apply(List<PersonTable> personTables) throws Exception {
                    return personTables.get(0);
                }
            });


            Observable.zip(dataObservable, personTableObservable, new BiFunction<Object[], PersonTable, Object[]>() {
                @Override
                public Object[] apply(Object[] objects, PersonTable personTable) throws Exception {
                    return new Object[]{objects[0], objects[1], personTable};
                }
            })
                    .flatMap(new Function<Object[], Observable<ResponseEntity>>() {
                        @Override
                        public Observable<ResponseEntity> apply(Object[] objects) throws Exception {
                            IdentifyResult identifyResult = (IdentifyResult) objects[0];
                            Integer type = (Integer) objects[1];
                            PersonTable personTable = (PersonTable) objects[2];

                            Map<String, String> params = new HashMap<>();
                            params.put("personId", identifyResult.getPersonId());
                            params.put("faceId", identifyResult.getFaceId());
                            params.put("score", identifyResult.getScore() + "");
                            params.put("name", personTable.getName());
                            params.put("cardNo", personTable.getCardNO());
                            params.put("face", NetHttpUtil.bitmapToBase64(identifyResult.getBitmap()));
                            params.put("type", type + "");

                            return RetrofitManager.getInstance()
                                    .apiService
                                    .sendRecoResult(ApiService.recoCallBackUrl, params);
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())

                    .doOnSubscribe(new Consumer<Disposable>() {
                        @Override
                        public void accept(Disposable disposable) throws Exception {
                            showAlert("快马加鞭开箱子！", true);
                        }
                    })
                    .flatMap(new Function<ResponseEntity, ObservableSource<?>>() {
                        @Override
                        public ObservableSource<?> apply(ResponseEntity responseEntity) throws Exception {
                            showAlert(responseEntity.getResult(), true);
                            // 显示信息之后延时3秒跳转
                            return Observable.just(1).timer(3, TimeUnit.SECONDS);
                        }
                    })
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<Object>() {
                        @Override
                        public void accept(Object o) throws Exception {
                            showAlert("重要提示", true);
                            toMainScreen();
                        }
                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            showAlert("网络似乎开小差了！", true);
                            toMainScreen();
                        }
                    });
            return;
        }
        if (recognition.isAlivePass() && !recognition.isVerifyPass()) {
            Log.i("coreCall", "活体检测通过， 识别没通过");
            return;
        }
        if (!recognition.isAlivePass() && recognition.isVerifyPass()) {
            Log.i("coreCall", "活体检测没通过， 识别通过");
            return;
        }
        if (!recognition.isAlivePass() && !recognition.isVerifyPass()) {
            Log.i("coreCall", "活体检测没通过， 识别没通过");
            return;
        }
    }

    @Override
    public void onTrackStart() {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        etherFaceManager.stopService(this);
        // 关灯
        FileNodeOperator.close(FileNodeOperator.LED_PATH);

        unregisterReceiver(alarmBroadcastReceive);
    }

    @Override
    public void onConnected() {
    }

    @Override
    public void onDisconnected() {

    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Subscribe
    public void setSchoolName(SettingMessageEvent messageEvent) {
        schoolNameView.setText(messageEvent.schooleNameLine1);
    }

    @Override
    protected void onStop() {
        super.onStop();
        cameraRGB.closeCamera();
        cameraIR.closeCamera();

        EventBus.getDefault().unregister(this);
    }

    /**
     * 跳转到主屏幕
     */
    @OnClick(R.id.btn_back)
    public void toMainScreen() {

        firstScreenGroup.setVisibility(View.VISIBLE);
        twoScreenGroup.setVisibility(View.INVISIBLE);
        countDownTimer.stopCount();
        // 设置预览值
        isPreViewCamera = true;

        showAlert("", false);
    }

    /**
     * 跳转到识别屏幕 显示摄像头的那种
     *
     * @param view
     */
    @OnClick({R.id.open, R.id.temp_open, R.id.final_open})
    public void toRecoScreen(View view) {
        // 设置预览值
        isPreViewCamera = false;

        countDownTimer.startCountDown(15);
        firstScreenGroup.setVisibility(View.INVISIBLE);
        twoScreenGroup.setVisibility(View.VISIBLE);
        Voiceutils.playPlayStartReco();
        switch (view.getId()) {
            case R.id.open:
                recoFromWhichButton = 0;
                title.setText("存件");
                break;
            case R.id.temp_open:
                title.setText("中途取件");
                recoFromWhichButton = 1;
                break;
            case R.id.final_open:
                title.setText("取件");
                recoFromWhichButton = 2;
                break;
        }

        queryConbinet();
    }

    /**
     * 显示识别后文字
     */
    public void showAlert(String alert, boolean show) {
        isPreViewCamera = true;
        alertView.setText(alert);
        if (show)
            alertView.setVisibility(View.VISIBLE);
        else
            alertView.setVisibility(View.INVISIBLE);
    }

    /**
     * 查询柜子
     */
    public void queryConbinet() {
        RetrofitManager.getInstance()
                .apiService
                .queryCabinet(ApiService.queryCabinetUrl)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<ResponseCabinetEntity>() {
                    @Override
                    public void accept(ResponseCabinetEntity listResponseEntity) throws Exception {
                        lockerList = listResponseEntity.getResult();
                        initRecycleView();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.i("error", "what error");
                    }
                });
    }

    /**
     * 一个小时刷新一次页面
     */
    public class AlarmBroadcastReceive extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_ALRAM.equals(intent.getAction())) {
                if (adWebView != null)
                    adWebView.reload();
            }
        }
    }




}
