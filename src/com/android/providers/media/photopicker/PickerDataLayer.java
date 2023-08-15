/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.media.photopicker;

import static android.database.DatabaseUtils.dumpCursorToString;
import static android.provider.CloudMediaProviderContract.AlbumColumns.ALL_PROJECTION;
import static android.provider.CloudMediaProviderContract.AlbumColumns.AUTHORITY;
import static android.provider.CloudMediaProviderContract.METHOD_GET_MEDIA_COLLECTION_INFO;
import static android.provider.CloudMediaProviderContract.MediaCollectionInfo.ACCOUNT_CONFIGURATION_INTENT;
import static android.provider.CloudMediaProviderContract.MediaCollectionInfo.ACCOUNT_NAME;
import static android.provider.MediaStore.MY_UID;

import static com.android.providers.media.PickerUriResolver.getAlbumUri;
import static com.android.providers.media.PickerUriResolver.getMediaCollectionInfoUri;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MergeCursor;
import android.os.Bundle;
import android.os.Trace;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.InstanceId;
import com.android.providers.media.ConfigStore;
import com.android.providers.media.photopicker.data.CloudProviderQueryExtras;
import com.android.providers.media.photopicker.data.PickerDbFacade;
import com.android.providers.media.photopicker.data.PickerSyncRequestExtras;
import com.android.providers.media.photopicker.metrics.NonUiEventLogger;
import com.android.providers.media.photopicker.sync.PickerSyncManager;
import com.android.providers.media.photopicker.sync.SyncTracker;
import com.android.providers.media.photopicker.sync.SyncTrackerRegistry;
import com.android.providers.media.util.ForegroundThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetches data for the picker UI from the db and cloud/local providers
 */
public class PickerDataLayer {
    private static final String TAG = "PickerDataLayer";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DUMP_CURSORS = false;
    private static final long CLOUD_SYNC_TIMEOUT_MILLIS = 500L;

    public static final String QUERY_ARG_LOCAL_ONLY = "android:query-arg-local-only";

    public static final String QUERY_DATE_TAKEN_BEFORE_MS = "android:query-date-taken-before-ms";

    public static final String QUERY_ROW_ID = "android:query-row-id";

    // Thread pool size should be at least equal to the number of unique work requests in
    // {@link PickerSyncManager} to ensure that any request type is not blocked on other request
    // types. It is advisable to use unique work requests because in case the number of queued
    // requests grows, they should not block other work requests.
    private static final int WORK_MANAGER_THREAD_POOL_SIZE = 5;
    @Nullable
    private static volatile Executor sWorkManagerExecutor;

    @NonNull
    private final Context mContext;
    @NonNull
    private final PickerDbFacade mDbFacade;
    @NonNull
    private final PickerSyncController mSyncController;
    @NonNull
    private final PickerSyncManager mSyncManager;
    @NonNull
    private final String mLocalProvider;
    @NonNull
    private final ConfigStore mConfigStore;

    public PickerDataLayer(@NonNull Context context, @NonNull PickerDbFacade dbFacade,
            @NonNull PickerSyncController syncController, @NonNull ConfigStore configStore) {
        this(context, dbFacade, syncController, configStore, /* schedulePeriodicSyncs */ true);
    }

    @VisibleForTesting
    public PickerDataLayer(@NonNull Context context, @NonNull PickerDbFacade dbFacade,
            @NonNull PickerSyncController syncController, @NonNull ConfigStore configStore,
            boolean schedulePeriodicSyncs) {
        mContext = requireNonNull(context);
        mDbFacade = requireNonNull(dbFacade);
        mSyncController = requireNonNull(syncController);
        mLocalProvider = requireNonNull(dbFacade.getLocalProvider());
        mConfigStore = requireNonNull(configStore);
        mSyncManager = new PickerSyncManager(
                getWorkManager(), context, configStore, schedulePeriodicSyncs);

        // Add a subscriber to config store changes to monitor the allowlist.
        mConfigStore.addOnChangeListener(
                ForegroundThread.getExecutor(),
                this::validateCurrentCloudProviderOnAllowlistChange);
    }

    /**
     * Returns {@link Cursor} with all local media part of the given album in {@code queryArgs}
     */
    public Cursor fetchLocalMedia(Bundle queryArgs) {
        queryArgs.putBoolean(QUERY_ARG_LOCAL_ONLY, true);
        return fetchMediaInternal(queryArgs);
    }

    /**
     * Returns {@link Cursor} with all local+cloud media part of the given album in
     * {@code queryArgs}
     */
    public Cursor fetchAllMedia(Bundle queryArgs) {
        queryArgs.putBoolean(QUERY_ARG_LOCAL_ONLY, false);
        return fetchMediaInternal(queryArgs);
    }

    private Cursor fetchMediaInternal(Bundle queryArgs) {
        if (DEBUG) {
            Log.d(TAG, "fetchMediaInternal() "
                    + (queryArgs.getBoolean(QUERY_ARG_LOCAL_ONLY) ? "LOCAL_ONLY" : "ALL")
                    + " args=" + queryArgs);
            Log.v(TAG, "Thread=" + Thread.currentThread() + "; Stacktrace:", new Throwable());
        }

        final CloudProviderQueryExtras queryExtras =
                CloudProviderQueryExtras.fromMediaStoreBundle(queryArgs);
        final String albumAuthority = queryExtras.getAlbumAuthority();

        Trace.beginSection(traceSectionName("fetchMediaInternal", albumAuthority));

        Cursor result = null;
        try {
            final boolean isLocalOnly = queryExtras.isLocalOnly();
            final String albumId = queryExtras.getAlbumId();
            // Use media table for all media except albums. Merged categories like,
            // favorites and video are tagged in the media table and are not a part of
            // album_media.
            if (TextUtils.isEmpty(albumId) || queryExtras.isMergedAlbum()) {
                // Refresh the 'media' table
                if (shouldSyncBeforePickerQuery()) {
                    syncAllMedia(isLocalOnly);
                } else {
                    // Wait for local sync to finish indefinitely
                    waitForSync(SyncTrackerRegistry.getLocalSyncTracker());
                    Log.i(TAG, "Local sync is complete");

                    // Wait for on cloud sync with timeout
                    if (!isLocalOnly) {
                        boolean syncIsComplete = waitForSyncWithTimeout(
                                SyncTrackerRegistry.getCloudSyncTracker(),
                                CLOUD_SYNC_TIMEOUT_MILLIS);
                        Log.i(TAG, "Finished waiting for cloud sync.  Is cloud sync complete: "
                                + syncIsComplete);
                    }
                }

                // Fetch all merged and deduped cloud and local media from 'media' table
                // This also matches 'merged' albums like Favorites because |authority| will
                // be null, hence we have to fetch the data from the picker db
                result = mDbFacade.queryMediaForUi(queryExtras.toQueryFilter());
            } else {
                if (isLocalOnly && !isLocal(albumAuthority)) {
                    // This is error condition because when cloud content is disabled, we shouldn't
                    // send any cloud albums in available albums list.
                    throw new IllegalStateException(
                            "Can't exclude cloud contents in cloud album " + albumAuthority);
                }

                // The album type here can only be local or cloud because merged categories like,
                // Favorites and Videos would hit the first condition.
                // Refresh the 'album_media' table
                if (shouldSyncBeforePickerQuery()) {
                    mSyncController.syncAlbumMedia(albumId, isLocal(albumAuthority));
                } else {
                    waitForSync(SyncTrackerRegistry.getAlbumSyncTracker(isLocal(albumAuthority)));
                    Log.i(TAG, "Album sync is complete");
                }

                // Fetch album specific media for local or cloud from 'album_media' table
                result = mDbFacade.queryAlbumMediaForUi(
                        queryExtras.toQueryFilter(), albumAuthority);
            }
            return result;
        } finally {
            Trace.endSection();
            if (DEBUG) {
                if (result == null) {
                    Log.d(TAG, "fetchMediaInternal()'s result is null");
                } else {
                    Log.d(TAG, "fetchMediaInternal() loaded " + result.getCount() + " items");
                    if (DEBUG_DUMP_CURSORS) {
                        Log.v(TAG, dumpCursorToString(result));
                    }
                }
            }
        }
    }

    private void syncAllMedia(boolean isLocalOnly) {
        if (isLocalOnly) {
            mSyncController.syncAllMediaFromLocalProvider();
        } else {
            mSyncController.syncAllMedia();
        }
    }

    private void waitForSync(@NonNull SyncTracker syncTracker) {
        waitForSyncWithTimeout(syncTracker, /* timeout */ null);
    }

    private boolean waitForSyncWithTimeout(
            @NonNull SyncTracker syncTracker,
            @Nullable Long timeoutInMillis) {
        try {
            final CompletableFuture<Void> completableFuture =
                    CompletableFuture.allOf(
                            syncTracker.pendingSyncFutures().toArray(new CompletableFuture[0]));
            if (timeoutInMillis == null) {
                completableFuture.get();
            } else {
                completableFuture.get(timeoutInMillis, TimeUnit.MILLISECONDS);
            }
            return true;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.w(TAG, "Could not wait for the sync to finish: " + e);
            return false;
        }
    }

    /**
     * Returns {@link Cursor} with all local and merged albums with local items.
     */
    public Cursor fetchLocalAlbums(Bundle queryArgs) {
        queryArgs.putBoolean(QUERY_ARG_LOCAL_ONLY, true);
        return fetchAlbumsInternal(queryArgs);
    }

    /**
     * Returns {@link Cursor} with all local, merged and cloud albums
     */
    public Cursor fetchAllAlbums(Bundle queryArgs) {
        queryArgs.putBoolean(QUERY_ARG_LOCAL_ONLY, false);
        return fetchAlbumsInternal(queryArgs);
    }

    private Cursor fetchAlbumsInternal(Bundle queryArgs) {
        if (DEBUG) {
            Log.d(TAG, "fetchAlbums() "
                    + (queryArgs.getBoolean(QUERY_ARG_LOCAL_ONLY) ? "LOCAL_ONLY" : "ALL")
                    + " args=" + queryArgs);
            Log.v(TAG, "Thread=" + Thread.currentThread() + "; Stacktrace:", new Throwable());
        }

        Trace.beginSection(traceSectionName("fetchAlbums"));

        Cursor result = null;
        try {
            final boolean isLocalOnly = queryArgs.getBoolean(QUERY_ARG_LOCAL_ONLY, false);
            // Refresh the 'media' table so that 'merged' albums (Favorites and Videos) are
            // up-to-date
            if (shouldSyncBeforePickerQuery()) {
                syncAllMedia(isLocalOnly);
            }

            final String cloudProvider = mSyncController.getCloudProvider();
            final CloudProviderQueryExtras queryExtras =
                    CloudProviderQueryExtras.fromMediaStoreBundle(queryArgs);
            final Bundle cloudMediaArgs = queryExtras.toCloudMediaBundle();
            final List<Cursor> cursors = new ArrayList<>();
            final Bundle cursorExtra = new Bundle();
            cursorExtra.putString(MediaStore.EXTRA_CLOUD_PROVIDER, cloudProvider);
            cursorExtra.putString(MediaStore.EXTRA_LOCAL_PROVIDER, mLocalProvider);

            // Favorites and Videos are merged albums.
            final Cursor mergedAlbums = mDbFacade.getMergedAlbums(queryExtras.toQueryFilter(),
                    cloudProvider);
            if (mergedAlbums != null) {
                cursors.add(mergedAlbums);
            }

            final Cursor localAlbums = queryProviderAlbums(mLocalProvider, cloudMediaArgs);
            if (localAlbums != null) {
                cursors.add(new AlbumsCursorWrapper(localAlbums, mLocalProvider));
            }

            if (!isLocalOnly) {
                final Cursor cloudAlbums = queryProviderAlbums(cloudProvider, cloudMediaArgs);
                if (cloudAlbums != null) {
                    // There's a bug in the Merge Cursor code (b/241096151) such that if the cursors
                    // being merged have different projections, the data gets corrupted post IPC.
                    // Fixing this bug requires a dessert release and will not be compatible with
                    // android T-. Hence, we're using {@link AlbumsCursorWrapper} that unifies the
                    // local and cloud album cursors' projections to {@link ALL_PROJECTION}
                    cursors.add(new AlbumsCursorWrapper(cloudAlbums, cloudProvider));
                }
            }

            if (cursors.isEmpty()) {
                return null;
            }

            result = new MergeCursor(cursors.toArray(new Cursor[cursors.size()]));
            result.setExtras(cursorExtra);
            return result;
        } finally {
            Trace.endSection();
            if (DEBUG) {
                if (result == null) {
                    Log.d(TAG, "fetchAlbumsInternal()'s result is null");
                } else {
                    Log.d(TAG, "fetchAlbumsInternal() loaded " + result.getCount() + " items");
                    if (DEBUG_DUMP_CURSORS) {
                        Log.v(TAG, dumpCursorToString(result));
                    }
                }
            }
        }
    }

    @Nullable
    public AccountInfo fetchCloudAccountInfo() {
        if (DEBUG) {
            Log.d(TAG, "fetchCloudAccountInfo()");
            Log.v(TAG, "Thread=" + Thread.currentThread() + "; Stacktrace:", new Throwable());
        }

        final String cloudProvider = mDbFacade.getCloudProvider();
        if (cloudProvider == null) {
            return null;
        }

        Trace.beginSection(traceSectionName("fetchCloudAccountInfo"));
        try {
            return fetchCloudAccountInfoInternal(cloudProvider);
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch account info from cloud provider: " + cloudProvider, e);
            return null;
        } finally {
            Trace.endSection();
        }
    }

    @Nullable
    private AccountInfo fetchCloudAccountInfoInternal(@NonNull String cloudProvider) {
        final Bundle accountBundle = mContext.getContentResolver()
                .call(getMediaCollectionInfoUri(cloudProvider), METHOD_GET_MEDIA_COLLECTION_INFO,
                        /* arg */ null, /* extras */ null);

        final String accountName = accountBundle.getString(ACCOUNT_NAME);
        if (accountName == null) {
            return null;
        }
        final Intent configIntent = accountBundle.getParcelable(ACCOUNT_CONFIGURATION_INTENT);

        return new AccountInfo(accountName, configIntent);
    }

    private Cursor queryProviderAlbums(@Nullable String authority, Bundle queryArgs) {
        if (authority == null) {
            // Can happen if there is no cloud provider
            return null;
        }

        Trace.beginSection(traceSectionName("queryProviderAlbums", authority));
        try {
            return queryProviderAlbumsInternal(authority, queryArgs);
        } finally {
            Trace.endSection();
        }
    }

    private Cursor queryProviderAlbumsInternal(@NonNull String authority, Bundle queryArgs) {
        final InstanceId instanceId = NonUiEventLogger.generateInstanceId();
        int numberOfAlbumsFetched = -1;
        NonUiEventLogger.logPickerGetAlbumsStart(instanceId, MY_UID, authority);
        try {
            final Cursor res = mContext.getContentResolver().query(getAlbumUri(authority),
                    /* projection */ null, queryArgs, /* cancellationSignal */ null);
            if (res != null) {
                numberOfAlbumsFetched = res.getCount();
            }
            return res;
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch cloud albums for: " + authority, e);
            return null;
        } finally {
            NonUiEventLogger.logPickerGetAlbumsEnd(instanceId, MY_UID, authority,
                    numberOfAlbumsFetched);
        }
    }

    private boolean isLocal(String authority) {
        return mLocalProvider.equals(authority);
    }

    private String traceSectionName(@NonNull String method) {
        return traceSectionName(method, null);
    }

    private String traceSectionName(@NonNull String method, @Nullable String authority) {
        final StringBuilder sb = new StringBuilder("PDL.")
                .append(method);
        if (authority != null) {
            sb.append('[').append(isLocal(authority) ? "local" : "cloud").append(']');
        }
        return sb.toString();
    }

    /**
     * Triggers a sync operation based on the parameters.
     */
    public void initMediaData(@NonNull PickerSyncRequestExtras syncRequestExtras) {
        if (syncRequestExtras.shouldSyncMediaData()) {
            // Sync media data
            Log.i(TAG, "Init data request for the main photo grid i.e. media data."
                    + " Should sync with local provider only: "
                    + syncRequestExtras.shouldSyncLocalOnlyData());

            mSyncManager.syncMediaImmediately(syncRequestExtras.shouldSyncLocalOnlyData());
        } else {
            // Sync album media data
            Log.i(TAG, String.format("Init data request for album content of: %s"
                            + " Should sync with local provider only: %b",
                    syncRequestExtras.getAlbumId(),
                    syncRequestExtras.shouldSyncLocalOnlyData()));

            validateAlbumMediaSyncArgs(syncRequestExtras);

            // We don't need to sync in case of merged albums
            if (!syncRequestExtras.shouldSyncMergedAlbum()) {
                mSyncManager.syncAlbumMediaForProviderImmediately(
                        syncRequestExtras.getAlbumId(),
                        syncRequestExtras.getAlbumAuthority());
            }
        }
    }

    private void validateAlbumMediaSyncArgs(PickerSyncRequestExtras syncRequestExtras) {
        if (!syncRequestExtras.shouldSyncMediaData()) {
            Objects.requireNonNull(syncRequestExtras.getAlbumId(),
                    "Album Id can't be null for an album sync request.");
            Objects.requireNonNull(syncRequestExtras.getAlbumAuthority(),
                    "Album authority can't be null for an album sync request.");
        }
        if (!syncRequestExtras.shouldSyncMediaData()
                && !syncRequestExtras.shouldSyncMergedAlbum()
                && syncRequestExtras.shouldSyncLocalOnlyData()
                && !isLocal(syncRequestExtras.getAlbumAuthority())) {
            throw new IllegalStateException(
                    "Can't exclude cloud contents in cloud album "
                            + syncRequestExtras.getAlbumAuthority());
        }
    }


    /**
     * Handles notification about media events like inserts/updates/deletes received from cloud or
     * local providers.
     */
    public void handleMediaEventNotification() {
        try {
            mSyncManager.syncAllMediaProactively();
        } catch (RuntimeException e) {
            // Catch any unchecked exceptions so that critical paths in MP that call this method are
            // not affected by Picker related issues.
            Log.e(TAG, "Could not handle media event notification ", e);
        }
    }

    public static class AccountInfo {
        public final String accountName;
        public final Intent accountConfigurationIntent;

        public AccountInfo(String accountName, Intent accountConfigurationIntent) {
            this.accountName = accountName;
            this.accountConfigurationIntent = accountConfigurationIntent;
        }
    }

    /**
     * A {@link CursorWrapper} that exposes the data stored in the underlying {@link Cursor} in the
     * {@link ALL_PROJECTION} "format", additionally overriding the {@link AUTHORITY} column.
     * Columns from the underlying that are not in the {@link ALL_PROJECTION} are ignored.
     * Missing columns (except {@link AUTHORITY}) are set with default value of {@code null}.
     */
    private static class AlbumsCursorWrapper extends CursorWrapper {
        static final String TAG = "AlbumsCursorWrapper";

        @NonNull static final Map<String, Integer> COLUMN_NAME_TO_INDEX_MAP;
        static final int AUTHORITY_COLUMN_INDEX;
        static {
            final Map<String, Integer> map = new HashMap<>();
            for (int columnIndex = 0; columnIndex < ALL_PROJECTION.length; columnIndex++) {
                map.put(ALL_PROJECTION[columnIndex], columnIndex);
            }
            COLUMN_NAME_TO_INDEX_MAP = map;
            AUTHORITY_COLUMN_INDEX = map.get(AUTHORITY);
        }

        @NonNull final String mAuthority;
        @NonNull final int[] mColumnIndexToCursorColumnIndexArray;

        boolean mAuthorityMismatchLogged = false;

        AlbumsCursorWrapper(@NonNull Cursor cursor, @NonNull String authority) {
            super(requireNonNull(cursor));
            mAuthority = requireNonNull(authority);

            mColumnIndexToCursorColumnIndexArray = new int[ALL_PROJECTION.length];
            for (int columnIndex = 0; columnIndex < ALL_PROJECTION.length; columnIndex++) {
                final String columnName = ALL_PROJECTION[columnIndex];
                final int cursorColumnIndex = cursor.getColumnIndex(columnName);
                mColumnIndexToCursorColumnIndexArray[columnIndex] = cursorColumnIndex;
            }
        }

        @Override
        public int getColumnCount() {
            return ALL_PROJECTION.length;
        }

        @Override
        public int getColumnIndex(String columnName) {
            return COLUMN_NAME_TO_INDEX_MAP.get(columnName);
        }

        @Override
        public int getColumnIndexOrThrow(String columnName)
                throws IllegalArgumentException {
            final int columnIndex = getColumnIndex(columnName);
            if (columnIndex < 0) {
                throw new IllegalArgumentException("column '" + columnName
                        + "' does not exist. Available columns: "
                        + Arrays.toString(getColumnNames()));
            }
            return columnIndex;
        }

        @Override
        public String getColumnName(int columnIndex) {
            return ALL_PROJECTION[columnIndex];
        }

        @Override
        public String[] getColumnNames() {
            return ALL_PROJECTION;
        }

        @Override
        public String getString(int columnIndex) {
            // 1. Get value from the underlying cursor.
            final int cursorColumnIndex = mColumnIndexToCursorColumnIndexArray[columnIndex];
            final String cursorValue = cursorColumnIndex != -1
                    ? getWrappedCursor().getString(cursorColumnIndex) : null;

            // 2a. If this is NOT the AUTHORITY column: just return the value.
            if (columnIndex != AUTHORITY_COLUMN_INDEX) {
                return cursorValue;
            }

            // Validity check: the cursor's authority value, if present, is expected to match the
            // mAuthority. Don't throw though, just log (at WARN). Also, only log once for the
            // cursor (we don't need 10,000 of these lines in the log).
            if (!mAuthorityMismatchLogged
                    && cursorValue != null && !cursorValue.equals(mAuthority)) {
                Log.w(TAG, "Cursor authority - '" + cursorValue + "' - is different from the "
                        + "expected authority '" + mAuthority + "'");
                mAuthorityMismatchLogged = true;
            }

            // 2b. If this IS the AUTHORITY column: "override" whatever value (which may be null)
            // is stored in the cursor.
            return mAuthority;
        }
    }

    /**
     * Initialize the {@link WorkManager} if it is not initialized already.
     *
     * @return a {@link WorkManager} object that can be used to run work requests.
     */
    @NonNull
    private WorkManager getWorkManager() {
        if (!WorkManager.isInitialized()) {
            Log.i(TAG, "Work manager not initialised. Attempting to initialise.");
            WorkManager.initialize(mContext, getWorkManagerConfiguration());
        }
        return WorkManager.getInstance(mContext);
    }

    @NonNull
    private static Configuration getWorkManagerConfiguration() {
        ensureWorkManagerExecutor();
        return new Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .setExecutor(sWorkManagerExecutor)
                .build();
    }

    private static void ensureWorkManagerExecutor() {
        if (sWorkManagerExecutor == null) {
            synchronized (PickerDataLayer.class) {
                if (sWorkManagerExecutor == null) {
                    sWorkManagerExecutor = Executors
                            .newFixedThreadPool(WORK_MANAGER_THREAD_POOL_SIZE);
                }
            }
        }
    }

    /**
     * For cloud feature enabled scenarios, sync request is sent from the
     * MediaStore.PICKER_MEDIA_INIT_CALL method call once when a fresh grid needs to be filled
     * populated data. This is because UI paginated queries are supported when cloud feature
     * enabled. This avoids triggering a sync for the same dataset for each paged query received
     * from the UI.
     */
    private boolean shouldSyncBeforePickerQuery() {
        return !mConfigStore.isCloudMediaInPhotoPickerEnabled();
    }

    /**
     * Checks the current allowed list of Cloud Provider packages, and ensures that the currently
     * set provider is a member of the allowlist. In the event the current Cloud Provider is not on
     * the list, the current Cloud Provider is removed.
     */
    private void validateCurrentCloudProviderOnAllowlistChange() {

        List<String> currentAllowlist = mConfigStore.getAllowedCloudProviderPackages();
        String currentCloudProvider = mSyncController.getCurrentCloudProviderInfo().packageName;

        if (!currentAllowlist.contains(currentCloudProvider)) {
            Log.d(
                    TAG,
                    String.format(
                            "Cloud provider allowlist was changed, and the current cloud provider"
                                    + " is no longer on the allowlist."
                                    + " Allowlist: %s"
                                    + " Current Provider: %s",
                            currentAllowlist.toString(), currentCloudProvider));
            mSyncController.notifyPackageRemoval(currentCloudProvider);
        }
    }
}
