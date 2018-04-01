package net.djeebus.mopidyauto;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import net.djeebus.mopidyauto.client.MopidyClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.support.v4.media.session.PlaybackStateCompat.*;
import static net.djeebus.mopidyauto.ConnectActivity.PREFS_CONFIG;
import static net.djeebus.mopidyauto.ConnectActivity.PREFS_CONFIG_HOST;

public class MopidyService extends MediaBrowserServiceCompat {
    private static final String TAG = "MopidyService";
    private static final float PLAYBACK_SPEED = 1.0f;

    private MediaSessionCompat mSession;

    private final Handler handler = new Handler();

    private MopidyClient client;
    private String host;

    int playbackState = PlaybackState.STATE_PAUSED;
    long position = 0;
    long tlid = 1;

    PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                    PlaybackStateCompat.ACTION_PLAY_PAUSE |
                    PlaybackStateCompat.ACTION_SET_REPEAT_MODE |
                    PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE);

    int translateState(String state) {
        switch (state) {
            case "playing": return PlaybackStateCompat.STATE_PLAYING;
            case "paused": return PlaybackStateCompat.STATE_PAUSED;
            case "stopped": return PlaybackStateCompat.STATE_PAUSED;
            default: return PlaybackStateCompat.STATE_NONE;
        }
    }

    private final Runnable updateState = new Runnable() {
        @Override
        public synchronized void run() {
            if (client.isConnected()) {
                updateState();
            }

            handler.postDelayed(updateState, 1000);
        }

        void updateState() {
            client.request("core.playback.get_time_position", response -> {
                position = response.getAsLong();

                stateBuilder.setState(playbackState, position, PLAYBACK_SPEED);
                mSession.setPlaybackState(stateBuilder.build());
            });
        }
    };

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        super.onCreate();

        SharedPreferences preferences = this.getSharedPreferences(
                PREFS_CONFIG, MODE_PRIVATE);
        host = preferences.getString(PREFS_CONFIG_HOST, "");
        if (TextUtils.isEmpty(host)) {
            Log.i(TAG, "A host has not been configured yet, bailing");
            return;
        }

        client = new MopidyClient() {

            @Override
            protected void onClosed() {
                Log.i(TAG, "Mopidy client closed, reopening");

                this.open(host);
            }
        };
        client.setEventListener(this::onEvent);
        client.open(host);

        mSession = new MediaSessionCompat(this, "MopidyService");
        setSessionToken(mSession.getSessionToken());
        mSession.setCallback(new MediaSessionCallback());
        mSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS |
                MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

        handler.postDelayed(updateState, 1000);

        refreshOptions();

        refreshCurrentTrack();

        refreshTracklist();
    }

    void refreshTracklist() {
        client.request("core.tracklist.get_tl_tracks", response -> {
            JsonArray tracks = response.getAsJsonArray();
            ArrayList<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
            for (JsonElement element : tracks) {
                JsonObject playlistInfo = element.getAsJsonObject();
                Long tlid = playlistInfo.get("tlid").getAsLong();
                JsonObject track = playlistInfo.get("track").getAsJsonObject();
                MediaDescriptionCompat desc = buildMediaDescription(track);
                MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(desc, tlid);
                queue.add(queueItem);
            }
            mSession.setQueue(queue);
            mSession.setQueueTitle("Queue");
        });
    }

    private void sendPlaybackState() {
        this.stateBuilder.setState(this.playbackState, this.position, PLAYBACK_SPEED);
        mSession.setPlaybackState(stateBuilder.build());
    }

    private void onEvent(String event, JsonObject jsonObject) {
        switch (event) {
            case "seeked":
                this.position = jsonObject.get("time_position").getAsLong();
                sendPlaybackState();
                break;

            case "volume_changed":
                int volume = jsonObject.get("volume").getAsInt();
                break;

            case "tracklist_changed":
                refreshCurrentTrack();
                refreshTracklist();
                break;

            case "playback_state_changed":
                String newState = jsonObject.get("new_state").getAsString();
                this.playbackState = translateState(newState);
                sendPlaybackState();

                refreshCurrentTrack();
                break;

            case "options_changed":
                refreshOptions();
                break;
        }
    }

    class GetImagesRequest {
        @SerializedName("uris")
        String[] uris;

        GetImagesRequest(String[] uris) {
            this.uris = uris;
        }
    }

    void refreshCurrentTrack() {
        client.request("core.playback.get_current_tl_track", response -> {
            if (response.isJsonNull()) {
                mSession.setMetadata(null);
                return;
            }

            JsonObject track = response.getAsJsonObject().get("track").getAsJsonObject();
            String trackId = track.get("uri").getAsString();

            MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, trackId);

            String trackName = track.get("name").getAsString();
            builder.putString(MediaMetadata.METADATA_KEY_TITLE, trackName);

            if (track.has("artists")) {
                JsonArray artists = track.get("artists").getAsJsonArray();
                String artistName;
                if (artists.size() == 0) {
                    artistName = "Unknown Artist";
                } else {
                    JsonObject artist = artists.get(0).getAsJsonObject();
                    artistName = artist.get("name").getAsString();
                }
                builder.putString(MediaMetadata.METADATA_KEY_ARTIST, artistName);
            }

            if (track.has("album")) {
                JsonObject album = track.get("album").getAsJsonObject();

                if (track.has("name")) {
                    String albumName = album.get("name").getAsString();
                    builder.putString(MediaMetadata.METADATA_KEY_ALBUM, albumName);
                }

                if (album.has("num_tracks")) {
                    Long trackTotal = album.get("num_tracks").getAsLong();
                    builder.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, trackTotal);
                }

                if (album.has("date")) {
                    Long year = Long.parseLong(album.get("date").getAsString().substring(0, 3));
                    builder.putLong(MediaMetadata.METADATA_KEY_YEAR, year);
                }
            }

            if (track.has("track_no")) {
                Long trackNumber = track.get("track_no").getAsLong();
                builder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, trackNumber);
            }

            if (track.has("length")) {
                Long duration = track.get("length").getAsLong();
                builder.putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
            }

            client.request("core.library.get_images", new GetImagesRequest(new String[] {trackId}), imagesResponse -> {
                JsonArray images = imagesResponse.getAsJsonObject().get(trackId).getAsJsonArray();
                for (JsonElement imageInfo : images) {

                    String imageUrl = host + imageInfo.getAsJsonObject().get("uri").getAsString();
                    Bitmap albumArt = getBitmapFromURL(imageUrl);
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
                    break;
                }

                mSession.setMetadata(builder.build());
            });
        });
    }

    public Bitmap getBitmapFromURL(String uri) {
        Log.i(TAG, "Downloading album art: " + uri);
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(uri).openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            // Log exception
            Log.e(TAG, "Failed to download album art", e);
            return null;
        }
    }

    void refreshOptions() {
        client.request("core.tracklist.get_random", response -> {
            boolean result = response.getAsBoolean();
            mSession.setShuffleMode(result ? SHUFFLE_MODE_ALL : SHUFFLE_MODE_NONE);
        });

        client.request("core.tracklist.get_repeat", response -> {
            boolean result = response.getAsBoolean();
            mSession.setRepeatMode(result ? REPEAT_MODE_ALL : REPEAT_MODE_NONE);
        });
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        this.client.close();

        mSession.release();
    }

    class LibraryBrowse {
        @SerializedName("uri")
        private String uri;

        LibraryBrowse(String uri) {
            this.uri = uri;
        }
    }

    class Seek {
        @SerializedName("time_position")
        private Long timePosition;

        Seek(Long timePosition) {
            this.timePosition = timePosition;
        }
    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 Bundle rootHints) {
        Log.i(TAG, "onGetRoot: " + clientPackageName + ", " + clientUid);

        Bundle roots = new Bundle();
        roots.putBoolean(BrowserRoot.EXTRA_RECENT, false);
        roots.putBoolean(BrowserRoot.EXTRA_OFFLINE, true);
        roots.putBoolean(BrowserRoot.EXTRA_SUGGESTED, false);

        return new BrowserRoot("root", roots);
    }

    private MediaDescriptionCompat buildMediaDescription(JsonObject object) {
        MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                .setMediaId(object.get("uri").getAsString());

        if (object.has("name")) {
            builder.setTitle(object.get("name").getAsString());
        }

        if (object.has("artists")) {
            JsonArray artists = object.get("artists").getAsJsonArray();
            for (JsonElement artistItem : artists) {
                JsonObject artist = artistItem.getAsJsonObject();
                builder.setSubtitle(artist.get("name").getAsString());
                break;
            }
        }

        return builder.build();
    }

    @Override
    public void onLoadChildren(@NonNull final String parentMediaId,
                               @NonNull final Result<List<MediaItem>> result) {
        Log.i(TAG, "onLoadChildren: " + parentMediaId);

        // must be done to indicate that this is asynchronous
        result.detach();

        this.client.request(
                "core.library.browse",
                new LibraryBrowse("root".equals(parentMediaId) ? null : parentMediaId),
                response -> {
                    List<MediaItem> items = new ArrayList<>();

                    JsonArray array = response.getAsJsonArray();
                    for (JsonElement item : array) {
                        JsonObject ref = item.getAsJsonObject();
                        MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                                .setTitle(ref.get("name").getAsString())
                                .setMediaId(ref.get("uri").getAsString())
                                .build();

                        int flags = 0;
                        String type = ref.get("type").getAsString();
                        switch (type) {
                            case "album":
                            case "artist":
                            case "directory":
                                flags |= MediaItem.FLAG_BROWSABLE;
                                break;

                            case "track":
                                flags |= MediaItem.FLAG_PLAYABLE;
                                break;

                            default:
                                continue;
                        }

                        MediaItem mediaItem = new MediaItem(desc, flags);
                        items.add(mediaItem);
                    }

                    result.sendResult(items);
                });
    }

    class PlayTrack {
        @SerializedName("tlid")
        private Integer tlid;

        PlayTrack(Integer tlid) {
            this.tlid = tlid;
        }
    }

    private final class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            Log.i(TAG, "onPlay");

            client.request("core.playback.play");
        }

        class TrackListId {
            @SerializedName("tlid")
            Long tlid;

            TrackListId(Long queueId) {
                this.tlid = queueId;
            }
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            Log.i(TAG, "onSkipToQueueItem: " + queueId);
            client.request("core.playback.play", new TrackListId(queueId));
        }

        @Override
        public void onSeekTo(long position) {
            Log.i(TAG, "onSeekTo: " + position);

            client.request("core.playback.seek", new Seek(position));
        }

        class AddToTracklist {
            @SerializedName("uri")
            private String uri;

            @SerializedName("at_position")
            private Long atPosition;

            AddToTracklist(String uri) {
                this.uri = uri;
                this.atPosition = 0L;
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.i(TAG, "onPlayFromMediaId: " + mediaId);

            client.request("core.tracklist.add", new AddToTracklist(mediaId), response -> {
                JsonArray tracks = response.getAsJsonArray();
                JsonElement track = tracks.get(0);

                PlayTrack request = new PlayTrack(
                        track.getAsJsonObject().get("tlid").getAsInt());

                client.request("core.playback.play", request);
            });
        }

        @Override
        public void onPause() {
            Log.i(TAG, "onPause");

            client.request("core.playback.pause");

            if (playbackState == PlaybackState.STATE_PLAYING) {
                playbackState = PlaybackState.STATE_PAUSED;
            } else {
                playbackState = PlaybackState.STATE_PLAYING;
            }

            sendPlaybackState();
        }

        @Override
        public void onStop() {
            Log.i(TAG, "onStop");

            client.request("core.playback.stop");

            playbackState = PlaybackState.STATE_PAUSED;
            sendPlaybackState();
        }

        @Override
        public void onSkipToNext() {
            Log.i(TAG, "onSkipToNext");

            client.request("core.playback.next");
        }

        @Override
        public void onSkipToPrevious() {
            Log.i(TAG, "onSkipToPrevious");

            client.request("core.playback.previous");
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            Log.i(TAG, "onCustomAction: " + action);
        }

        class SearchRequest {
            @SerializedName("any")
            String[] parts;

            public SearchRequest(String[] parts) {
                this.parts = parts;
            }
        }

        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            Log.i(TAG, "onPlayFromSearch: " + query);
            String[] parts = query.split(" ");

            ArrayList<String> uris = new ArrayList<>();
            client.request("core.library.search", new SearchRequest(parts), response -> {
                JsonArray results = response.getAsJsonArray();
                ArrayList<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
                for (JsonElement element : results) {
                    JsonObject searchResult = element.getAsJsonObject();
                    JsonArray tracks = searchResult.get("tracks").getAsJsonArray();
                    for (JsonElement element2 : tracks) {
                        JsonObject result = element2.getAsJsonObject();
                        switch (result.get("__model__").getAsString()) {
                            case "Track":
                                uris.add(result.get("uri").getAsString());
                                MediaDescriptionCompat desc = buildMediaDescription(result);
                                MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(desc, tlid++);
                                queue.add(queueItem);
                                break;
                        }
                    }
                }

                if (queue.size() == 0) {
                    return;
                }

                String[] array = new String[uris.size()];
                uris.toArray(array);

                client.request("core.tracklist.clear", clearResponse -> {
                    BatchAddToTracklist addRequest = new BatchAddToTracklist(array);
                    client.request("core.tracklist.add", addRequest, addResponse -> {
                        mSession.setQueue(queue);
                        mSession.setQueueTitle("Search Results");
                        client.request("core.playback.play", new TrackListId(queue.get(0).getQueueId()));
                    });
                });
            });
        }

        class BatchAddToTracklist {
            @SerializedName("uris")
            String[] uris;

            BatchAddToTracklist(String[] uris) {
                this.uris = uris;
            }
        }


        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            Log.i(TAG, "onAddQueueItem 1");
            super.onAddQueueItem(description);
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            Log.i(TAG, "onMediaButtonEvent");
            return super.onMediaButtonEvent(mediaButtonEvent);
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description, int index) {
            Log.i(TAG, "onAddQueueItem 2");
            super.onAddQueueItem(description, index);
        }

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            Log.i(TAG, "onCommand(" + command + ")");
            super.onCommand(command, extras, cb);
        }

        @Override
        public void onFastForward() {
            Log.i(TAG, "onFastForward");
            super.onFastForward();
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            Log.i(TAG, "onPlayFromUri " + uri);
            super.onPlayFromUri(uri, extras);
        }

        @Override
        public void onPrepare() {
            Log.i(TAG, "onPrepare");
            super.onPrepare();
        }

        @Override
        public void onPrepareFromMediaId(String mediaId, Bundle extras) {
            Log.i(TAG, "onPrepareFromMediaId");
            super.onPrepareFromMediaId(mediaId, extras);
        }

        @Override
        public void onPrepareFromSearch(String query, Bundle extras) {
            Log.i(TAG, "onPrepareFromSearch");
            super.onPrepareFromSearch(query, extras);
        }

        @Override
        public void onPrepareFromUri(Uri uri, Bundle extras) {
            Log.i(TAG, "onPrepareFromUri");
            super.onPrepareFromUri(uri, extras);
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            Log.i(TAG, "onRemoveQueueItem");
            super.onRemoveQueueItem(description);
        }

        @Override
        public void onRewind() {
            Log.i(TAG, "onRewind");
            super.onRewind();
        }

        @Override
        public void onSetCaptioningEnabled(boolean enabled) {
            Log.i(TAG, "onSetCaptioningEnabled");
            super.onSetCaptioningEnabled(enabled);
        }

        @Override
        public void onSetRating(RatingCompat rating) {
            Log.i(TAG, "onSetRating");
            super.onSetRating(rating);
        }

        @Override
        public void onSetRating(RatingCompat rating, Bundle extras) {
            Log.i(TAG, "onSetRating");
            super.onSetRating(rating, extras);
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            Log.i(TAG, "onSetRepeatMode");
            super.onSetRepeatMode(repeatMode);
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            Log.i(TAG, "onSetShuffleMode");
            super.onSetShuffleMode(shuffleMode);
        }
    }
}
