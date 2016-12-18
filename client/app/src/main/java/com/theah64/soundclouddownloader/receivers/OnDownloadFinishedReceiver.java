package com.theah64.soundclouddownloader.receivers;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.theah64.soundclouddownloader.database.Tracks;
import com.theah64.soundclouddownloader.models.Track;

public class OnDownloadFinishedReceiver extends BroadcastReceiver {

    private static final String X = OnDownloadFinishedReceiver.class.getSimpleName();

    public OnDownloadFinishedReceiver() {
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        final String downloadId = String.valueOf(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
        Log.d(X, "Download finished :  id : " + downloadId);

/*        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);

        final DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        final Cursor cursor = dm.query(query);

        Log.d(X, "Cursor : " + cursor);
        if (cursor != null) {

            if (cursor.moveToFirst()) {
                final String uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI));
                final String LocalUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                Log.d(X, "URI: " + uri + "\nLOCAL URI:" + LocalUri);
            }

            cursor.close();
        }*/

        final Tracks tracksTable = Tracks.getInstance(context);

        if (!tracksTable.update(Tracks.COLUMN_DOWNLOAD_ID, downloadId, Tracks.COLUMN_IS_DOWNLOADED, Tracks.TRUE, handler)) {
            throw new IllegalArgumentException("Failed to update download status");
        }

        final Track downloadedTrack = tracksTable.get(Tracks.COLUMN_DOWNLOAD_ID, downloadId);

        if (downloadedTrack != null) {
            Toast.makeText(context, "Track downloaded -> " + downloadedTrack.getTitle(), Toast.LENGTH_SHORT).show();
        }
    }
}
