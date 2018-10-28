package net.djeebus.mopidyauto;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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
import net.djeebus.mopidyauto.client.MopidyBluetoothClient;
import net.djeebus.mopidyauto.client.MopidyRequest;
import net.djeebus.mopidyauto.messages.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.support.v4.media.session.PlaybackStateCompat.*;
import static net.djeebus.mopidyauto.FindBluetoothActivity.BT_ADDR;
import static net.djeebus.mopidyauto.FindBluetoothActivity.PREFS_CONFIG;

public class MopidyService extends MediaBrowserServiceCompat {
    private static final String TAG = "MopidyService";
    private static final float PLAYBACK_SPEED = 1.0f;

    private MediaSessionCompat mSession;

    private final Handler handler = new Handler();

    private MopidyBluetoothClient client;
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
            case "playing":
                return PlaybackStateCompat.STATE_PLAYING;
            case "paused":
                return PlaybackStateCompat.STATE_PAUSED;
            case "stopped":
                return PlaybackStateCompat.STATE_PAUSED;
            default:
                return PlaybackStateCompat.STATE_NONE;
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
                setPosition(position);

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
        host = preferences.getString(BT_ADDR, "");
        if (TextUtils.isEmpty(host)) {
            Log.i(TAG, "A host has not been configured yet, bailing");
            return;
        }

        client = new MopidyBluetoothClient() {

            @Override
            protected void onClosed() {
                Log.i(TAG, "Mopidy client closed");
            }
        };
        client.setEventListener(this::onEvent);


        try {
            client.open(host);
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect to mopidy");
            return;
        }

        mSession = new MediaSessionCompat(this, "MopidyService");
        setSessionToken(mSession.getSessionToken());
        mSession.setCallback(new MediaSessionCallback());
        mSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS |
                        MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

        handler.postDelayed(updateState, 1000);

        synchronizeInterface();
    }

    void synchronizeInterface() {
        this.client.request(
                new MopidyRequest("core.tracklist.get_random", response -> {
                    boolean result = response.getAsBoolean();
                    mSession.setShuffleMode(result ? SHUFFLE_MODE_ALL : SHUFFLE_MODE_NONE);
                }),
                new MopidyRequest("core.tracklist.get_repeat", response -> {
                    boolean result = response.getAsBoolean();
                    mSession.setRepeatMode(result ? REPEAT_MODE_ALL : REPEAT_MODE_NONE);
                }),
                new MopidyRequest("core.playback.get_current_tl_track", this::processGetCurrentTrackListResponse),
                new MopidyRequest("core.tracklist.get_tl_tracks", this::processRefreshTrackListResponse)
        );
    }

    void processRefreshTrackListResponse(JsonElement response) {
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
    }

    private void setPosition(long position) {
        this.position = position;

        this.sendPlaybackState();
    }

    private void setPlaybackState(Integer playbackState) {
        this.playbackState = playbackState;

        this.sendPlaybackState();
    }

    private void sendPlaybackState() {
        this.stateBuilder.setState(this.playbackState, this.position, PLAYBACK_SPEED);
        mSession.setPlaybackState(stateBuilder.build());
    }

    private void onEvent(String event, JsonObject jsonObject) {
        switch (event) {
            case "seeked":
                setPosition(jsonObject.get("time_position").getAsLong());
                sendPlaybackState();
                break;

            case "volume_changed":
                /* int volume = */
                jsonObject.get("volume").getAsInt();
                break;

            case "tracklist_changed":
                this.client.request(
                        new MopidyRequest("core.playback.get_current_tl_track", this::processGetCurrentTrackListResponse),
                        new MopidyRequest("core.tracklist.get_tl_tracks", this::processRefreshTrackListResponse)
                );
                break;

            case "playback_state_changed":
                String newState = jsonObject.get("new_state").getAsString();
                setPlaybackState(translateState(newState));

                client.request(
                        "core.playback.get_current_tl_track", this::processGetCurrentTrackListResponse
                );
                break;

            case "options_changed":
                this.client.request(
                        new MopidyRequest("core.tracklist.get_random", response -> {
                            boolean result = response.getAsBoolean();
                            mSession.setShuffleMode(result ? SHUFFLE_MODE_ALL : SHUFFLE_MODE_NONE);
                        }),
                        new MopidyRequest("core.tracklist.get_repeat", response -> {
                            boolean result = response.getAsBoolean();
                            mSession.setRepeatMode(result ? REPEAT_MODE_ALL : REPEAT_MODE_NONE);
                        })
                );
                break;
        }
    }

    void processGetCurrentTrackListResponse(JsonElement response) {
        if (response.isJsonNull()) {
            mSession.setMetadata(null);
            return;
        }

        JsonObject track = response.getAsJsonObject().get("track").getAsJsonObject();
        String trackId = track.get("uri").getAsString();

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, trackId);

        if (track.has("name")) {
            String trackName = track.get("name").getAsString();
            builder.putString(MediaMetadata.METADATA_KEY_TITLE, trackName);
        }

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

        client.request(
                new MopidyRequest(
                        "core.library.get_images",
                        new GetImagesRequest(new String[]{trackId}),
                        response1 -> {
                            JsonArray images = response1.getAsJsonObject().get(trackId).getAsJsonArray();
                            for (JsonElement imageInfo : images) {

                                String imageUrl = host + imageInfo.getAsJsonObject().get("uri").getAsString();
                                Bitmap albumArt = client.getBitmapFromURL(imageUrl);
                                if (albumArt == null) {
                                    continue;
                                }

                                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
                                break;
                            }

                            mSession.setMetadata(builder.build());
                        })
        );
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        if (this.client != null) {
            this.client.close();
        }

        mSession.release();
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
                if (artist.has("name")) {
                    builder.setSubtitle(artist.get("name").getAsString());
                    break;
                }
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
                new MopidyRequest(
                        "core.library.browse",
                        new LibraryBrowseRequest("root".equals(parentMediaId) ? null : parentMediaId),
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
                        })
        );
    }

    private final class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            Log.i(TAG, "onPlay");

            client.request("core.playback.play", response -> setPlaybackState(PlaybackState.STATE_PLAYING));
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            Log.i(TAG, "onSkipToQueueItem: " + queueId);
            client.request("core.playback.play", new TrackListId(queueId), response -> setPlaybackState(PlaybackState.STATE_PLAYING));
        }

        @Override
        public void onSeekTo(long position) {
            Log.i(TAG, "onSeekTo: " + position);

            client.request("core.playback.seek", new SeekRequest(position), response -> setPosition(position));
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.i(TAG, "onPlayFromMediaId: " + mediaId);

            client.request(new MopidyRequest("core.tracklist.add", new AddToTracklist(mediaId), response1 -> {
                    JsonArray tracks = response1.getAsJsonArray();
                    JsonElement track = tracks.get(0);

                    client.request(
                            "core.playback.play",
                            new PlayTrackRequest(track.getAsJsonObject().get("tlid").getAsInt()),
                            response2 -> setPlaybackState(PlaybackState.STATE_PLAYING)
                    );
            }));
        }

        @Override
        public void onPause() {
            Log.i(TAG, "onPause");

            client.request("core.playback.pause", response -> setPlaybackState(PlaybackState.STATE_PAUSED));
        }

        @Override
        public void onStop() {
            Log.i(TAG, "onStop");

            client.request("core.playback.stop", response -> setPlaybackState(PlaybackState.STATE_STOPPED));
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
