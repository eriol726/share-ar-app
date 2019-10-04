package fr.pchab.androidrtc;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


import android.support.v4.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.MediaStream;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import common.rendering.BackgroundRenderer;
import common.rendering.ObjectRenderer;
import common.rendering.PlaneRenderer;
import common.rendering.PointCloudRenderer;
import fr.pchab.webrtcclient.PeerConnectionParameters;
import fr.pchab.webrtcclient.WebRtcClient;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
//import com.google.ar.sceneform.AnchorNode;
//import com.google.ar.sceneform.rendering.ModelRenderable;
//import com.google.ar.sceneform.ux.ArFragment;
//import com.google.ar.sceneform.ux.TransformableNode;

import common.helpers.CameraPermissionHelper;
import common.helpers.DisplayRotationHelper;
import common.helpers.FullScreenHelper;
import common.helpers.SnackbarHelper;
import common.helpers.TapHelper;
import common.helpers.TrackingStateHelper;


import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class RtcActivity extends AppCompatActivity implements WebRtcClient.RtcListener, GLSurfaceView.Renderer{

    private final static String TAG = WebRtcClient.class.getCanonicalName();
    private final static int VIDEO_CALL_SENT = 666;
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 0;
    private static final int LOCAL_Y_CONNECTED = 0;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private VideoRenderer.I420Frame I420Frame;
    private WebRtcClient client;
    private String mSocketAddress;
    private String callerId;
    private String readyCallID;
    private String type;
    private JSONObject payload = new JSONObject();
    TextView txtJson;
    ProgressDialog pd;
    private static final double MIN_OPENGL_VERSION = 3.0;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};

    private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";

    private boolean done = false;
    private boolean hasAcandidate = false;

    // Anchors created from taps used for object placing with a given color.
    private static class ColoredAnchor {
        public final Anchor anchor;
        public final float[] color;

        public ColoredAnchor(Anchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }

    private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();

    Button connectButton;
    Button disconnectButton;
    Button showTypeButton;

    private static final String[] RequiredPermissions = new String[]{Manifest.permission.CAMERA};
    protected PermissionChecker permissionChecker = new PermissionChecker();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_KEEP_SCREEN_ON
                        | LayoutParams.FLAG_DISMISS_KEYGUARD
                        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.main);


        connectButton = (Button)findViewById(R.id.connectButton);
        disconnectButton = (Button)findViewById(R.id.disconnectButton);
        showTypeButton = (Button)findViewById(R.id.showType);



        connectButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                Log.d(TAG, "readyCallID: " + readyCallID);
                Log.d(TAG, "callerID: " + callerId);
                Log.d(TAG, "payload: " + payload);
                connectButton.setText("searching");

                new JsonTask().execute("http://192.168.2.194:3000/streams.json");

                if(callerId != null){
                    try {
                        answer(callerId, payload);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                else if(!hasAcandidate){
                    //callerId = readyCallID;
                    startCam();
                }



//                if(payload.length() != 0) {
//                    try {
//                        answer(callerId, payload);
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }else{
//                    try {
//
//                        offer(readyCallID, payload);
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }


            }
        });

        showTypeButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                String s = client.getPeerType(callerId);

                if(s == "ANSWER"){
                    VideoRendererGui.update(remoteRender,
                            LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                            LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                            scalingType, false);
                }
                else{

                }
                Log.d(TAG, "readyCallID: " + readyCallID);
                Log.d(TAG, "callerID: " + callerId);
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                client.onRemove(callerId);
            }
        });

        mSocketAddress = "http://" + getResources().getString(R.string.host);
        mSocketAddress += (":" + getResources().getString(R.string.port) + "/");

        surfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setKeepScreenOn(true);


        //final List<String> segments = intent.getData().getPathSegments();


//        Log.d(TAG, "action: " + action);
//        Log.d(TAG, "intent: " + intent.getData());
//        Log.d(TAG, "Intent.action: " + Intent.ACTION_VIEW.equals(action));
//        Log.d(TAG, "Intent.ACTION_VIEW: " + Intent.ACTION_VIEW);


        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Set up tap listener.
        tapHelper = new TapHelper(/*context=*/ this);
        surfaceView.setOnTouchListener(tapHelper);


        // Set up renderer.
//        surfaceView.setPreserveEGLContextOnPause(true);
//        surfaceView.setEGLContextClientVersion(2);
//        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
//        surfaceView.setRenderer(this);
//        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
//        surfaceView.setWillNotDraw(false);
//
        VideoRendererGui.setView(surfaceView, new Runnable() {
            @Override
            public void run() {
                // init the web rtc client
                init();
            }
        });



        //init();
//
//        // local and remote render


        remoteRender =  VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            final List<String> segments = intent.getData().getPathSegments();
            callerId = segments.get(0);
        }

        //   init();
        checkPermissions();
        installRequested = false;

    }



    private void checkPermissions() {
        permissionChecker.verifyPermissions(this, RequiredPermissions, new PermissionChecker.VerifyPermissionsCallback() {

            @Override
            public void onPermissionAllGranted() {

            }

            @Override
            public void onPermissionDeny(String[] permissions) {
                Toast.makeText(RtcActivity.this, "Please grant required permissions.", Toast.LENGTH_LONG).show();
            }
        });
    }

    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    private void init() {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        PeerConnectionParameters params = new PeerConnectionParameters(
                true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);

        client = new WebRtcClient(this, mSocketAddress, params, surfaceView);


    }

    @Override
    public void onPause() {
        super.onPause();
        surfaceView.onPause();
        if (client != null) {
            client.onPause();
        }

        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        surfaceView.onResume();
        if (client != null) {
            client.onResume();
        }

//        if (session == null) {
//            Exception exception = null;
//            String message = null;
//            try {
//                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
//                    case INSTALL_REQUESTED:
//                        installRequested = true;
//                        return;
//                    case INSTALLED:
//                        break;
//                }
//
//                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
//                // permission on Android M and above, now is a good time to ask the user for it.
//                if (!CameraPermissionHelper.hasCameraPermission(this)) {
//                    CameraPermissionHelper.requestCameraPermission(this);
//                    return;
//                }
//
//                // Create the session.
//                session = new Session(/* context= */ this);
//
//            } catch (UnavailableArcoreNotInstalledException
//                    | UnavailableUserDeclinedInstallationException e) {
//                message = "Please install ARCore";
//                exception = e;
//            } catch (UnavailableApkTooOldException e) {
//                message = "Please update ARCore";
//                exception = e;
//            } catch (UnavailableSdkTooOldException e) {
//                message = "Please update this app";
//                exception = e;
//            } catch (UnavailableDeviceNotCompatibleException e) {
//                message = "This device does not support AR";
//                exception = e;
//            } catch (Exception e) {
//                message = "Failed to create AR session";
//                exception = e;
//            }
//
//            if (message != null) {
//                messageSnackbarHelper.showError(this, message);
//                Log.e(TAG, "Exception creating session", exception);
//                return;
//            }
//        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
//        try {
//            Log.d(TAG,"test");
//            session.resume();
//        } catch (CameraNotAvailableException e) {
//            // In some cases (such as another camera app launching) the camera may be given to
//            // a different app instead. Handle this properly by showing a message and recreate the
//            // session at the next iteration.
//            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
//            session = null;
//            return;
//        }

        //surfaceView.onResume();
        //displayRotationHelper.onResume();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy");
        if (client != null) {
            client.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onCallReady(String callId) {
        readyCallID = callId;
        Log.d(TAG,"readyCallID: " + readyCallID);
        if (callerId != null) {
            Log.d(TAG,"callerId is not null: " + callerId);
//            try {
//                answer(callerId, payload);
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
        } else {
           /* try {
                answer(callId);
            } catch (JSONException e) {
                e.printStackTrace();
            }*/
           // startCam();
          // call(callId);

        }
       // startCam();
    }

    public void answer(String callerId, JSONObject payload) throws JSONException {
        String deviceName = getDeviceName();
        client.sendMessage(callerId, "init", null, deviceName );
        //startCam();
    }

    public void offer(String callerId, JSONObject payload) throws JSONException {
        String deviceName = getDeviceName();
        //startCam();
        client.sendMessage(callerId, "init", payload, deviceName );

    }

    public void call(String callId) {

//        Intent msg = new Intent(Intent.ACTION_SEND);
//        msg.putExtra(Intent.EXTRA_TEXT, mSocketAddress + callId + "5555");
//        msg.setType("text/plain");
//        startActivityForResult(Intent.createChooser(msg, "Call someone :"), VIDEO_CALL_SENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VIDEO_CALL_SENT) {
            startCam();
        }
    }

    public void startCam() {
        // Camera settings
        if (PermissionChecker.hasPermissions(this, RequiredPermissions)) {
            BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();
            String deviceName = getDeviceName();
            client.start(deviceName);
        }
    }


    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onLocalStream(MediaStream localStream) {
        Log.d(TAG, "onLocalStream: ");

        BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();
        String deviceName = getDeviceName();
        if(deviceName.equals("Samsung SM-G950F")) {
            localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));

            VideoRendererGui.update(localRender,
                    LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                    LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                    scalingType, false);
        }
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {

        //new JsonTask().execute("http://192.168.2.194:3000/streams.json");
        Log.d(TAG, "onAddRemoteStream type: " + type);
        Log.d(TAG, "endPoint: " + endPoint);



        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                connectButton.setText("connected");

            }
        });



        BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();
        String deviceName = getDeviceName();
        if(deviceName.equals("Samsung SM-T830")){
            remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));

            VideoRendererGui.update(localRender,
                    LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                    25, 25, scalingType, false);

            VideoRendererGui.update(remoteRender,
                    REMOTE_X, REMOTE_Y,
                    REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        }else{
            Log.d(TAG, "deviceName: " + deviceName);
        }

//








    }

    @Override
    public void onRemoveRemoteStream(int endPoint) {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                connectButton.setText("connect");
            }
        });
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType, false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        permissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
            planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(/*context=*/ this);

            virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png");
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

            virtualObjectShadow.createOnGlThread(
                    /*context=*/ this, "models/andy_shadow.obj", "models/andy_shadow.png");
            virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow);
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            Camera camera = frame.getCamera();

            // Handle one tap per frame.
            handleTap(frame, camera);

            // If frame is ready, render camera preview image to the GL surface.
            backgroundRenderer.draw(frame);

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            // If not tracking, don't draw 3D objects, show tracking failure reason instead.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                messageSnackbarHelper.showMessage(
                        this, TrackingStateHelper.getTrackingFailureReasonString(camera));
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // Visualize tracked points.
            // Use try-with-resources to automatically release the point cloud.
            try (PointCloud pointCloud = frame.acquirePointCloud()) {
                pointCloudRenderer.update(pointCloud);
                pointCloudRenderer.draw(viewmtx, projmtx);
            }

            // No tracking error at this point. If we detected any plane, then hide the
            // message UI, otherwise show searchingPlane message.
            if (hasTrackingPlane()) {
                messageSnackbarHelper.hide(this);
            } else {
                messageSnackbarHelper.showMessage(this, SEARCHING_PLANE_MESSAGE);
            }

            // Visualize planes.
            planeRenderer.drawPlanes(
                    session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

            // Visualize anchors created by touch.
            float scaleFactor = 1.0f;
            for (ColoredAnchor coloredAnchor : anchors) {
                if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }
                // Get the current pose of an Anchor in world space. The Anchor pose is updated
                // during calls to session.update() as ARCore refines its estimate of the world.
                coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);

                // Update and draw the model and its shadow.
                virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
                virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
                virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                Trackable trackable = hit.getTrackable();
                // Creates an anchor if a plane or an oriented point was hit.
                if ((trackable instanceof Plane
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof com.google.ar.core.Point
                        && ((com.google.ar.core.Point) trackable).getOrientationMode()
                        == com.google.ar.core.Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size() >= 20) {
                        anchors.get(0).anchor.detach();
                        anchors.remove(0);
                    }

                    // Assign a color to the object for rendering based on the trackable type
                    // this anchor attached to. For AR_TRACKABLE_POINT, it's blue color, and
                    // for AR_TRACKABLE_PLANE, it's green color.
                    float[] objColor;
                    if (trackable instanceof com.google.ar.core.Point) {
                        objColor = new float[] {66.0f, 133.0f, 244.0f, 255.0f};
                    } else if (trackable instanceof Plane) {
                        objColor = new float[] {139.0f, 195.0f, 74.0f, 255.0f};
                    } else {
                        objColor = DEFAULT_COLOR;
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    anchors.add(new ColoredAnchor(hit.createAnchor(), objColor));
                    break;
                }
            }
        }
    }
    /** Checks if we detected at least one plane. */
    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) {
                return true;
            }
        }
        return false;
    }


    private class JsonTask extends AsyncTask<String, Void, JSONArray> {

        @Override
        protected JSONArray doInBackground(String... params)
        {

            String str= params[0];
            URLConnection urlConn = null;
            BufferedReader bufferedReader = null;
            try
            {
                URL url = new URL(str);
                urlConn = url.openConnection();
                bufferedReader = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));

                StringBuffer stringBuffer = new StringBuffer();
                String line;
                while ((line = bufferedReader.readLine()) != null)
                {
                    stringBuffer.append(line);
                }

                return new JSONArray(stringBuffer.toString());
            }
            catch(Exception ex)
            {
                Log.e("App", "yourDataTask", ex);
                return null;
            }
            finally
            {
                if(bufferedReader != null)
                {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(JSONArray response)
        {
            if(response.length() > 0)
            {
                try {
                    Log.e("App", "Success: " + response.getJSONObject(0).getString("id") );
                    hasAcandidate = true;
                    for(int i = 0; i < response.length(); i++){
                        if(response.getJSONObject(i).getString("id") != readyCallID){
                            callerId = response.getJSONObject(i).getString("id");
                            type = response.getJSONObject(i).getString("type");
                            //payload = response.getJSONObject(i).getJSONObject("payload");
                            payload = null;

                            break;
                        }

                    }

                } catch (JSONException ex) {
                    Log.e("App", "Failure", ex);
                }
            }
        }
    }

    /** Returns the consumer friendly device name */
    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }

        return phrase.toString();
    }
}