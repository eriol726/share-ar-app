package xyz.vivekc.webrtccodelab;

import android.content.Context;
import android.opengl.GLSurfaceView;

class MyGLSurfaceView extends GLSurfaceView {

    private final MyGLRenderer renderer;

    public MyGLSurfaceView(Context context) {
        super(context);
       // this.renderer = renderer;

        renderer = new MyGLRenderer(context);

        // Set up renderer.
        setPreserveEGLContextOnPause(true);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        setRenderer(renderer);

    }
}
