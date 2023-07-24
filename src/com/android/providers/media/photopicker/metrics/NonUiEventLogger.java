/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.providers.media.photopicker.metrics;

import com.android.internal.logging.InstanceId;
import com.android.internal.logging.InstanceIdSequence;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.providers.media.metrics.MPUiEventLoggerImpl;

/**
 * Logger for the Non UI Events triggered indirectly by some UI event(s).
 */
public class NonUiEventLogger {
    enum NonUiEvent implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "User changed the active Photo picker cloud provider")
        PHOTO_PICKER_CLOUD_PROVIDER_CHANGED(1135),
        @UiEvent(doc = "Triggered a full sync in photo picker")
        PHOTO_PICKER_FULL_SYNC_START(1442),
        @UiEvent(doc = "Triggered an incremental sync in photo picker")
        PHOTO_PICKER_INCREMENTAL_SYNC_START(1443),
        @UiEvent(doc = "Triggered an album media sync in photo picker")
        PHOTO_PICKER_ALBUM_MEDIA_SYNC_START(1444),
        @UiEvent(doc = "Ended an add media sync in photo picker")
        PHOTO_PICKER_ADD_MEDIA_SYNC_END(1445),
        @UiEvent(doc = "Ended a remove media sync in photo picker")
        PHOTO_PICKER_REMOVE_MEDIA_SYNC_END(1446),
        @UiEvent(doc = "Ended an add album media sync in photo picker")
        PHOTO_PICKER_ADD_ALBUM_MEDIA_SYNC_END(1447);

        private final int mId;

        NonUiEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }

    private static final int INSTANCE_ID_MAX = 1 << 15;
    private static final InstanceIdSequence INSTANCE_ID_SEQUENCE =
            new InstanceIdSequence(INSTANCE_ID_MAX);
    private static final UiEventLogger LOGGER = new MPUiEventLoggerImpl();

    /**
     * Generate and {@return} a new unique instance id to group some events for aggregated metrics
     */
    public static InstanceId generateInstanceId() {
        return INSTANCE_ID_SEQUENCE.newInstanceId();
    }

    /**
     * Log metrics to notify that the user has changed the active cloud provider
     * @param cloudProviderUid     new active cloud provider uid
     * @param cloudProviderPackage new active cloud provider package name
     */
    public static void logPickerCloudProviderChanged(int cloudProviderUid,
            String cloudProviderPackage) {
        LOGGER.log(NonUiEvent.PHOTO_PICKER_CLOUD_PROVIDER_CHANGED, cloudProviderUid,
                cloudProviderPackage);
    }

    /**
     * Log metrics to notify that a full sync started
     * @param instanceId an identifier for the current sync
     * @param uid        the uid of the MediaProvider logging this metric
     * @param authority  the authority of the provider syncing with
     */
    public static void logPickerFullSyncStart(InstanceId instanceId, int uid, String authority) {
        LOGGER.logWithInstanceId(NonUiEvent.PHOTO_PICKER_FULL_SYNC_START, uid, authority,
                instanceId);
    }

    /**
     * Log metrics to notify that an incremental sync started
     * @param instanceId an identifier for the current sync
     * @param uid        the uid of the MediaProvider logging this metric
     * @param authority  the authority of the provider syncing with
     */
    public static void logPickerIncrementalSyncStart(InstanceId instanceId, int uid,
            String authority) {
        LOGGER.logWithInstanceId(NonUiEvent.PHOTO_PICKER_INCREMENTAL_SYNC_START, uid, authority,
                instanceId);
    }

    /**
     * Log metrics to notify that an album media sync started
     * @param instanceId an identifier for the current sync
     * @param uid        the uid of the MediaProvider logging this metric
     * @param authority  the authority of the provider syncing with
     */
    public static void logPickerAlbumMediaSyncStart(InstanceId instanceId, int uid,
            String authority) {
        LOGGER.logWithInstanceId(NonUiEvent.PHOTO_PICKER_ALBUM_MEDIA_SYNC_START, uid, authority,
                instanceId);
    }

    /**
     * Log metrics to notify that an add media sync ended
     * @param instanceId an identifier for the current sync
     * @param uid        the uid of the MediaProvider logging this metric
     * @param authority  the authority of the provider syncing with
     * @param count      the number of items synced
     */
    public static void logPickerAddMediaSyncCompletion(InstanceId instanceId, int uid,
            String authority, int count) {
        LOGGER.logWithInstanceIdAndPosition(NonUiEvent.PHOTO_PICKER_ADD_MEDIA_SYNC_END, uid,
                authority, instanceId, count);
    }

    /**
     * Log metrics to notify that a remove media sync ended
     * @param instanceId an identifier for the current sync
     * @param uid        the uid of the MediaProvider logging this metric
     * @param authority  the authority of the provider syncing with
     * @param count      the number of items synced
     */
    public static void logPickerRemoveMediaSyncCompletion(InstanceId instanceId, int uid,
            String authority, int count) {
        LOGGER.logWithInstanceIdAndPosition(NonUiEvent.PHOTO_PICKER_REMOVE_MEDIA_SYNC_END, uid,
                authority, instanceId, count);
    }

    /**
     * Log metrics to notify that an add album media sync ended
     * @param instanceId an identifier for the current sync
     * @param uid        the uid of the MediaProvider logging this metric
     * @param authority  the authority of the provider syncing with
     * @param count      the number of items synced
     */
    public static void logPickerAddAlbumMediaSyncCompletion(InstanceId instanceId, int uid,
            String authority, int count) {
        LOGGER.logWithInstanceIdAndPosition(NonUiEvent.PHOTO_PICKER_ADD_ALBUM_MEDIA_SYNC_END, uid,
                authority, instanceId, count);
    }
}
