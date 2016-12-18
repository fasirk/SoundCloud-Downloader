package com.theah64.soundclouddownloader.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.theah64.soundclouddownloader.R;
import com.theah64.soundclouddownloader.database.Playlists;
import com.theah64.soundclouddownloader.database.Tracks;
import com.theah64.soundclouddownloader.models.Playlist;
import com.theah64.soundclouddownloader.ui.activities.PlaylistDownloadActivity;
import com.theah64.soundclouddownloader.models.Track;
import com.theah64.soundclouddownloader.ui.activities.settings.SettingsActivity;
import com.theah64.soundclouddownloader.utils.APIRequestBuilder;
import com.theah64.soundclouddownloader.utils.APIResponse;
import com.theah64.soundclouddownloader.utils.App;
import com.theah64.soundclouddownloader.utils.DownloadUtils;
import com.theah64.soundclouddownloader.utils.NetworkUtils;
import com.theah64.soundclouddownloader.utils.OkHttpUtils;
import com.theah64.soundclouddownloader.utils.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class DownloaderService extends Service {

    private static final String X = DownloaderService.class.getSimpleName();
    public static final String KEY_NOTIFICATION_ID = "my_notification_id";


    private int notifId;
    private NotificationManager nm;
    private NotificationCompat.Builder apiNotification;
    private App app;

    public DownloaderService() {
    }

    public void showToast(final @StringRes int stringRes) {
        showToast(getString(stringRes));
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private void showToast(final String message) {
        Log.d(X, "Message : " + message);
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(X, "Download service initialized : " + intent);

        if (intent != null) {

            nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

            //Clearing notification if exists
            final int clipNotifId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1);

            Log.d(X, "clipNotifId is : " + clipNotifId);
            if (clipNotifId != -1) {
                nm.cancel(clipNotifId);
            }

            final String soundCloudUrl = intent.getStringExtra(Tracks.COLUMN_SOUNDCLOUD_URL);

            Log.d(X, "SoundCloud url is : " + soundCloudUrl);

            if (soundCloudUrl != null) {

                if (NetworkUtils.isNetwork(this)) {

                    final Tracks tracksTable = Tracks.getInstance(this);

                    apiNotification = new NotificationCompat.Builder(this)
                            .setContentTitle(getString(R.string.initializing_download))
                            .setContentText(soundCloudUrl)
                            .setSmallIcon(R.drawable.ic_stat_logo_white)
                            .setProgress(100, 0, true)
                            .setAutoCancel(false)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentInfo(soundCloudUrl)
                            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                            .setTicker(getString(R.string.initializing_download))
                            .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo_color_24dp));

                    notifId = Random.getRandomInt();
                    nm.notify(notifId, apiNotification.build());

                    //Building json download request
                    final Request scdRequest = new APIRequestBuilder("/scd/json")
                            .addParam("soundcloud_url", soundCloudUrl)
                            .build();

                    //Processing request
                    OkHttpUtils.getInstance().getClient().newCall(scdRequest).enqueue(new Callback() {

                        @Override
                        public void onFailure(Call call, IOException e) {
                            showToast("ERROR: " + e.getMessage());
                            showErrorNotification(e.getMessage(), null);
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            try {
                                final APIResponse apiResponse = new APIResponse(OkHttpUtils.logAndGetStringBody(response));

                                final JSONObject joData = apiResponse.getJSONObjectData();
                                final JSONArray jaTracks = joData.getJSONArray("tracks");


                                if (!joData.has(Track.KEY_PLAYLIST_NAME)) {

                                    // Single song
                                    final JSONObject joTrack = jaTracks.getJSONObject(0);

                                    final String title = joTrack.getString("title");
                                    final String downloadUrl = joTrack.getString("download_url");
                                    final String fileName = joTrack.getString("filename");

                                    String artworkUrl = null;
                                    if (joTrack.has(Tracks.COLUMN_ARTWORK_URL)) {
                                        artworkUrl = joTrack.getString(Tracks.COLUMN_ARTWORK_URL);
                                        Log.d(X, title + " has artwork " + artworkUrl);
                                    } else {
                                        Log.e(X, title + " hasn't artwork url ");
                                    }

                                    apiNotification.setContentTitle(getString(R.string.Starting_download));
                                    apiNotification.setContentText(downloadUrl);
                                    nm.notify(notifId, apiNotification.build());

                                    //Checking file existence
                                    final String baseStorageLocation = pref.getString(SettingsActivity.SettingsFragment.KEY_STORAGE_LOCATION, App.getDefaultStorageLocation());
                                    final String absFilePath = String.format("%s/%s", baseStorageLocation, fileName);
                                    Log.d(X, "New file : " + absFilePath);

                                    final File trackFile = new File(absFilePath);


                                    if (!trackFile.exists()) {

                                        final String username = joTrack.getString(Tracks.COLUMN_USERNAME);
                                        final long duration = joTrack.getLong(Tracks.COLUMN_DURATION);

                                        final Track track = new Track(null, title, username, downloadUrl, artworkUrl, null, soundCloudUrl, null, false, false, trackFile, duration);

                                        //Starting download
                                        final long downloadId = DownloadUtils.addToDownloadQueue(DownloaderService.this, track);
                                        track.setDownloadId(String.valueOf(downloadId));


                                        app = (App) getApplicationContext();

                                        final String trackId = tracksTable.get(Tracks.COLUMN_SOUNDCLOUD_URL, soundCloudUrl, Tracks.COLUMN_ID);

                                        if (trackId == null) {
                                            //Adding track to database -
                                            tracksTable.add(track, handler);

                                        } else {

                                            track.setId(trackId);
                                            track.setDownloadId(String.valueOf(downloadId));
                                            track.setFile(trackFile);

                                            //Track exist so just updating the download id.
                                            tracksTable.update(track, handler);
                                        }

                                        nm.cancel(notifId);
                                        showToast("Download started");

                                    } else {

                                        tracksTable.update(Tracks.COLUMN_SOUNDCLOUD_URL, soundCloudUrl, Tracks.COLUMN_IS_DOWNLOADED, Tracks.TRUE, handler);
                                        tracksTable.update(Tracks.COLUMN_SOUNDCLOUD_URL, soundCloudUrl, Tracks.COLUMN_ABS_FILE_PATH, absFilePath, handler);
                                        showErrorNotification("Existing track :" + title + "", absFilePath);
                                    }

                                } else {
                                    //It's a playlist
                                    showToast("Playlist ready!");

                                    final String playlistName = joData.getString("playlist_name");
                                    final String username = joData.getString("username");

                                    String artworkUrl = null;
                                    if (joData.has(Playlists.COLUMN_ARTWORK_URL)) {
                                        artworkUrl = joData.getString(Playlists.COLUMN_ARTWORK_URL);
                                    }

                                    final Intent playListDownloadIntent = new Intent(DownloaderService.this, PlaylistDownloadActivity.class);

                                    playListDownloadIntent.putExtra(Playlist.KEY, new Playlist(null, playlistName, username, soundCloudUrl, artworkUrl, -1, -1, -1));
                                    playListDownloadIntent.putExtra(PlaylistDownloadActivity.KEY_TRACKS, jaTracks.toString());


                                    playListDownloadIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                    startActivity(playListDownloadIntent);

                                    nm.cancel(notifId);
                                }


                            } catch (APIResponse.APIException | JSONException e) {
                                e.printStackTrace();
                                showErrorNotification(e.getMessage(), null);
                                showToast("ERROR: " + e.getMessage());
                            }
                        }

                    });

                } else {
                    showToast(R.string.network_error);
                }
            } else {
                Log.e(X, "SoundCloud url is null");
            }


        } else {
            Log.e(X, "Intent can't be null");
        }


        return START_STICKY;
    }

    private void showErrorNotification(String message, String absFilePath) {
        apiNotification.setContentTitle(message)
                .setContentText(absFilePath)
                .setProgress(0, 0, false);
        nm.notify(notifId, apiNotification.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static PendingIntent getDismissIntent(int notifId, Context context) {
        final Intent intent = new Intent(context, DownloaderService.class);
        intent.putExtra(KEY_NOTIFICATION_ID, notifId);
        return PendingIntent.getService(context, 2, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
