package io.github.professorengineergit.downlink.globe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* --- Der Globus in OpenGL ES 3: echte Tag- und Nachtbilder, Sonne, Atmosphäre, Sterne ---
   Die Kamera kreist um die Erde; Compose zeichnet Bahnen und Punkte darüber mit derselben Projektion. */
class GlobeRenderer(private val ctx: Context, private val camera: GlobeCamera) : GLSurfaceView.Renderer {

    @Volatile var sun = floatArrayOf(0f, 0.3f, 1f)
    @Volatile var nightSide = true
    @Volatile var atmosphere = true
    @Volatile var stars = true
    @Volatile private var capture: ((Bitmap) -> Unit)? = null

    private var planet = 0
    private var atmo = 0
    private var starProg = 0
    private var texDay = 0
    private var texNight = 0
    private lateinit var sphereVerts: FloatBuffer
    private lateinit var sphereIdx: ShortBuffer
    private var indexCount = 0
    private lateinit var starVerts: FloatBuffer
    private var width = 1
    private var height = 1
    private val mvp = FloatArray(16)

    fun capture(cb: (Bitmap) -> Unit) { capture = cb }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glClearColor(0.020f, 0.031f, 0.051f, 1f)
        glEnable(GL_DEPTH_TEST)
        planet = program(VS_SPHERE, FS_PLANET)
        atmo = program(VS_SPHERE, FS_ATMO)
        starProg = program(VS_STAR, FS_STAR)
        texDay = texture("earth_day.webp")
        texNight = texture("earth_night.webp")
        buildSphere(96, 192)
        buildStars(1600)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        glViewport(0, 0, w, h)
        camera.viewport(w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        camera.matrix(mvp)
        val eye = camera.eye()

        if (stars) drawStars()

        glUseProgram(planet)
        glUniformMatrix4fv(glGetUniformLocation(planet, "uMVP"), 1, false, mvp, 0)
        glUniform1f(glGetUniformLocation(planet, "uScale"), 1f)
        glUniform3fv(glGetUniformLocation(planet, "uSun"), 1, sun, 0)
        glUniform3f(glGetUniformLocation(planet, "uEye"), eye[0].toFloat(), eye[1].toFloat(), eye[2].toFloat())
        glUniform1f(glGetUniformLocation(planet, "uNightOn"), if (nightSide) 1f else 0f)
        glUniform1f(glGetUniformLocation(planet, "uAtmo"), if (atmosphere) 1f else 0f)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texDay)
        glUniform1i(glGetUniformLocation(planet, "uDay"), 0)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, texNight)
        glUniform1i(glGetUniformLocation(planet, "uNight"), 1)
        glEnable(GL_CULL_FACE); glCullFace(GL_BACK)
        drawSphere(planet)

        if (atmosphere) {
            glUseProgram(atmo)
            glUniformMatrix4fv(glGetUniformLocation(atmo, "uMVP"), 1, false, mvp, 0)
            glUniform1f(glGetUniformLocation(atmo, "uScale"), 1.045f)
            glUniform3fv(glGetUniformLocation(atmo, "uSun"), 1, sun, 0)
            glUniform3f(glGetUniformLocation(atmo, "uEye"), eye[0].toFloat(), eye[1].toFloat(), eye[2].toFloat())
            glUniform1f(glGetUniformLocation(atmo, "uNightOn"), if (nightSide) 1f else 0f)
            glCullFace(GL_FRONT)
            glEnable(GL_BLEND); glBlendFunc(GL_ONE, GL_ONE); glDepthMask(false)
            drawSphere(atmo)
            glDepthMask(true); glDisable(GL_BLEND)
        }
        glDisable(GL_CULL_FACE)

        capture?.let { cb ->
            capture = null
            val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buf)
            val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buf)
            val flip = android.graphics.Matrix().apply { preScale(1f, -1f) }
            val bmp = Bitmap.createBitmap(raw, 0, 0, width, height, flip, false)
            Handler(Looper.getMainLooper()).post { cb(bmp) }
        }
    }

    private fun drawSphere(prog: Int) {
        sphereVerts.position(0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, sphereVerts)
        glEnableVertexAttribArray(0)
        sphereVerts.position(3)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, sphereVerts)
        glEnableVertexAttribArray(1)
        sphereIdx.position(0)
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_SHORT, sphereIdx)
    }

    private fun drawStars() {
        glUseProgram(starProg)
        val view = FloatArray(16)
        camera.rotationOnly(view)
        glUniformMatrix4fv(glGetUniformLocation(starProg, "uMVP"), 1, false, view, 0)
        starVerts.position(0)
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 16, starVerts)
        glEnableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
        glDepthMask(false)
        glEnable(GL_BLEND); glBlendFunc(GL_ONE, GL_ONE)
        glDrawArrays(GL_POINTS, 0, starVerts.capacity() / 4)
        glDisable(GL_BLEND); glDepthMask(true)
    }

    // Kugel mit UV wie die Textur: u = Länge, v = Breite (Norden oben)
    private fun buildSphere(rows: Int, cols: Int) {
        val v = FloatArray((rows + 1) * (cols + 1) * 5)
        var k = 0
        for (r in 0..rows) {
            val lat = 90.0 - 180.0 * r / rows
            for (c in 0..cols) {
                val lon = -180.0 + 360.0 * c / cols
                val p = Geo.vec(lat, lon)
                v[k++] = p[0].toFloat(); v[k++] = p[1].toFloat(); v[k++] = p[2].toFloat()
                v[k++] = c.toFloat() / cols; v[k++] = r.toFloat() / rows
            }
        }
        val idx = ShortArray(rows * cols * 6)
        k = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            val a = r * (cols + 1) + c
            val b = a + cols + 1
            idx[k++] = a.toShort(); idx[k++] = b.toShort(); idx[k++] = (a + 1).toShort()
            idx[k++] = (a + 1).toShort(); idx[k++] = b.toShort(); idx[k++] = (b + 1).toShort()
        }
        indexCount = idx.size
        sphereVerts = floats(v)
        sphereIdx = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(idx).apply { position(0) }
    }

    private fun buildStars(n: Int) {
        val rnd = Random(42)
        val v = FloatArray(n * 4)
        for (i in 0 until n) {
            val z = rnd.nextDouble(-1.0, 1.0)
            val t = rnd.nextDouble(0.0, 2 * PI)
            val r = kotlin.math.sqrt(1 - z * z)
            v[i * 4] = (r * cos(t)).toFloat(); v[i * 4 + 1] = (r * sin(t)).toFloat(); v[i * 4 + 2] = z.toFloat()
            // wenige helle, viele schwache
            v[i * 4 + 3] = (0.15 + 0.85 * Math.pow(rnd.nextDouble(), 6.0)).toFloat()
        }
        starVerts = floats(v)
    }

    private fun floats(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).apply { position(0) }

    private fun texture(asset: String): Int {
        val id = IntArray(1)
        glGenTextures(1, id, 0)
        glBindTexture(GL_TEXTURE_2D, id[0])
        val bmp = ctx.assets.open(asset).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false }) }
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bmp, 0)
        bmp?.recycle()
        glGenerateMipmap(GL_TEXTURE_2D)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        return id[0]
    }

    private fun program(vs: String, fs: String): Int {
        fun shader(type: Int, src: String) = glCreateShader(type).also {
            glShaderSource(it, src); glCompileShader(it)
            val ok = IntArray(1); glGetShaderiv(it, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { glGetShaderInfoLog(it) }
        }
        return glCreateProgram().also {
            glAttachShader(it, shader(GL_VERTEX_SHADER, vs))
            glAttachShader(it, shader(GL_FRAGMENT_SHADER, fs))
            glBindAttribLocation(it, 0, "aPos")
            glBindAttribLocation(it, 1, "aUv")
            glLinkProgram(it)
        }
    }

    companion object {
        private const val VS_SPHERE = """#version 300 es
uniform mat4 uMVP;
uniform float uScale;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
out vec3 vN;
out vec2 vUv;
void main() {
  vN = aPos;
  vUv = aUv;
  gl_Position = uMVP * vec4(aPos * uScale, 1.0);
}"""

        private const val FS_PLANET = """#version 300 es
precision highp float;
uniform sampler2D uDay;
uniform sampler2D uNight;
uniform vec3 uSun;
uniform vec3 uEye;
uniform float uNightOn;
uniform float uAtmo;
in vec3 vN;
in vec2 vUv;
out vec4 outColor;
void main() {
  vec3 n = normalize(vN);
  vec3 v = normalize(uEye - n);
  float d = dot(n, uSun);
  vec3 day = texture(uDay, vUv).rgb;
  vec3 night = texture(uNight, vUv).rgb;
  float k = mix(1.0, smoothstep(-0.12, 0.18, d), uNightOn);
  vec3 dayLit = day * mix(1.0, 0.18 + 1.05 * clamp(d + 0.08, 0.0, 1.0), uNightOn);
  vec3 nightLit = night * 1.7 + day * 0.025;
  vec3 c = mix(nightLit, dayLit, k);
  // Glanz nur auf dem Wasser
  float water = smoothstep(0.02, 0.18, day.b - day.r);
  vec3 h = normalize(uSun + v);
  c += water * pow(max(dot(n, h), 0.0), 60.0) * 0.45 * k * uNightOn;
  float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
  c += vec3(0.32, 0.68, 1.0) * rim * 0.55 * uAtmo * mix(0.2, 1.0, k);
  outColor = vec4(c, 1.0);
}"""

        private const val FS_ATMO = """#version 300 es
precision highp float;
uniform vec3 uSun;
uniform vec3 uEye;
uniform float uNightOn;
in vec3 vN;
in vec2 vUv;
out vec4 outColor;
void main() {
  vec3 n = normalize(vN);
  vec3 v = normalize(uEye - n * 1.045);
  float edge = 1.0 - abs(dot(n, v));
  float glow = pow(edge, 5.0);
  float lit = mix(1.0, smoothstep(-0.35, 0.35, dot(n, uSun)), uNightOn);
  outColor = vec4(vec3(0.30, 0.62, 1.0) * glow * (0.25 + 0.95 * lit), 1.0);
}"""

        private const val VS_STAR = """#version 300 es
uniform mat4 uMVP;
layout(location = 0) in vec4 aPos;
out float vB;
void main() {
  vB = aPos.w;
  gl_Position = uMVP * vec4(aPos.xyz * 50.0, 1.0);
  gl_PointSize = 1.5 + 2.5 * aPos.w;
}"""

        private const val FS_STAR = """#version 300 es
precision mediump float;
in float vB;
out vec4 outColor;
void main() {
  float r = length(gl_PointCoord - 0.5) * 2.0;
  float a = exp(-r * r * 4.0);
  outColor = vec4(vec3(0.85, 0.92, 1.0) * vB * a, 1.0);
}"""
    }
}

/* --- Kamera auf einer Kugelbahn um die Erde; dieselbe Mathematik für GL und Compose --- */
class GlobeCamera {
    @Volatile var lat = 20.0
    @Volatile var lon = 0.0
    @Volatile var dist = 3.2
    @Volatile var offsetX = 0f
    @Volatile var offsetY = 0f // verschiebt die Erde im Bild nach oben oder unten
    var placed = false
    private var aspect = 1f
    var widthPx = 1; private set
    var heightPx = 1; private set
    private val proj = FloatArray(16)

    fun viewport(w: Int, h: Int) {
        widthPx = w
        heightPx = h
        aspect = w.toFloat() / h
    }

    fun eye(): DoubleArray {
        val v = Geo.vec(lat.coerceIn(-85.0, 85.0), lon)
        return doubleArrayOf(v[0] * dist, v[1] * dist, v[2] * dist)
    }

    fun matrix(out: FloatArray) {
        val e = eye()
        val view = FloatArray(16)
        Matrix.setLookAtM(view, 0, e[0].toFloat(), e[1].toFloat(), e[2].toFloat(), 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.perspectiveM(proj, 0, 34f, aspect, 0.05f, 100f)
        val shift = FloatArray(16)
        Matrix.setIdentityM(shift, 0)
        Matrix.translateM(shift, 0, offsetX, offsetY, 0f)
        val pv = FloatArray(16)
        Matrix.multiplyMM(pv, 0, proj, 0, view, 0)
        Matrix.multiplyMM(out, 0, shift, 0, pv, 0)
    }

    fun rotationOnly(out: FloatArray) {
        val e = eye()
        val view = FloatArray(16)
        Matrix.setLookAtM(view, 0, e[0].toFloat(), e[1].toFloat(), e[2].toFloat(), 0f, 0f, 0f, 0f, 1f, 0f)
        view[12] = 0f; view[13] = 0f; view[14] = 0f
        Matrix.perspectiveM(proj, 0, 34f, aspect, 0.05f, 100f)
        Matrix.multiplyMM(out, 0, proj, 0, view, 0)
    }

    // Bildschirmposition eines Punkts auf der Erde, null wenn er hinter dem Horizont liegt
    fun project(p: DoubleArray, out: FloatArray = FloatArray(2)): FloatArray? {
        val e = eye()
        if (p[0] * e[0] + p[1] * e[1] + p[2] * e[2] < 1.0) return null
        val m = FloatArray(16)
        matrix(m)
        val r = FloatArray(4)
        Matrix.multiplyMV(r, 0, m, 0, floatArrayOf(p[0].toFloat(), p[1].toFloat(), p[2].toFloat(), 1f), 0)
        if (r[3] <= 0f) return null
        out[0] = (r[0] / r[3] * 0.5f + 0.5f) * widthPx
        out[1] = (1f - (r[1] / r[3] * 0.5f + 0.5f)) * heightPx
        return out
    }
}
