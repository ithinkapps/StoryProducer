package org.sil.storyproducer.controller.translaterevise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.sil.storyproducer.R
import org.sil.storyproducer.controller.MultiRecordFrag
import org.sil.storyproducer.controller.PopupHelpUtils
import org.sil.storyproducer.controller.phase.PhaseBaseActivity
import org.sil.storyproducer.model.Workspace

/**
 * The fragment for the Translate + Revise phase.
 * This is where a user can translate the story slide by slide
 */
class TranslateReviseFrag : MultiRecordFrag() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

    }

    private fun addAndStartPopupMenus(slideNumber: Int) {

        if (mPopupHelpUtils != null) {
            mPopupHelpUtils?.dismissPopup()
            mPopupHelpUtils = null
        }

        if (slideNumber == 0) {
            mPopupHelpUtils = PopupHelpUtils(this, 0)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.toolbar,
                50, 90,
                R.string.help_translate_phase_title, R.string.help_translate_phase_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.seek_bar,
                88, 70,
                R.string.help_translate_titleslide_title, R.string.help_translate_titleslide_body)
            mPopupHelpUtils?.addPopupHelpItem(
                    R.id.edit_text_view,
                    20, 90,
                    R.string.help_translate_enter_title, R.string.help_translate_enter_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.start_recording_button,
                80, 10 ,
                R.string.help_translate_record_title, R.string.help_translate_record_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.phase_frame,
                98, 50,
                R.string.help_translate_swipe_left_title, R.string.help_translate_swipe_left_body)
        } else if (Workspace.activeStory.slides[slideNumber].isNumberedPage()) {
            mPopupHelpUtils = PopupHelpUtils(this, 1)   // always use slide 1
            mPopupHelpUtils?.addPopupHelpItem(
                    R.id.seek_bar,
                    84, 70,
                    R.string.help_translate_storyslide_title, R.string.help_translate_storyslide_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.fragment_reference_audio_button,
                80, 90,
                R.string.help_translate_listen_title, R.string.help_translate_listen_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.start_recording_button,
                80, 10,
                R.string.help_translate_record_slide_title, R.string.help_translate_record_slide_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.play_recording_button,
                50, 10,
                R.string.help_translate_review_title, R.string.help_translate_review_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.phase_frame,
                98, 50,
                R.string.help_translate_swipe_left2_title, R.string.help_translate_swipe_left2_body)

        } else if (Workspace.activeStory.slides[slideNumber].isSongSlide()) {
            mPopupHelpUtils = PopupHelpUtils(this, 2)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.phase_frame,
                -1, -1,
                R.string.help_translate_song_slide_title, R.string.help_translate_song_slide_body)
            mPopupHelpUtils?.addPopupHelpItem(
                    R.id.seek_bar,
                    84, 70,
                    R.string.help_translate_song_slide_title, R.string.help_translate_songlabel_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.start_recording_button,
                80, 10,
                R.string.help_translate_record_song_title, R.string.help_translate_record_song_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.edit_text_view,
                20, 90,
                R.string.help_translate_song_title_title, R.string.help_translate_song_title_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.insert_image_view,
                80, 90,
                R.string.help_translate_song_camera_title, R.string.help_translate_song_camera_body)
            mPopupHelpUtils?.addPopupHelpItem(
                R.id.toolbar,
                50, 90,
                R.string.help_translate_navigation_title, R.string.help_translate_navigation_body)
        }

        if (mPopupHelpUtils != null)
            mPopupHelpUtils?.showNextPopupHelp()

        (requireActivity() as PhaseBaseActivity).setBasePopupHelpUtils(mPopupHelpUtils!!)

    }

    override fun onResume() {
        super.onResume()

        addAndStartPopupMenus(slideNum)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        setScriptureText(null, rootView!!.findViewById(R.id.fragment_scripture_text))
        setReferenceText(rootView!!.findViewById(R.id.fragment_reference_text))

        return rootView
    }

}
