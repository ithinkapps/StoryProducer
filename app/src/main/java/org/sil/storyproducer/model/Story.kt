package org.sil.storyproducer.model


import com.squareup.moshi.JsonClass
import org.sil.storyproducer.model.logging.LogEntry

import java.util.*
import java.util.regex.Pattern

internal const val PROJECT_DIR = "project"
internal const val VIDEO_DIR = "videos"
internal const val PROJECT_FILE = "story.json"
internal val RE_TITLE_NUMBER = "([0-9]+[A-Za-z]?)?[_ -]*(.+)".toRegex()

@JsonClass(generateAdapter = true)
class Story(var title: String, val slides: List<Slide>){

    var isApproved: Boolean = false

    var learnAudioFile = ""
    var wholeStoryBackTAudioFile = ""
    var activityLogs: MutableList<LogEntry> = ArrayList()
    var outputVideos: MutableList<String> = ArrayList()
    var lastPhaseType: PhaseType = PhaseType.LEARN
    var lastSlideNum: Int = 0

    val shortTitle: String get() {
        val match = RE_TITLE_NUMBER.find(title)
        return if(match != null){
            match.groupValues[2]
        } else {
            title
        }
    }
    val titleNumber: String get() {
        val match = RE_TITLE_NUMBER.find(title)
        return if(match != null){
            match.groupValues[1]
        } else {
            ""
        }
    }

    companion object

    fun addVideo(video: String){
        if(!(video in outputVideos)){
            outputVideos.add(video)
            outputVideos.sort()
        }
    }

    fun getVideoTitle(existingTitle : String = "") : String {
        val ovNoPath : MutableList<String> = ArrayList()
        ovNoPath.add(existingTitle)
        for (ov in outputVideos){
            ovNoPath.add(ov.split(".")[0])
        }
        if(title !in ovNoPath) return title
        for(i in 1..100){
            val temp = "${title}_$i"
            if(!((temp) in ovNoPath)) return temp
        }
        return ""
    }
}

fun emptyStory() : Story {return Story("",ArrayList())}

