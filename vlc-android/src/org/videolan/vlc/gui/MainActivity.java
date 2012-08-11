/*****************************************************************************
 * MainActivity.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui;

import java.util.ArrayList;

import org.videolan.vlc.AudioService;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.LibVLC;
import org.videolan.vlc.LibVlcException;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCCallbackTask;
import org.videolan.vlc.gui.SidebarAdapter.SidebarEntry;
import org.videolan.vlc.gui.video.VideoListAdapter;
import org.videolan.vlc.interfaces.ISortable;
import org.videolan.vlc.widget.AudioMiniPlayer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.slidingmenu.lib.SlidingMenu;

public class MainActivity extends SherlockFragmentActivity {
    public final static String TAG = "VLC/MainActivity";

    protected static final String ACTION_SHOW_PROGRESSBAR = "org.videolan.vlc.gui.ShowProgressBar";
    protected static final String ACTION_HIDE_PROGRESSBAR = "org.videolan.vlc.gui.HideProgressBar";
    protected static final String ACTION_SHOW_TEXTINFO = "org.videolan.vlc.gui.ShowTextInfo";

    private static final int VIDEO_TAB = 0;
    private static final int AUDIO_TAB = 1;
    private static final String PREF_SHOW_INFO = "show_info";

    private ActionBar mActionBar;
    private SlidingMenu mMenu;
    private SidebarAdapter mSidebarAdapter;
    private AudioMiniPlayer mAudioPlayer;
    private AudioServiceController mAudioController;
    private View mInfoLayout;
    private ProgressBar mInfoProgress;
    private TextView mInfoText;
    private DirectoryViewFragment mDirectoryView;
    private int mCurrentViewTab;
    private Boolean mMediaLibraryActive;

    private SharedPreferences mSettings;

    private int mVersionNumber = -1;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!Util.hasCompatibleCPU()) {
            Log.e(TAG, Util.getErrorMsg());
            super.onCreate(savedInstanceState);
            Intent i = new Intent(this, CompatErrorActivity.class);
            startActivity(i);
            finish();
            return;
        }

        if (Util.isICSOrLater()) /* Bug on pre-ICS, the progress bar is always present */
            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // Set up the sliding menu
        setContentView(R.layout.sliding_menu);
        mMenu = (SlidingMenu) findViewById(R.id.sliding_menu);
        updateMenuOffset();

        View v_main = LayoutInflater.from(this).inflate(R.layout.main, null);
        mMenu.setViewAbove(v_main);
        View sidebar = LayoutInflater.from(this).inflate(R.layout.sidebar, null);
        ((ListView)sidebar).setFooterDividersEnabled(true);
        final ListView listView = (ListView)sidebar.findViewById(android.R.id.list);
        mSidebarAdapter = new SidebarAdapter(getSupportFragmentManager());
        listView.setAdapter(mSidebarAdapter);
        listView.setBackgroundColor(Color.parseColor("#1f3f61"));
        mMenu.setViewBehind(sidebar);

        super.onCreate(savedInstanceState);

        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        LibVLC.useIOMX(this);
        try {
            // Start libvlc
            LibVLC.getInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
        }

        /* Initialize variables */
        mInfoLayout = v_main.findViewById(R.id.info_layout);
        mInfoProgress = (ProgressBar) v_main.findViewById(R.id.info_progress);
        mInfoText = (TextView) v_main.findViewById(R.id.info_text);

        /* Initialize the tabs */
        mActionBar = getSupportActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        mActionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

        /* Set up the sidebar click listener */
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick (AdapterView<?> parent, View view,
                    int position, long id) {
                SidebarAdapter.SidebarEntry entry = (SidebarEntry) listView.getItemAtPosition(position);
                Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
                if(current.getTag() == entry.id) /* Already selected */
                    return;

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.detach(current);
                ft.attach(mSidebarAdapter.getFragment(entry.id));
                ft.commit();
            }
        });

        /* DirectoryView */
        mDirectoryView = new DirectoryViewFragment();
        mDirectoryView.setRetainInstance(true); /* Retain instance across attach/detach */
        getSupportFragmentManager().beginTransaction()
            .add(R.id.fragment_placeholder, mDirectoryView)
            .commit();
        getSupportFragmentManager().beginTransaction()
            .detach(mDirectoryView)
            .commit();

        /* Set up the mini audio player */
        mAudioPlayer = new AudioMiniPlayer();
        mAudioController = AudioServiceController.getInstance();
        mAudioPlayer.setAudioPlayerControl(mAudioController);
        mAudioPlayer.update();

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.audio_mini_player, mAudioPlayer)
            .commit();

        /* Show info/alpha/beta Warning */
        PackageInfo pinfo = null;
        try {
            pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "package info not found.");
        }
        if (pinfo != null) {
            mVersionNumber = pinfo.versionCode;

            if (mSettings.getInt(PREF_SHOW_INFO, -1) != mVersionNumber)
                showInfoDialog();
        }

        /* Prepare the progressBar */
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_PROGRESSBAR);
        filter.addAction(ACTION_HIDE_PROGRESSBAR);
        filter.addAction(ACTION_SHOW_TEXTINFO);
        registerReceiver(messageReceiver, filter);

        /* Reload the latest preferences */
        reloadPreferences();

        /* Load media items from database and storage */
        MediaLibrary.getInstance(this).loadMediaItems(this);
    }

    private void updateMenuOffset() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        @SuppressWarnings("deprecation")
        int behindOffset_dp = Util.convertPxToDp(display.getWidth()) - 208;
        mMenu.setBehindOffset(Util.convertDpToPx(behindOffset_dp));
    }

    @Override
    protected void onResume() {
        mAudioController.addAudioPlayer(mAudioPlayer);
        AudioServiceController.getInstance().bindAudioService(this);
        Boolean startFromNotification = getIntent().hasExtra(AudioService.START_FROM_NOTIFICATION);

        /* Restore last view */
        if(!mMediaLibraryActive)
            showDirectoryView();
        else if (startFromNotification || mCurrentViewTab == AUDIO_TAB)
            showAudioTab();
        else
            showVideoTab();
        if (startFromNotification)
            getIntent().removeExtra(AudioService.START_FROM_NOTIFICATION);
        super.onResume();
    }

    /**
     * Stop audio player and save opened tab
     */
    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = getSharedPreferences("MainActivity", MODE_PRIVATE).edit();
        editor.putInt("tab", mCurrentViewTab);
        editor.putBoolean("medialibrary", mMediaLibraryActive);
        editor.commit();
        mAudioController.removeAudioPlayer(mAudioPlayer);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(messageReceiver);
        } catch (IllegalArgumentException e) {}
        super.onDestroy();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        /* Reload the latest preferences */
        reloadPreferences();
    }

    /** Create menu from XML
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        /* Note: on Android 3.0+ with an action bar this method
         * is called while the view is created. This can happen
         * any time after onCreate.
         */
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.media_library, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateMenuOffset();
    }

    @Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(this, SearchActivity.class);
        startActivity(intent);
        return false;
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Intent to start new Activity
        Intent intent;

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
            case R.id.ml_menu_sortby_length:
                Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
                if (current == null)
                    break;
                if (current instanceof ISortable)
                    ((ISortable) current).sortBy(item.getItemId() == R.id.ml_menu_sortby_name
                    ? VideoListAdapter.SORT_BY_TITLE
                    : VideoListAdapter.SORT_BY_LENGTH);
                break;
            // About
            case R.id.ml_menu_about:
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
            // Preferences
            case R.id.ml_menu_preferences:
                intent = new Intent(this, PreferencesActivity.class);
                startActivity(intent);
                break;
            // Refresh
            case R.id.ml_menu_refresh:
                if(mMediaLibraryActive)
                    MediaLibrary.getInstance(this).loadMediaItems(this);
                else
                    mDirectoryView.refresh();
                break;
            // Browse Folders
            case R.id.ml_menu_browse:
                if(mMediaLibraryActive) {
                    item.setTitle(R.string.media_library);
                    showDirectoryView();
                }
                else {
                    item.setTitle(R.string.directories);
                    if (mCurrentViewTab == AUDIO_TAB)
                        showAudioTab();
                    else
                        showVideoTab();
                }
                break;
            // Open MRL
            case R.id.ml_menu_open_mrl:
                onOpenMRL();
                break;
            case R.id.ml_menu_search:
            	onSearchRequested();
            	break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reloadPreferences() {
        SharedPreferences sharedPrefs = getSharedPreferences("MainActivity", MODE_PRIVATE);
        mCurrentViewTab = sharedPrefs.getInt("tab", VIDEO_TAB);
        mMediaLibraryActive = sharedPrefs.getBoolean("medialibrary", true);
    }

    private void showDirectoryView() {
        FragmentTransaction ft = this.getSupportFragmentManager().beginTransaction();

        ft.setCustomAnimations(R.anim.anim_enter_bottom, 0);

        /* Remove existing tabs */
        if (mActionBar.getNavigationMode() != ActionBar.NAVIGATION_MODE_STANDARD)
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        /* Load directory view fragment */
        ft.attach(mDirectoryView);
        ft.commitAllowingStateLoss();
        mMediaLibraryActive = false;
    }

    private void hideDirectoryView() {
        FragmentTransaction ft = this.getSupportFragmentManager().beginTransaction();

        ft.setCustomAnimations(0, R.anim.anim_leave_bottom);

        /* Restore the tabs */
        if (mActionBar.getNavigationMode() != ActionBar.NAVIGATION_MODE_TABS)
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        /* Remove the directory view from the tabs */
        ft.detach(mDirectoryView);
        ft.commitAllowingStateLoss();
        mMediaLibraryActive = true;
    }

    private void showInfoDialog() {
        final Dialog infoDialog = new Dialog(this, R.style.info_dialog);
        infoDialog.setContentView(R.layout.info_dialog);
        Button okButton = (Button) infoDialog.findViewById(R.id.ok);
        okButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckBox notShowAgain =
                        (CheckBox) infoDialog.findViewById(R.id.not_show_again);
                if (notShowAgain.isChecked() && mSettings != null) {
                    Editor editor = mSettings.edit();
                    editor.putInt(PREF_SHOW_INFO, mVersionNumber);
                    editor.commit();
                }
                infoDialog.dismiss();
            }
        });
        infoDialog.show();
    }

    /**
     * onClick event from xml
     * @param view
     */
    public void changeTabClick(View view) {
        // Toggle audio- and video-tab
        if (mActionBar.getSelectedNavigationIndex() == VIDEO_TAB) {
            showAudioTab();
        } else {
            showVideoTab();
        }
    }

    private void showVideoTab() {
        hideDirectoryView();
        mActionBar.setSelectedNavigationItem(VIDEO_TAB);
    }

    private void showAudioTab() {
        hideDirectoryView();
        mActionBar.setSelectedNavigationItem(AUDIO_TAB);
    }

    /**
     * onClick event from xml
     * @param view
     */
    public void searchClick(View view) {
        onSearchRequested();
    }

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(ACTION_SHOW_PROGRESSBAR)) {
                setProgressBarIndeterminateVisibility(Boolean.TRUE);
            } else if (action.equalsIgnoreCase(ACTION_HIDE_PROGRESSBAR)) {
                setProgressBarIndeterminateVisibility(Boolean.FALSE);
            } else if (action.equalsIgnoreCase(ACTION_SHOW_TEXTINFO)) {
                String info = intent.getStringExtra("info");
                int max = intent.getIntExtra("max", 0);
                int progress = intent.getIntExtra("progress", 100);
                mInfoText.setText(info);
                mInfoProgress.setMax(max);
                mInfoProgress.setProgress(progress);
                mInfoLayout.setVisibility(info != null ? View.VISIBLE : View.GONE);
            }
        }
    };

    public static void showProgressBar(Context context) {
        if (context == null)
            return;
        Intent intent = new Intent();
        intent.setAction(ACTION_SHOW_PROGRESSBAR);
        context.getApplicationContext().sendBroadcast(intent);
    }

    public static void hideProgressBar(Context context) {
        if (context == null)
            return;
        Intent intent = new Intent();
        intent.setAction(ACTION_HIDE_PROGRESSBAR);
        context.getApplicationContext().sendBroadcast(intent);
    }


    public static void clearTextInfo(Context context) {
        sendTextInfo(context, null, 0, 100);
    }

    public static void sendTextInfo(Context context, String info, int progress, int max) {
        if (context == null)
            return;
        Intent intent = new Intent();
        intent.setAction(ACTION_SHOW_TEXTINFO);
        intent.putExtra("info", info);
        intent.putExtra("progress", progress);
        intent.putExtra("max", max);
        context.getApplicationContext().sendBroadcast(intent);
    }

    private void onOpenMRL() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        b.setTitle(R.string.open_mrl_dialog_title);
        b.setMessage(R.string.open_mrl_dialog_msg);
        b.setView(input);
        b.setPositiveButton(R.string.open, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int button) {
                ProgressDialog pd = ProgressDialog.show(
                        MainActivity.this,
                        getApplicationContext().getString(R.string.loading),
                        "Please wait...", true);
                pd.setCancelable(true);

                VLCCallbackTask t = new VLCCallbackTask(
                    /* Task to run */
                    new VLCCallbackTask.CallbackListener() {
                        @Override
                        public void callback() {
                            AudioServiceController c = AudioServiceController.getInstance();
                            String s = input.getText().toString();

                            /* Use the audio player by default. If a video track is
                             * detected, then it will automatically switch to the video
                             * player. This allows us to support more types of streams
                             * (for example, RTSP and TS streaming) where ES can be
                             * dynamically adapted rather than a simple scan.
                             */
                            ArrayList<String> media = new ArrayList<String>();
                            media.add(s);
                            c.append(media);
                        }

                        @Override
                        public void callback_object(Object o) {
                            ProgressDialog pd = (ProgressDialog)o;
                            pd.dismiss();
                        }
                    }, pd);

                /* Start this in a new friend as to not block the UI thread */
                new Thread(t).start();
            }
        }
        );
        b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                return;
                }});
        b.show();
    }

    public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private final SherlockFragmentActivity mActivity;
        private final String mTag;
        private final Class<T> mClass;
        private final Bundle mArgs;
        private Fragment mFragment;

        public TabListener(SherlockFragmentActivity activity, String tag, Class<T> clz) {
            this(activity, tag, clz, null);
        }

        public TabListener(SherlockFragmentActivity activity, String tag, Class<T> clz, Bundle args) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;
            mArgs = args;

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state.  If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            mFragment = mActivity.getSupportFragmentManager().findFragmentByTag(mTag);
            if (mFragment != null && !mFragment.isDetached()) {
                FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
                ft.detach(mFragment);
                ft.commit();
            }
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            Fragment current = mActivity.getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);

            if (mTag.equalsIgnoreCase("video"))
                ((MainActivity)mActivity).mCurrentViewTab = VIDEO_TAB;
            else if (mTag.equalsIgnoreCase("audio"))
                ((MainActivity)mActivity).mCurrentViewTab = AUDIO_TAB;

            if (current != null && current.getTag() != null) {
                if (current.getTag() == mTag)
                    return;
                if (mTag.equalsIgnoreCase("video"))
                    ft.setCustomAnimations(0, R.anim.anim_leave_right);
                else if (mTag.equalsIgnoreCase("audio"))
                    ft.setCustomAnimations(0, R.anim.anim_leave_left);
                ft.detach(current);
            }

            if (mTag.equalsIgnoreCase("video"))
                ft.setCustomAnimations(R.anim.anim_enter_left, 0);
            else if (mTag.equalsIgnoreCase("audio"))
                ft.setCustomAnimations(R.anim.anim_enter_right, 0);

            if (mFragment == null) {
                mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
                mFragment.setRetainInstance(true);
                ft.add(R.id.fragment_placeholder, mFragment, mTag);
            } else {
                ft.attach(mFragment);
            }
        }

        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    }
}
