package org.sil.storyproducer.controller.phase

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.view.GestureDetectorCompat
import android.support.v4.view.GravityCompat
import android.support.v4.view.MenuItemCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.*

import org.sil.storyproducer.R
import org.sil.storyproducer.controller.ImageSlideFrag
import org.sil.storyproducer.controller.ToolbarFrag
import org.sil.storyproducer.controller.community.CommunityCheckFrag
import org.sil.storyproducer.controller.dramatization.DramatizationFrag
import org.sil.storyproducer.model.*
import org.sil.storyproducer.tools.DrawerItemClickListener
import org.sil.storyproducer.tools.PhaseGestureListener
import org.sil.storyproducer.tools.media.AudioPlayer

abstract class PhaseBaseActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener, ToolbarFrag.OnAudioPlayListener, ImageSlideFrag.OnAudioPlayListener, CommunityCheckFrag.OnAudioPlayListener, DramatizationFrag.OnAudioPlayListener {
    private var mDetector: GestureDetectorCompat? = null
    private var mDrawerList: ListView? = null
    private var mAdapter: ArrayAdapter<String>? = null
    private var mDrawerToggle: ActionBarDrawerToggle? = null
    private var mDrawerLayout: DrawerLayout? = null
    private var mediaPlayer: AudioPlayer? = null
    private var oldImage : ImageButton? = null

    protected var phase: Phase = Workspace.activePhase
    protected var story: Story = Workspace.activeStory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(R.layout.phase_frame)

        //keeps the screen from going to sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val mActionBarToolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(mActionBarToolbar)
        supportActionBar?.title = ""
        supportActionBar?.setBackgroundDrawable(ColorDrawable(ResourcesCompat.getColor(resources,
                phase.getColor(), null)))

        mDetector = GestureDetectorCompat(this, PhaseGestureListener(this))
        mediaPlayer = AudioPlayer()
        setupDrawer()
        setupStatusBar()
    }

    override fun onPlayButtonClicked(path: String, image: ImageButton, stopImage: Int, playImage: Int) {
        mediaPlayer?.onPlayBackStop(MediaPlayer.OnCompletionListener {
            image.setBackgroundResource(playImage)
        })
        if(mediaPlayer?.isAudioPlaying!!){
            oldImage?.setBackgroundResource(playImage)
            mediaPlayer?.stopAudio()
            mediaPlayer?.reset()
        }
        else{
            oldImage = null
        }
        if(oldImage != image) {
            mediaPlayer?.reset()
            if (mediaPlayer?.setStorySource(this, path) == true) {
                oldImage = image
                mediaPlayer?.playAudio()
                Toast.makeText(this, R.string.recording_toolbar_play_back_recording, Toast.LENGTH_SHORT).show()
                image.setBackgroundResource(stopImage)
            } else {
                Toast.makeText(this, R.string.recording_toolbar_no_recording, Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onPauseButtonClicked(path: String, image: ImageButton, stopImage: Int, playImage: Int) : Int? {
        mediaPlayer?.onPlayBackStop(MediaPlayer.OnCompletionListener {
            image.setBackgroundResource(playImage)
        })
        if(mediaPlayer?.isAudioPlaying!!){
            oldImage?.setBackgroundResource(playImage)
            mediaPlayer?.pauseAudio()
        }
        else{
            oldImage = null
        }
        if(oldImage != image) {
            mediaPlayer?.reset()
            if (mediaPlayer?.setStorySource(this, path) == true) {
                oldImage = image
                mediaPlayer?.playAudio()
                Toast.makeText(this, R.string.draft_playback_lwc_audio, Toast.LENGTH_SHORT).show()
                image.setBackgroundResource(stopImage)
            } else {
                Toast.makeText(this, R.string.recording_toolbar_no_recording, Toast.LENGTH_SHORT).show()
            }
        }
        else{
            mediaPlayer?.resumeAudio()
        }
        return mediaPlayer?.currentPosition
    }

    override fun getCurrentAudioPosition() : Int? {
        return mediaPlayer?.currentPosition
    }

    override fun getDuration() : Int?{
        return mediaPlayer?.audioDurationInMilliseconds
    }
    override fun setPosition(pos: Int) {
        mediaPlayer?.currentPosition = pos
    }

    override fun onPause(){
        super.onPause()
        story.lastSlideNum = Workspace.activeSlideNum
        story.lastPhaseType = Workspace.activePhase.phaseType
        Thread(Runnable{story.toJson(this)}).start()
    }

    //Override setContentView to coerce into child view.
    override fun setContentView(id: Int) {
        val inflater = layoutInflater
        inflater.inflate(id, mDrawerLayout)
        //Bring menu to front again.
        mDrawerList!!.bringToFront()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.getItem(0)
        item.setIcon(phase.getIcon())
        return true
    }

    /**
     * sets the Menu spinner_item object
     * @param menu
     * @return
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_phases, menu)

        val item = menu.findItem(R.id.spinner)
        val spinner = MenuItemCompat.getActionView(item) as Spinner
        val adapter: ArrayAdapter<CharSequence>
        if (Workspace.registration.getBoolean("isRemote",false)) {
            //remote
            adapter = ArrayAdapter.createFromResource(this,
                    R.array.remote_phases_menu_array, android.R.layout.simple_spinner_item)
        } else {
            //local
            adapter = ArrayAdapter.createFromResource(this,
                    R.array.local_phases_menu_array, android.R.layout.simple_spinner_item)
        }
        adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)

        spinner.adapter = adapter
        //Set the selection before setting the listener.  If flipped, the listener would be called
        //when initializing and cause bad things.
        spinner.setSelection(Workspace.activePhaseIndex)
        spinner.onItemSelectedListener = this
        return true
    }


    override fun onItemSelected(parent: AdapterView<*>, view: View,
                                pos: Int, id: Long) {
        jumpToPhase(Workspace.phases[pos])
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }

    /**
     * get the touch event so that it can be passed on to GestureDetector
     * @param event the MotionEvent
     * @return the super version of the function
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        mDetector!!.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle!!.syncState()                                  //needed to make the drawer synced
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle!!.onConfigurationChanged(newConfig)            //needed to make the drawer synced
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                mDrawerLayout!!.openDrawer(GravityCompat.START)
                true
            }
            R.id.spinner -> {
                mDrawerToggle!!.onOptionsItemSelected(item)
            }
            R.id.helpButton -> {
                val dialog = AlertDialog.Builder(this)
                        .setTitle(getString(R.string.help))
                        .setMessage(Phase.getHelp(this, phase.phaseType))
                        .create()
                dialog.show()
                true
            }
            else -> mDrawerToggle!!.onOptionsItemSelected(item)
        }
    }

    /**
     * initializes the items that the drawer needs
     */
    private fun setupDrawer() {
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
        mDrawerList = findViewById(R.id.navList)
        mDrawerList!!.bringToFront()
        mDrawerLayout = findViewById(R.id.drawer_layout)
        addDrawerItems()
        mDrawerList!!.onItemClickListener = DrawerItemClickListener(this)
        mDrawerToggle = object : ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.nav_open, R.string.nav_close) {

            /** Called when a drawer has settled in a completely open state.  */
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                invalidateOptionsMenu() // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely closed state.  */
            override fun onDrawerClosed(view: View) {
                super.onDrawerClosed(view)
                invalidateOptionsMenu() // creates call to onPrepareOptionsMenu()
            }
        }
        mDrawerToggle!!.isDrawerIndicatorEnabled = true
        mDrawerLayout!!.addDrawerListener(mDrawerToggle!!)
    }

    /**
     * adds the items to the drawer from the array resources
     */
    private fun addDrawerItems() {
        val menuArray = resources.getStringArray(R.array.global_menu_array)
        mAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, menuArray)
        mDrawerList!!.adapter = mAdapter
    }

    fun jumpToPhase(newPhase: Phase) {
        if(newPhase.phaseType == phase.phaseType) return
        Workspace.activePhase = newPhase
        val intent = Intent(this.applicationContext, newPhase.getTheClass())
        intent.putExtra("storyname", Workspace.activeStory.title)
        startActivity(intent)
        finish()
    }

    /**
     * starts the previous phase and starts that activity
     */
    fun startPrevActivity() {
        if (Workspace.goToPreviousPhase()) {
            jumpToPhase(Workspace.activePhase)
            overridePendingTransition(R.anim.enter_down, R.anim.exit_down)
        }
    }

    /**
     * starts the next phase and starts that activity
     */
    fun startNextActivity() {
        if (Workspace.goToNextPhase()) {
            jumpToPhase(Workspace.activePhase)
            overridePendingTransition(R.anim.enter_up, R.anim.exit_up)
        }
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val hsv : FloatArray = floatArrayOf(0.0f,0.0f,0.0f)
            Color.colorToHSV(ContextCompat.getColor(this, Workspace.activePhase.getColor()), hsv)
            hsv[2] *= 0.8f
            window.statusBarColor = Color.HSVToColor(hsv)
        }
    }

    companion object {
        fun disableViewAndChildren(view: View) {
            view.isEnabled = false
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    disableViewAndChildren(child)
                }
            }
        }
    }
}
