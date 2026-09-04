package com.visualizerstudio.app.render.ffmpeg

import android.net.Uri
import com.visualizerstudio.app.domain.model.BgVisualEffect
import com.visualizerstudio.app.domain.model.OverlayAssetConfig
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.SlowMotionGroupConfig
import com.visualizerstudio.app.domain.model.SpectrumConfig
import com.visualizerstudio.app.domain.model.SpectrumType
import com.visualizerstudio.app.util.FfmpegUtils
import java.io.File
import javax.inject.Inject

/**
 * Menyusun filter graph multi-layer utama (section 5.2 blueprint) + argumen FFmpeg lengkap
 * siap dieksekusi [FfmpegSessionRunner] — porting 1:1 dari blok `filter_complex` di
 * `render_task_pipeline` versi Python sumber, disesuaikan ke [RenderTask] (Appendix A.3) dan
 * Uri Android (SAF) alih-alih path filesystem langsung.
 *
 * Urutan layer (persis versi sumber): bg_base (crop+scale+setpts+fps+slowmo+reverse+beatzoom)
 * -> bg visual effect (vignette/blur/grayscale/sepia) -> overlay asset (luma-key compositing,
 * fade in/out, shear, speed beat sync) -> spectrum (native filter ATAU video alpha shader
 * kustom dari Fase 3 via [CustomSpectrumRenderer], sudah di-pre-render sebelum builder ini
 * dipanggil) -> drawtext intro/title.
 */
class FfmpegFilterGraphBuilder @Inject constructor(
    private val safPathResolver: SafPathResolver,
    private val introTitleFilterBuilder: IntroTitleFilterBuilder,
    private val encoderDetector: HardwareEncoderDetector
) {

    /** Kumpulan input yang tidak bisa disimpulkan langsung dari [RenderTask] saja. */
    data class BuildInput(
        val task: RenderTask,
        /** Path/param siap-pakai untuk visual utama: file timeline lokal ATAU `-i` param dari [SafPathResolver] kalau media tunggal berupa Uri content. */
        val mainVisualReadParam: String,
        val mainVisualIsPicture: Boolean,
        /** true kalau [mainVisualReadParam] adalah file timeline gabungan (hasil [MediaTimelineBuilder]) — selalu diperlakukan sebagai video, tidak pernah "gambar". */
        val mainVisualIsTimeline: Boolean,
        val mainVisualDurationSec: Float,
        val mainVisualKeyForSlowMotion: String,
        val concatAudioReadParam: String,
        val renderDurationSec: Float,
        val resWidth: Int,
        val resHeight: Int,
        val fps: Int,
        /** idx (indeks di `task.spectrums`) -> path lokal video alpha hasil render Fase 3, HANYA untuk spectrum ber-`specType == CUSTOM_SHADER`. */
        val customSpecVideoPaths: Map<Int, String>,
        val encoderSelection: EncoderSelection,
        /** path/param output siap-pakai (lokal ATAU hasil [SafPathResolver.resolveForWrite]). */
        val outputWriteParam: String
    )

    private fun resolveSlowMotionSpeed(
        key: String,
        groupCfg: SlowMotionGroupConfig?,
        perFileCfg: Map<String, SlowMotionGroupConfig>
    ): Float {
        perFileCfg[key]?.let { if (it.active) return it.speed }
        if (groupCfg?.active == true) return groupCfg.speed
        return 1.0f
    }

    private fun buildSlowMotionSuffix(speed: Float, fps: Int): String {
        if (speed <= 0f || speed >= 1.0f) return ""
        val ptsFactor = 1.0 / speed
        return ",setpts=%.6f*PTS,framerate=fps=$fps:interp_start=0:interp_end=255:scene=100".format(ptsFactor)
    }

    suspend fun buildArgs(input: BuildInput): List<String> {
        val task = input.task
        val resW = input.resWidth
        val resH = input.resHeight
        val fps = input.fps
        val renderDuration = input.renderDurationSec

        // ---------------- 1. INPUTS ----------------
        val inputs = mutableListOf<String>()

        val isPicture = input.mainVisualIsPicture && !input.mainVisualIsTimeline
        val mainSpeed = if (isPicture) 1.0f else resolveSlowMotionSpeed(
            input.mainVisualKeyForSlowMotion,
            task.slowMotionConfig.visualGroup,
            task.slowMotionConfig.perFile
        )
        val mainReverseActive = !isPicture && task.reversePlayConfig.active

        when {
            isPicture -> inputs += listOf(
                "-thread_queue_size", "16384", "-loop", "1", "-framerate", "1",
                "-t", renderDuration.toString(), "-i", input.mainVisualReadParam
            )
            mainReverseActive -> inputs += listOf(
                "-thread_queue_size", "16384", "-an", "-sn", "-i", input.mainVisualReadParam
            )
            else -> inputs += listOf(
                "-thread_queue_size", "16384", "-stream_loop", "-1", "-an", "-sn", "-i", input.mainVisualReadParam
            )
        }

        if (task.loops > 1) {
            inputs += listOf(
                "-thread_queue_size", "16384", "-stream_loop", (task.loops - 1).toString(),
                "-t", renderDuration.toString(), "-i", input.concatAudioReadParam
            )
        } else {
            inputs += listOf("-thread_queue_size", "16384", "-t", renderDuration.toString(), "-i", input.concatAudioReadParam)
        }

        val validOverlays = task.bgVideoOverlays.filter { isValidOverlayUri(it.fileUri) }
        val overlayInputStartIdx = 2
        validOverlays.forEach { ov ->
            val readParam = safPathResolver.resolveForRead(ov.fileUri)
            val name = safPathResolver.displayPath(ov.fileUri).lowercase()
            when {
                isPictureName(name) -> inputs += listOf(
                    "-thread_queue_size", "16384", "-loop", "1", "-framerate", "1",
                    "-t", renderDuration.toString(), "-i", readParam
                )
                ov.loop -> inputs += listOf(
                    "-thread_queue_size", "16384", "-stream_loop", "-1",
                    "-t", renderDuration.toString(), "-i", readParam
                )
                else -> inputs += listOf(
                    "-thread_queue_size", "16384", "-t", renderDuration.toString(), "-i", readParam
                )
            }
        }

        val customSpecInputIndices = mutableMapOf<Int, Int>()
        var nextInputIdx = overlayInputStartIdx + validOverlays.size
        input.customSpecVideoPaths.toSortedMap().forEach { (idx, path) ->
            inputs += listOf("-thread_queue_size", "16384", "-i", path)
            customSpecInputIndices[idx] = nextInputIdx
            nextInputIdx += 1
        }

        // ---------------- 2. FILTER GRAPH ----------------
        val filters = mutableListOf<String>()

        val zoomExpr = if (task.useBeatZoom) {
            ",zoompan=z='min(max(zoom,1.0)+0.0015*sin(time*3.1415*2),1.08)':x='iw/2-(iw/zoom/2)':" +
                "y='ih/2-(ih/zoom/2)':d=1:s=${resW}x${resH}:fps=$fps"
        } else ""

        val crop = task.cropConfig
        val cropExpr = if (crop.isActive) {
            "crop=iw-${crop.left}-${crop.right}:ih-${crop.top}-${crop.bottom}:${crop.left}:${crop.top},"
        } else ""

        val mainSlowmoSuffix = buildSlowMotionSuffix(mainSpeed, fps)

        when {
            isPicture -> filters += "[0:v]${cropExpr}scale=$resW:$resH:force_original_aspect_ratio=increase:eval=init," +
                "crop=$resW:$resH,setpts=PTS-STARTPTS,settb=AVTB,fps=$fps$zoomExpr[bg_base]"

            mainReverseActive -> {
                val mainDur = if (input.mainVisualDurationSec > 0f) input.mainVisualDurationSec else renderDuration
                val effectiveDur = if (mainSpeed < 1.0f) mainDur / mainSpeed else mainDur
                val boomerangFrames = maxOf(2, Math.round(2 * effectiveDur * fps))
                filters += "[0:v]${cropExpr}scale=$resW:$resH:force_original_aspect_ratio=increase:eval=init," +
                    "crop=$resW:$resH,setpts=PTS-STARTPTS,settb=AVTB,fps=$fps$mainSlowmoSuffix,split[fwd_v][rev_src];" +
                    "[rev_src]reverse[rev_v];" +
                    "[fwd_v][rev_v]concat=n=2:v=1:a=0,loop=loop=-1:size=$boomerangFrames:start=0," +
                    "setpts=N/FRAME_RATE/TB$zoomExpr[bg_base]"
            }

            else -> filters += "[0:v]${cropExpr}scale=$resW:$resH:force_original_aspect_ratio=increase:eval=init," +
                "crop=$resW:$resH,setpts=N/FRAME_RATE/TB,settb=AVTB,fps=$fps$mainSlowmoSuffix$zoomExpr[bg_base]"
        }

        var currentNode = "[bg_base]"

        when (task.bgVisualEffect) {
            BgVisualEffect.VIGNETTE -> { filters += "${currentNode}vignette[bg_video_effect]"; currentNode = "[bg_video_effect]" }
            BgVisualEffect.BLUR -> { filters += "${currentNode}boxblur=10:5[bg_video_effect]"; currentNode = "[bg_video_effect]" }
            BgVisualEffect.GRAYSCALE -> { filters += "${currentNode}hue=s=0[bg_video_effect]"; currentNode = "[bg_video_effect]" }
            BgVisualEffect.SEPIA -> {
                filters += "${currentNode}colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131[bg_video_effect]"
                currentNode = "[bg_video_effect]"
            }
            BgVisualEffect.NONE -> { /* tidak ada efek */ }
        }

        val speedCfg = task.overlaySpeedConfig
        validOverlays.forEachIndexed { idx, ov ->
            val inputIdx = overlayInputStartIdx + idx

            val ptsSpeed = if (speedCfg.active && idx in speedCfg.targetOverlayIndices) {
                "(PTS-STARTPTS)*(0.7+0.6*abs(sin(N/20)))"
            } else "PTS-STARTPTS"

            val ovSpeed = resolveSlowMotionSpeed(ov.fileUri.toString(), task.slowMotionConfig.overlayGroup, task.slowMotionConfig.perFile)
            val ovSlowmoSuffix = buildSlowMotionSuffix(ovSpeed, fps)

            var ovShearFilter = ""
            var ovOverlayX = "0"
            var ovOverlayY = "0"
            if (ov.shearX != 0f || ov.shearY != 0f) {
                ovShearFilter = ",shear=shx=${ov.shearX}:shy=${ov.shearY}:fillcolor=0x00000000"
                ovOverlayX = "0 - (w-$resW)/2"
                ovOverlayY = "0 - (h-$resH)/2"
            }

            val ovRaw = "[ov_raw_$idx]"; val ovRgb = "[ov_rgb_$idx]"
            val ovMaskSrc = "[ov_mask_src_$idx]"; val ovMask = "[ov_mask_$idx]"
            val ovClean = "[ov_clean_$idx]"; val nextBg = "[bg_with_ov_$idx]"

            val ovDuration = 10f // durasi asli overlay tidak diprobe di sini (I/O berat); fade-out dihitung konservatif terhadap durasi render.
            val effectiveOvDuration = if (ovSpeed < 1.0f) ovDuration / ovSpeed else ovDuration
            val fadeOutDur = 2.0f
            val fadeOutStart = maxOf(0f, minOf(effectiveOvDuration, renderDuration) - fadeOutDur)

            var fadeFilter = "fade=t=in:st=${ov.delaySeconds}:d=${ov.fadeInSeconds}:alpha=1"
            if (!ov.loop) fadeFilter += ",fade=t=out:st=$fadeOutStart:d=$fadeOutDur:alpha=1"

            val lumaKeyFilter = if (ov.autoLumaKey) {
                "$ovMaskSrc" + "format=gray,lutyuv=y='if(lt(val,22),0,min(255,val*1.25))*${ov.opacity}'$ovMask"
            } else {
                "$ovMaskSrc" + "format=gray,lutyuv=y='255*${ov.opacity}'$ovMask"
            }

            filters += "[$inputIdx:v]setpts='$ptsSpeed'$ovSlowmoSuffix,scale=$resW:$resH:force_original_aspect_ratio=increase," +
                "crop=$resW:$resH,format=rgba$ovShearFilter$ovRaw"
            filters += "${ovRaw}split$ovRgb$ovMaskSrc"
            filters += lumaKeyFilter
            filters += "$ovRgb${ovMask}alphamerge,$fadeFilter$ovClean"
            filters += "$currentNode${ovClean}overlay=x='$ovOverlayX':y='$ovOverlayY':eof_action=pass$nextBg"
            currentNode = nextBg
        }

        task.spectrums.forEachIndexed { idx, spec ->
            if (spec.specType == SpectrumType.NONE) return@forEachIndexed

            val sw = FfmpegUtils.ensureEvenInt(spec.width, minVal = 40)
            val sh = FfmpegUtils.ensureEvenInt(spec.height, minVal = 20)
            val sx = FfmpegUtils.sanitizeCoord(spec.posX)
            val sy = FfmpegUtils.sanitizeCoord(spec.posY)
            val scolor = spec.color
            val sop = spec.opacity

            var specShearFilter = ""
            var offsetXExpr = sx
            var offsetYExpr = sy
            if (spec.shearX != 0f || spec.shearY != 0f) {
                specShearFilter = ",shear=shx=${spec.shearX}:shy=${spec.shearY}:fillcolor=0x00000000"
                offsetXExpr = "($sx) - (w-$sw)/2"
                offsetYExpr = "($sy) - (h-$sh)/2"
            }

            if (!spec.bgColor.isNullOrBlank() && spec.bgColor != "none") {
                val boxNode = "[bg_box_spec_$idx]"
                filters += "${currentNode}drawbox=x='$sx':y='$sy':w=$sw:h=$sh:color=${spec.bgColor}@${spec.bgOpacity}:t=fill$boxNode"
                currentNode = boxNode
            }

            val nextSpecNode = "[v_spec_out_$idx]"

            if (spec.specType == SpectrumType.CUSTOM_SHADER && customSpecInputIndices.containsKey(idx)) {
                val inpI = customSpecInputIndices.getValue(idx)
                val specCleanNode = "[spec_custom_clean_$idx]"
                filters += "[$inpI:v]format=rgba,scale=$sw:$sh$specShearFilter,colorchannelmixer=aa=$sop$specCleanNode"
                filters += "$currentNode${specCleanNode}overlay=x='$offsetXExpr':y='$offsetYExpr'$nextSpecNode"
                currentNode = nextSpecNode
            } else if (spec.specType != SpectrumType.CUSTOM_SHADER) {
                val specRawNode = "[spec_std_raw_$idx]"
                val visualizerFilter = when (spec.specType) {
                    SpectrumType.SHOWWAVES ->
                        "[1:a]showwaves=s=${sw}x$sh:mode=line:colors=$scolor:rate=$fps,format=rgba$specShearFilter," +
                            "colorkey=black:0.05:0.05,colorchannelmixer=aa=$sop$specRawNode"
                    SpectrumType.CANDLES -> {
                        val iw = FfmpegUtils.ensureEvenInt(sw / 6.0, minVal = 4)
                        val ih = FfmpegUtils.ensureEvenInt(sh / 6.0, minVal = 4)
                        "[1:a]showwaves=s=${iw}x$ih:mode=cline:colors=$scolor:r=$fps:scale=sqrt,format=rgba[wave_$idx]; " +
                            "[wave_$idx]geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='if(lt(mod(X,3),2), p(X,Y)*$sop, 0)'[seg_$idx]; " +
                            "[seg_$idx]scale=$sw:$sh:flags=neighbor$specShearFilter$specRawNode"
                    }
                    SpectrumType.SEGMENTED_FREQ -> {
                        val iw = FfmpegUtils.ensureEvenInt(sw / 4.0, minVal = 4)
                        val ih = FfmpegUtils.ensureEvenInt(sh / 4.0, minVal = 4)
                        "[1:a]showfreqs=s=${iw}x$ih:mode=bar:colors=$scolor:fscale=log:ascale=sqrt,format=rgba[raw_$idx]; " +
                            "[raw_$idx]geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='if(lt(mod(X,4),3)*lt(mod(Y,3),2), p(X,Y)*$sop, 0)'[seg_$idx]; " +
                            "[seg_$idx]scale=$sw:$sh:flags=neighbor$specShearFilter$specRawNode"
                    }
                    SpectrumType.SHOWFREQS_LOG ->
                        "[1:a]showfreqs=s=${sw}x$sh:mode=bar:fscale=log:ascale=sqrt:colors=$scolor|0xdddddd:win_size=2048," +
                            "format=rgba$specShearFilter,colorkey=black:0.05:0.05,colorchannelmixer=aa=$sop$specRawNode"
                    else -> // SHOWFREQS (default)
                        "[1:a]showfreqs=s=${sw}x$sh:mode=line:fscale=log:colors=$scolor:rate=$fps,format=rgba$specShearFilter," +
                            "colorkey=black:0.05:0.05,colorchannelmixer=aa=$sop$specRawNode"
                }
                filters += visualizerFilter
                filters += "$currentNode${specRawNode}overlay=x='$offsetXExpr':y='$offsetYExpr'$nextSpecNode"
                currentNode = nextSpecNode
            }
            // else: CUSTOM_SHADER tapi belum ada video alpha pre-rendered (Fase 3 belum digabung
            // / gagal render) -> di-skip dengan aman, TIDAK crash, sama seperti perilaku sumber.
        }

        // ---------------- 3. INTRO / TITLE ----------------
        val textFilters = mutableListOf<String>()
        textFilters += introTitleFilterBuilder.buildIntroFilters(task.introConfig)

        val delayAfterIntro = if (task.introConfig != null) task.introConfig.displayDurationSeconds else 0f
        val firstAudioName = task.audioFiles.firstOrNull()?.let { safPathResolver.displayPath(it) }
        textFilters += introTitleFilterBuilder.buildTitleFilters(task.titleConfig, delayAfterIntro, firstAudioName)

        if (textFilters.isNotEmpty()) {
            filters += "$currentNode${textFilters.joinToString(",")}[vout]"
        } else {
            filters += "${currentNode}null[vout]"
        }

        val filterGraph = filters.joinToString("; ")

        // ---------------- 4. ARGUMEN FINAL ----------------
        val encoderArgs = encoderDetector.buildEncoderArgs(input.encoderSelection, resolutionOf(resW, resH), fps)
        val audioArgs = encoderDetector.buildAudioArgs()

        return listOf(
            "-y", "-sws_flags", "fast_bilinear",
            "-filter_threads", "8", "-filter_complex_threads", "8", "-threads", "0"
        ) + inputs + listOf(
            "-filter_complex", filterGraph,
            "-map", "[vout]",
            "-map", "1:a"
        ) + encoderArgs + audioArgs + listOf(
            "-max_muxing_queue_size", "2048",
            "-movflags", "+faststart",
            "-t", renderDuration.toString(),
            input.outputWriteParam
        )
    }

    private fun isValidOverlayUri(uri: Uri?): Boolean = uri != null && uri != Uri.EMPTY

    private fun isPictureName(name: String): Boolean =
        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")

    private fun resolutionOf(w: Int, h: Int): com.visualizerstudio.app.domain.model.Resolution =
        com.visualizerstudio.app.domain.model.Resolution.values().firstOrNull { it.width == w && it.height == h }
            ?: com.visualizerstudio.app.domain.model.Resolution.R720P
}
