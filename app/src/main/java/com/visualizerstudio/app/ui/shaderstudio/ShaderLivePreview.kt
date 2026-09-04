package com.visualizerstudio.app.ui.shaderstudio

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.visualizerstudio.app.render.gl.ShaderCategory
import com.visualizerstudio.app.render.gl.ShaderValidator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Preview langsung (`GLSurfaceView`) dengan data audio sample sebelum disimpan (section 5.1g /
 * 6.2). Kode wizard "Mode Template" mentah (#version 330) dikonversi dulu lewat [ShaderValidator]
 * lalu di-compile ke GLSurfaceView untuk animasi live — dipakai supaya user bisa lihat hasil
 * sebelum menekan "Generate & Validasi" final.
 */
@Composable
fun ShaderLivePreview(
    rawGlsl330: String,
    category: ShaderCategory,
    modifier: Modifier = Modifier.fillMaxWidth().height(180.dp)
) {
    val validator = remember { ShaderValidator() }
    val renderer = remember { LiveShaderRenderer(rawGlsl330, category, validator) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        update = { renderer.updateSource(rawGlsl330) }
    )
}

private class LiveShaderRenderer(
    initialSource: String,
    private val category: ShaderCategory,
    private val validator: ShaderValidator
) : GLSurfaceView.Renderer {

    @Volatile private var pendingSource: String = initialSource
    private var program = 0
    private val startTime = System.nanoTime()
    private val sampleBars = FloatArray(120)
    private val quadBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
        }

    fun updateSource(source: String) { pendingSource = source }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        compileFromPending()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    private var lastCompiled = ""

    override fun onDrawFrame(gl: GL10?) {
        if (pendingSource != lastCompiled) compileFromPending()
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (program == 0) return

        val t = (System.nanoTime() - startTime) / 1_000_000_000f
        for (i in sampleBars.indices) {
            sampleBars[i] = (0.4f + 0.5f * abs(sin(t * 1.3 + i * 0.15))).toFloat()
        }

        GLES30.glUseProgram(program)
        setF2(program, "u_resolution", 256f, 180f)
        setF1(program, "u_time", t)
        setF1Array(program, "u_bars", sampleBars)
        setI1(program, "u_num_bars", 120)
        setF3(program, "u_color", 1f, 1f, 1f)
        setF1(program, "u_zoom_factor", (0.5f + 0.5f * sin(t.toDouble())).toFloat())
        setF1(program, "u_shear_x", 0f)
        setF1(program, "u_shear_y", 0f)
        setF1(program, "u_total_duration", 60f)
        setF1(program, "u_loop_duration", 8f)
        setI1(program, "u_effect_mode", 0)

        val posLoc = GLES30.glGetAttribLocation(program, "a_position")
        if (posLoc >= 0) {
            GLES30.glEnableVertexAttribArray(posLoc)
            GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 0, quadBuffer)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glDisableVertexAttribArray(posLoc)
        }
    }

    private fun compileFromPending() {
        lastCompiled = pendingSource
        val result = validator.validateAndConvert(pendingSource, category)
        val es = result.glslEs ?: return
        val vertexSrc = """
            #version 300 es
            in vec2 a_position;
            out vec2 v_uv;
            void main() {
                v_uv = a_position * 0.5 + 0.5;
                gl_Position = vec4(a_position, 0.0, 1.0);
            }
        """.trimIndent()
        program = linkProgram(vertexSrc, es)
    }

    private fun linkProgram(vertexSrc: String, fragSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        if (vs == 0 || fs == 0) return 0
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        val status = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
        return if (status[0] != 0) prog else 0
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        return if (status[0] != 0) shader else 0
    }

    private fun setF1(p: Int, n: String, v: Float) { val l = GLES30.glGetUniformLocation(p, n); if (l >= 0) GLES30.glUniform1f(l, v) }
    private fun setF2(p: Int, n: String, a: Float, b: Float) { val l = GLES30.glGetUniformLocation(p, n); if (l >= 0) GLES30.glUniform2f(l, a, b) }
    private fun setF3(p: Int, n: String, a: Float, b: Float, c: Float) { val l = GLES30.glGetUniformLocation(p, n); if (l >= 0) GLES30.glUniform3f(l, a, b, c) }
    private fun setI1(p: Int, n: String, v: Int) { val l = GLES30.glGetUniformLocation(p, n); if (l >= 0) GLES30.glUniform1i(l, v) }
    private fun setF1Array(p: Int, n: String, v: FloatArray) { val l = GLES30.glGetUniformLocation(p, n); if (l >= 0) GLES30.glUniform1fv(l, v.size, v, 0) }
}
