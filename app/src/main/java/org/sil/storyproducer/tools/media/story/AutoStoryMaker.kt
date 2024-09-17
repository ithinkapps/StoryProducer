package org.sil.storyproducer.tools.media.story

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.sil.storyproducer.R
import org.sil.storyproducer.model.MUSIC_CONTINUE
import org.sil.storyproducer.model.MUSIC_NONE
import org.sil.storyproducer.model.PROJECT_DIR
import org.sil.storyproducer.model.Slide
import org.sil.storyproducer.model.SlideType
import org.sil.storyproducer.model.Story
import org.sil.storyproducer.model.VIDEO_DIR
import org.sil.storyproducer.model.Workspace
import org.sil.storyproducer.model.Workspace.activeStory
import org.sil.storyproducer.service.SlideService
import org.sil.storyproducer.tools.file.copyToWorkspacePath
import org.sil.storyproducer.tools.file.getStoryUri
import org.sil.storyproducer.tools.media.MediaHelper
import org.sil.storyproducer.tools.media.graphics.KenBurnsEffect
import org.sil.storyproducer.viewmodel.SlideViewModelBuilder
import java.io.Closeable
import java.io.File
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Locale


/**
 * AutoStoryMaker is a layer of abstraction above [StoryMaker] that handles all of the
 * parameters for StoryMaker according to some defaults, structure of projects/templates, and
 * minimal customization.
 */
class AutoStoryMaker(private val context: Context) : Thread(), Closeable {

    val mContext = context
    var videoRelPath: String = Workspace.activeStory.title.replace(' ', '_') + VIDEO_MP4_EXT
    val video3gpPath: String get(){return File(videoRelPath).nameWithoutExtension + VIDEO_3GP_EXT}

    // bits per second for video
    private var videoTempFile: File = File(context.filesDir,"temp$VIDEO_MP4_EXT")
    private var video3gpFile: File = File(context.filesDir,"temp$VIDEO_3GP_EXT")

    var mIncludeBackgroundMusic = true
    var mIncludePictures = true
    var mIncludeText = false
    var mIncludeKBFX = true
    var mIncludeSong = false

    private var mLogProgress = false

    private var mStoryMaker: StoryMaker? = null
    private var time3GPms = 0
    private var allVideosDone = false

    val isDone: Boolean
        get() = allVideosDone

    val isSuccess: Boolean
        get() = mStoryMaker != null && mStoryMaker!!.isSuccess

    val progress: Double
        get() {
            if (mStoryMaker == null) {
                return 0.0
            } else {
                if (!mStoryMaker!!.isDone) {
                    //Still making main video
                    return if (isVideoWidescreen)
                            mStoryMaker!!.progress
                        else
                            mStoryMaker!!.progress / 2
                }else {
                    //making 3gp video
                    return if (isVideoWidescreen)
                        mStoryMaker!!.progress
                    else
                        0.5 + time3GPms*1000.0/mStoryMaker!!.storyDuration / 2
                }
            }
        }

    val isVideoWidescreen = SlideService(context).isVideoWideScreen()

    override fun start() {
        val outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4

        val videoFormat = if(mIncludePictures) {generateVideoFormat()} else {null}
        val audioFormat = generateAudioFormat()
        val pages = generatePages() ?: return

        videoTempFile.delete()  //just in case it's still there.
        mStoryMaker = StoryMaker(context, videoTempFile, outputFormat, videoFormat, audioFormat,
                pages, AUDIO_TRANSITION_US, SLIDE_CROSS_FADE_US)

        watchProgress()

        super.start()
    }

    override fun run() {
        var duration = -System.currentTimeMillis()

        Log.i(TAG, "Starting video creation")
        mStoryMaker!!.churn()

        duration += System.currentTimeMillis()
        Log.i(TAG, "Stopped making story after "
                + MediaHelper.getDecimal(duration / 1000.toDouble()) + " seconds")

        if (isSuccess) {
            Log.v(TAG, "Moving completed video to " + videoRelPath)
            copyToWorkspacePath(context,Uri.fromFile(videoTempFile),"$VIDEO_DIR/$videoRelPath")
            activeStory.addVideo(videoRelPath)

            Workspace.logVideoCreationEvent(videoRelPath)

            //Make 3gp video before you delete the temp video - it's made from that.
            if (!isVideoWidescreen) {   // but not if doing wide screen
                if(mIncludePictures)
                    make3GPVideo()
            }

            videoTempFile.delete()

        } else {
            Log.w(TAG, "Deleting incomplete temporary video")
            videoTempFile.delete()
        }
        allVideosDone = true
    }

    private fun make3GPVideo() {
        Log.v(TAG, "Creating 3gp video" + video3gpPath)
        video3gpFile.delete()  //just in case it's still there.

        try{

            Config.resetStatistics()
            Config.enableStatisticsCallback { newStatistics -> time3GPms = newStatistics.time }
            FFmpeg.execute("-i ${videoTempFile.absolutePath} " +
                    "-f 3gp -vcodec $VIDEO_3GP_CODEC -framerate $VIDEO_3GP_FRAMERATE -vf " +
                    "scale=${VIDEO_3GP_WIDTH}x$VIDEO_3GP_HEIGHT -acodec $VIDEO_3GP_AUDIO" +
                    " -b:v $VIDEO_3GP_BITRATE " + video3gpFile.absolutePath)
            Log.w(TAG,FFmpeg.getLastCommandOutput() ?: "No FFMPEG output")
            copyToWorkspacePath(context,Uri.fromFile(video3gpFile),"$VIDEO_DIR/$video3gpPath")
            Workspace.activeStory.addVideo(video3gpPath)
        } catch(e:Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }

        video3gpFile.delete()
    }

    private fun generatePages(): Array<StoryPage>? {
        //TODO: add hymn to count
        val pages: MutableList<StoryPage> = mutableListOf()

        var lastSoundtrack = ""
        var lastSoundtrackVolume = 0.0f
        val slides = Workspace.activeStory.slides.toCollection(mutableListOf())
        var iSlide = 0

        // Create Local Credits Slide
        var slide = Slide()
        slide.slideType = SlideType.COPYRIGHT
        slide.content = Workspace.activeStory.localCredits + "\n" +
                    context.getString(R.string.license_attribution) + 
                    "${SimpleDateFormat("yyyy", Locale.US).format(GregorianCalendar().time)}"
        slide.translatedContent = slide.content
        slide.musicFile = MUSIC_NONE
        slides.add(slide)

        while (iSlide < slides.size) {
            val curSlide = slides[iSlide++]

            //Check if the song slide should be included
            if(curSlide.slideType == SlideType.LOCALSONG && !mIncludeSong) continue

            val image = if (mIncludePictures) curSlide.imageFile else ""
            var audio = Story.getFilename(curSlide.chosenVoiceStudioFile)
            //fallback to draft audio
            if (audio == "") {
                audio = Story.getFilename(curSlide.chosenTranslateReviseFile)
            }
            //fallback to LWC audio
            if (audio == "") {
                audio = Story.getFilename(curSlide.narrationFile)
            }

            var soundtrack = ""
            var soundtrackVolume = 0.0f
            if (mIncludeBackgroundMusic) {

                soundtrack = curSlide.musicFile
                soundtrackVolume = curSlide.volume
                if (soundtrack == MUSIC_CONTINUE) {
                    //Try not to leave nulls in so null may be reserved for no soundtrack.
                    soundtrack = lastSoundtrack
                    soundtrackVolume = lastSoundtrackVolume
                } else if (soundtrack == MUSIC_NONE) {
                    soundtrack = ""
                    soundtrackVolume = 0f
                }

                lastSoundtrack = soundtrack
                lastSoundtrackVolume = soundtrackVolume
            }

            var kbfx: KenBurnsEffect? = null
            if (mIncludePictures && mIncludeKBFX && curSlide.slideType == SlideType.NUMBEREDPAGE) {
                // SP422 - DKH 5/6/2022 Enable images on all the slides to be swapped out via the camera tool
                // Ken Burns effect is not yet implemented on local slides, ie, slides created
                // with the camera tool
                val imageExtension = image.substringAfterLast(".", "")  // without '.'
                // find a double file extension if there is one (e.g.: ".png.jpg") the first one being the original file extension otherwise simply use the single file extension
                val imageDblExtFind = Regex("\\.[a-zA-Z0-9]+\\.[a-zA-Z0-9]+\$").find(image)?.value?.substring(1) ?: imageExtension
                // check that we don't have a "_Local." camera tool selected file
                if (!(image.startsWith("$PROJECT_DIR/") && image.endsWith("${Slide.localSlideExtension}${imageDblExtFind}"))  &&
                        curSlide.startMotion != null &&
                        curSlide.endMotion != null)
                {
                    val videoRect = SlideService(mContext).getVideoScreenRect(true, true)
                    kbfx = KenBurnsEffect.fromSlide(curSlide, videoRect.width(), videoRect.height())
                }
            }

            val overlayText = SlideViewModelBuilder(curSlide).buildOverlayText(mIncludeText)

            //error
            var duration = 5000000L  // 5 seconds, microseconds.
            if (audio != "") {
                duration = MediaHelper.getAudioDuration(context, getStoryUri(audio)!!)
            }

            pages.add(StoryPage(image, audio, duration, kbfx, overlayText, soundtrack,soundtrackVolume,curSlide.slideType))
        }

        return pages.toTypedArray()
    }

    private fun watchProgress() {
        val watcher = Thread(Runnable {
            while (!mStoryMaker!!.isDone) {
                val progress = mStoryMaker!!.progress
                val audioProgress = mStoryMaker!!.audioProgress
                val videoProgress = mStoryMaker!!.videoProgress
                Log.i(TAG, "StoryMaker progress: " + MediaHelper.getDecimal(progress * 100) + "% "
                        + "(audio " + MediaHelper.getDecimal(audioProgress * 100) + "% "
                        + " and video " + MediaHelper.getDecimal(videoProgress * 100) + "%)")
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        })

        if (mLogProgress) {
            watcher.start()
        }
    }

    private fun error(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun close() {
        if (mStoryMaker != null) {
            mStoryMaker!!.close()
        }
    }

    private fun generateVideoFormat(): MediaFormat? {
        //If no video component, use null format.
        val videoRect = SlideService(mContext).getVideoScreenRect(true, true)
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MP4_CODEC, videoRect.width(), videoRect.height())
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,VIDEO_MP4_COLOR)
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_MP4_FRAMERATE)
        videoFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, VIDEO_MP4_FRAMERATE)
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_MP4_BITRATE)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_MP4_IFRAME_INTERVAL)

        return videoFormat
    }

    fun generateAudioFormat(): MediaFormat {

        val audioFormat = MediaHelper.createFormat(AUDIO_MIME_TYPE)
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, AUDIO_SAMPLE_RATE)
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, AUDIO_CHANNEL_COUNT)

        return audioFormat
    }

    companion object {
        private val TAG = "AutoStoryMaker"

        private val SLIDE_CROSS_FADE_US: Long = 750000
        private val AUDIO_TRANSITION_US: Long = 500000


        private val VIDEO_MP4_EXT = ".mp4"
        private val VIDEO_MP4_CODEC = MediaFormat.MIMETYPE_VIDEO_AVC
        private val VIDEO_MP4_COLOR = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        private val VIDEO_MP4_WIDTH = 768
        private val VIDEO_MP4_HEIGHT = 576
        private val VIDEO_MP4_BITRATE = 5000000
        private val VIDEO_MP4_FRAMERATE = 30
        private val VIDEO_MP4_IFRAME_INTERVAL = 8           // 5 second between I-frames

        private val VIDEO_3GP_EXT = ".3gp"
        private val VIDEO_3GP_CODEC = "h263"
        private val VIDEO_3GP_WIDTH = 176
        private val VIDEO_3GP_HEIGHT = 144
        private val VIDEO_3GP_AUDIO = "aac"
        private val VIDEO_3GP_BITRATE = 1000000
        private val VIDEO_3GP_FRAMERATE = 15

        // parameters for the audio encoder
        private val AUDIO_MIME_TYPE = "audio/mp4a-latm" //MediaFormat.MIMETYPE_AUDIO_AAC;
        private val AUDIO_SAMPLE_RATE = 44100
        private val AUDIO_CHANNEL_COUNT = 1
        private val AUDIO_BIT_RATE = 64000

    }
}

fun Boolean?.toInt(): Int {
    return if (this == true) 1 else 0
}
