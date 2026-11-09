package io.github.professorengineergit.downlink.globe

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import java.util.concurrent.atomic.AtomicBoolean

/* --- GL auf einer TextureView statt einer SurfaceView: nur so kann der Hintergrund-Blur der Tabbar den Globus erfassen.
   Zeichnet auf Anforderung (requestRender), wie GLSurfaceView im Modus WHEN_DIRTY. --- */
class GLTextureView(ctx: Context) : TextureView(ctx), TextureView.SurfaceTextureListener {
    var renderer: GLSurfaceView.Renderer? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private val pending = AtomicBoolean(false)
    private var ready = false
    /** < 1 rendert in kleinerer Auflösung und lässt die TextureView hochskalieren: spart viel Füllrate. */
    var bufferScale = 1f

    init { surfaceTextureListener = this }

    fun requestRender() {
        if (pending.compareAndSet(false, true)) handler?.post { pending.set(false); draw() }
    }

    private fun draw() {
        if (!ready) return
        renderer?.onDrawFrame(null)
        EGL14.eglSwapBuffers(display, surface)
    }

    private fun initEgl(st: SurfaceTexture) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2); EGL14.eglInitialize(display, v, 0, v, 1)
        val attrs = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_RENDERABLE_TYPE, 0x40 /* ES3 */, EGL14.EGL_NONE)
        val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(display, attrs, 0, cfgs, 0, 1, n, 0)
        config = cfgs[0]
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        surface = EGL14.eglCreateWindowSurface(display, config, st, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surface, surface, context)
    }

    private fun releaseEgl() {
        ready = false
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY; context = EGL14.EGL_NO_CONTEXT; surface = EGL14.EGL_NO_SURFACE
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        val t = HandlerThread("globe-gl").also { it.start() }
        thread = t; handler = Handler(t.looper)
        val bw = (w * bufferScale).toInt().coerceAtLeast(64); val bh = (h * bufferScale).toInt().coerceAtLeast(64)
        if (bufferScale != 1f) st.setDefaultBufferSize(bw, bh)
        handler?.post {
            initEgl(st)
            renderer?.onSurfaceCreated(null, null)
            renderer?.onSurfaceChanged(null, bw, bh)
            ready = true
            draw()
        }
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
        val bw = (w * bufferScale).toInt().coerceAtLeast(64); val bh = (h * bufferScale).toInt().coerceAtLeast(64)
        handler?.post { renderer?.onSurfaceChanged(null, bw, bh); draw() }
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        val h = handler; val t = thread
        h?.post { releaseEgl() }
        t?.quitSafely()
        handler = null; thread = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
}
