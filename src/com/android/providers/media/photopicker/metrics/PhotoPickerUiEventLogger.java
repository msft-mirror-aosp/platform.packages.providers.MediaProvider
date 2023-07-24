/*
 * Copyright (C) 2022 The Android Open Source Project
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

import androidx.annotation.NonNull;

import com.android.internal.logging.InstanceId;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.providers.media.metrics.MPUiEventLoggerImpl;

public class PhotoPickerUiEventLogger {

    enum PhotoPickerEvent implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "Photo picker opened in personal profile")
        PHOTO_PICKER_OPEN_PERSONAL_PROFILE(942),
        @UiEvent(doc = "Photo picker opened in work profile")
        PHOTO_PICKER_OPEN_WORK_PROFILE(943),
        @UiEvent(doc = "Photo picker opened via GET_CONTENT intent")
        PHOTO_PICKER_OPEN_GET_CONTENT(1080),
        @UiEvent(doc = "Photo picker opened in half screen")
        PHOTO_PICKER_OPEN_HALF_SCREEN(1166),
        @UiEvent(doc = "Photo picker opened in full screen")
        PHOTO_PICKER_OPEN_FULL_SCREEN(1167),
        @UiEvent(doc = "Photo picker opened in single select mode")
        PHOTO_PICKER_OPEN_SINGLE_SELECT(1168),
        @UiEvent(doc = "Photo picker opened in multi select mode")
        PHOTO_PICKER_OPEN_MULTI_SELECT(1169),
        @UiEvent(doc = "Photo picker opened with the filter to show all images")
        PHOTO_PICKER_FILTER_ALL_IMAGES(1170),
        @UiEvent(doc = "Photo picker opened with the filter to show all videos")
        PHOTO_PICKER_FILTER_ALL_VIDEOS(1171),
        @UiEvent(doc = "Photo picker opened with some other specific filter")
        PHOTO_PICKER_FILTER_OTHER(1172),
        @UiEvent(doc = "DocumentsUi opened by clicking on Browse in Photo picker")
        PHOTO_PICKER_BROWSE_DOCUMENTSUI(1085),
        @UiEvent(doc = "Photo picker cancelled in work profile")
        PHOTO_PICKER_CANCEL_WORK_PROFILE(1125),
        @UiEvent(doc = "Photo picker cancelled in personal profile")
        PHOTO_PICKER_CANCEL_PERSONAL_PROFILE(1126),
        @UiEvent(doc = "Confirmed selection in Photo picker in work profile")
        PHOTO_PICKER_CONFIRM_WORK_PROFILE(1127),
        @UiEvent(doc = "Confirmed selection in Photo picker in personal profile")
        PHOTO_PICKER_CONFIRM_PERSONAL_PROFILE(1128),
        @UiEvent(doc = "Photo picker opened with an active cloud provider")
        PHOTO_PICKER_CLOUD_PROVIDER_ACTIVE(1198),
        @UiEvent(doc = "Photo Picker uri is queried with an unknown column")
        PHOTO_PICKER_QUERY_UNKNOWN_COLUMN(1227),
        @UiEvent(doc = "Clicked the mute / unmute button in a photo picker video preview")
        PHOTO_PICKER_VIDEO_PREVIEW_AUDIO_BUTTON_CLICK(1413),
        @UiEvent(doc = "Clicked the 'view selected' button in photo picker")
        PHOTO_PICKER_PREVIEW_ALL_SELECTED(1414),
        @UiEvent(doc = "Photo picker opened with the 'switch profile' button visible and enabled")
        PHOTO_PICKER_PROFILE_SWITCH_BUTTON_ENABLED(1415),
        @UiEvent(doc = "Photo picker opened with the 'switch profile' button visible but disabled")
        PHOTO_PICKER_PROFILE_SWITCH_BUTTON_DISABLED(1416),
        @UiEvent(doc = "Clicked the 'switch profile' button in photo picker")
        PHOTO_PICKER_PROFILE_SWITCH_BUTTON_CLICK(1417),
        @UiEvent(doc = "Exited photo picker by swiping down")
        PHOTO_PICKER_EXIT_SWIPE_DOWN(1420),
        @UiEvent(doc = "Back pressed in photo picker")
        PHOTO_PICKER_BACK_GESTURE(1421),
        @UiEvent(doc = "Action bar home button clicked in photo picker")
        PHOTO_PICKER_ACTION_BAR_HOME_BUTTON_CLICK(1422),
        @UiEvent(doc = "Expanded from half screen to full in photo picker")
        PHOTO_PICKER_FROM_HALF_TO_FULL_SCREEN(1423),
        @UiEvent(doc = "Photo picker menu opened")
        PHOTO_PICKER_MENU(1424),
        @UiEvent(doc = "User switched to the photos tab in photo picker")
        PHOTO_PICKER_TAB_PHOTOS_OPEN(1425),
        @UiEvent(doc = "User switched to the albums tab in photo picker")
        PHOTO_PICKER_TAB_ALBUMS_OPEN(1426),
        @UiEvent(doc = "Opened the device favorites album in photo picker")
        PHOTO_PICKER_ALBUM_FAVORITES_OPEN(1427),
        @UiEvent(doc = "Opened the device camera album in photo picker")
        PHOTO_PICKER_ALBUM_CAMERA_OPEN(1428),
        @UiEvent(doc = "Opened the device downloads album in photo picker")
        PHOTO_PICKER_ALBUM_DOWNLOADS_OPEN(1429),
        @UiEvent(doc = "Opened the device screenshots album in photo picker")
        PHOTO_PICKER_ALBUM_SCREENSHOTS_OPEN(1430),
        @UiEvent(doc = "Opened the device videos album in photo picker")
        PHOTO_PICKER_ALBUM_VIDEOS_OPEN(1431),
        @UiEvent(doc = "Opened a cloud album in photo picker")
        PHOTO_PICKER_ALBUM_FROM_CLOUD_OPEN(1432),
        @UiEvent(doc = "Selected a media item in the main grid")
        PHOTO_PICKER_SELECTED_ITEM_MAIN_GRID(1433),
        @UiEvent(doc = "Selected a media item in an album")
        PHOTO_PICKER_SELECTED_ITEM_ALBUM(1434),
        @UiEvent(doc = "Selected a cloud only media item")
        PHOTO_PICKER_SELECTED_ITEM_CLOUD_ONLY(1435),
        @UiEvent(doc = "Previewed a media item in the main grid")
        PHOTO_PICKER_PREVIEW_ITEM_MAIN_GRID(1436),
        @UiEvent(doc = "Loaded media items in the main grid in photo picker")
        PHOTO_PICKER_UI_LOADED_PHOTOS(1437),
        @UiEvent(doc = "Loaded albums in photo picker")
        PHOTO_PICKER_UI_LOADED_ALBUMS(1438),
        @UiEvent(doc = "Loaded media items in an album grid in photo picker")
        PHOTO_PICKER_UI_LOADED_ALBUM_CONTENTS(1439);

        private final int mId;

        PhotoPickerEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }

    private UiEventLogger logger;

    public PhotoPickerUiEventLogger() {
        logger = new MPUiEventLoggerImpl();
    }

    public void logPickerOpenPersonal(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_OPEN_PERSONAL_PROFILE,
                callingUid,
                callingPackage,
                instanceId);
    }

    public void logPickerOpenWork(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_OPEN_WORK_PROFILE,
                callingUid,
                callingPackage,
                instanceId);
    }

    public void logPickerOpenViaGetContent(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_OPEN_GET_CONTENT,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that the picker has opened in half screen
     * @param instanceId     an identifier for the current picker session
     * @param callingUid     the uid of the app initiating the picker launch
     * @param callingPackage the package name of the app initiating the picker launch
     */
    public void logPickerOpenInHalfScreen(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_OPEN_HALF_SCREEN,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that the picker has opened in full screen
     * @param instanceId     an identifier for the current picker session
     * @param callingUid     the uid of the app initiating the picker launch
     * @param callingPackage the package name of the app initiating the picker launch
     */
    public void logPickerOpenInFullScreen(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_OPEN_FULL_SCREEN,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that the picker has opened in single select mode
     * @param instanceId     an identifier for the current picker session
     * @param callingUid     the uid of the app initiating the picker launch
     * @param callingPackage the package name of the app initiating the picker launch
     */
    public void logPickerOpenInSingleSelect(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_OPEN_SINGLE_SELECT,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that the picker has opened in multi select mode
     * @param instanceId     an identifier for the current picker session
     * @param callingUid     the uid of the app initiating the picker launch
     * @param callingPackage the package name of the app initiating the picker launch
     */
    public void logPickerOpenInMultiSelect(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_OPEN_MULTI_SELECT,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that the picker has opened with the filter to show all images
     * @param instanceId     an identifier for the current picker session
     * @param callingUid     the uid of the app initiating the picker launch
     * @param callingPackage the package name of the app initiating the picker launch
     */
    public void logPickerOpenWithFilterAllImages(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_FILTER_ALL_IMAGES,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that the picker has opened with the filter to show all videos
     * @param instanceId     an identifier for the current picker session
     * @param callingUid     the uid of the app initiating the picker launch
     * @param callingPackage the package name of the app initiating the picker launch
     */
    public void logPickerOpenWithFilterAllVideos(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_FILTER_ALL_VIDEOS,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that the picker has opened with a specific filter, other than the ones
     * tracked explicitly
     * @param instanceId     an identifier for the current picker session
     * @param callingUid     the uid of the app initiating the picker launch
     * @param callingPackage the package name of the app initiating the picker launch
     */
    public void logPickerOpenWithAnyOtherFilter(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_FILTER_OTHER,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that user has clicked on "Browse..." in Photo picker overflow menu.
     * This UI click even opens DocumentsUi.
     */
    public void logBrowseToDocumentsUi(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_BROWSE_DOCUMENTSUI,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that user has confirmed selection in personal profile
     */
    public void logPickerConfirmPersonal(InstanceId instanceId, int callingUid,
            String callingPackage, int countOfItemsConfirmed) {
        logger.logWithInstanceIdAndPosition(
                PhotoPickerEvent.PHOTO_PICKER_CONFIRM_PERSONAL_PROFILE,
                callingUid,
                callingPackage,
                instanceId,
                countOfItemsConfirmed);
    }

    /**
     * Log metrics to notify that user has confirmed selection in work profile
     */
    public void logPickerConfirmWork(InstanceId instanceId, int callingUid,
            String callingPackage, int countOfItemsConfirmed) {
        logger.logWithInstanceIdAndPosition(
                PhotoPickerEvent.PHOTO_PICKER_CONFIRM_WORK_PROFILE,
                callingUid,
                callingPackage,
                instanceId,
                countOfItemsConfirmed);
    }

    /**
     * Log metrics to notify that user has cancelled picker (without any selection) in personal
     * profile
     */
    public void logPickerCancelPersonal(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_CANCEL_PERSONAL_PROFILE,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that user has cancelled picker (without any selection) in work
     * profile
     */
    public void logPickerCancelWork(InstanceId instanceId, int callingUid,
            String callingPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_CANCEL_WORK_PROFILE,
                callingUid,
                callingPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that the picker has opened with an active cloud provider
     * @param instanceId           an identifier for the current picker session
     * @param cloudProviderUid     the uid of the cloud provider app
     * @param cloudProviderPackage the package name of the cloud provider app
     */
    public void logPickerOpenWithActiveCloudProvider(InstanceId instanceId, int cloudProviderUid,
            String cloudProviderPackage) {
        logger.logWithInstanceId(
                PhotoPickerEvent.PHOTO_PICKER_CLOUD_PROVIDER_ACTIVE,
                cloudProviderUid,
                cloudProviderPackage,
                instanceId);
    }

    /**
     * Log metrics to notify that a picker uri was queried for an unknown column (that is not
     * supported yet)
     * @param callingUid     the uid of the app initiating the picker query
     * @param callingPackage the package name of the app initiating the picker query
     *
     * TODO(b/251425380): Move non-UI events out of PhotoPickerUiEventLogger
     */
    public void logPickerQueriedWithUnknownColumn(int callingUid, String callingPackage) {
        logger.log(PhotoPickerEvent.PHOTO_PICKER_QUERY_UNKNOWN_COLUMN,
                callingUid,
                callingPackage);
    }

    /**
     * Log metrics to notify that the user has clicked the mute / unmute button in a video preview
     * @param instanceId an identifier for the current picker session
     */
    public void logVideoPreviewMuteButtonClick(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_VIDEO_PREVIEW_AUDIO_BUTTON_CLICK, instanceId);
    }

    /**
     * Log metrics to notify that the user has clicked the 'view selected' button
     * @param instanceId        an identifier for the current picker session
     * @param selectedItemCount the number of items selected for preview all
     */
    public void logPreviewAllSelected(InstanceId instanceId, int selectedItemCount) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_PREVIEW_ALL_SELECTED,
                /* uid */ 0, /* packageName */ null, instanceId, selectedItemCount);
    }

    /**
     * Log metrics to notify that the 'switch profile' button is visible & enabled
     * @param instanceId an identifier for the current picker session
     */
    public void logProfileSwitchButtonEnabled(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_PROFILE_SWITCH_BUTTON_ENABLED, instanceId);
    }

    /**
     * Log metrics to notify that the 'switch profile' button is visible but disabled
     * @param instanceId an identifier for the current picker session
     */
    public void logProfileSwitchButtonDisabled(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_PROFILE_SWITCH_BUTTON_DISABLED, instanceId);
    }

    /**
     * Log metrics to notify that the user has clicked the 'switch profile' button
     * @param instanceId an identifier for the current picker session
     */
    public void logProfileSwitchButtonClick(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_PROFILE_SWITCH_BUTTON_CLICK, instanceId);
    }

    /**
     * Log metrics to notify that the user has cancelled the current session by swiping down
     * @param instanceId an identifier for the current picker session
     */
    public void logSwipeDownExit(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_EXIT_SWIPE_DOWN, instanceId);
    }

    /**
     * Log metrics to notify that the user has made a back gesture
     * @param instanceId          an identifier for the current picker session
     * @param backStackEntryCount the number of fragment entries currently in the back stack
     */
    public void logBackGestureWithStackCount(InstanceId instanceId, int backStackEntryCount) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_BACK_GESTURE, /* uid */ 0,
                /* packageName */ null, instanceId, backStackEntryCount);
    }

    /**
     * Log metrics to notify that the user has clicked the action bar home button
     * @param instanceId          an identifier for the current picker session
     * @param backStackEntryCount the number of fragment entries currently in the back stack
     */
    public void logActionBarHomeButtonClick(InstanceId instanceId, int backStackEntryCount) {
        logger.logWithInstanceIdAndPosition(
                PhotoPickerEvent.PHOTO_PICKER_ACTION_BAR_HOME_BUTTON_CLICK, /* uid */ 0,
                /* packageName */ null, instanceId, backStackEntryCount);
    }

    /**
     * Log metrics to notify that the user has expanded from half screen to full
     * @param instanceId an identifier for the current picker session
     */
    public void logExpandToFullScreen(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_FROM_HALF_TO_FULL_SCREEN, instanceId);
    }

    /**
     * Log metrics to notify that the user has opened the photo picker menu
     * @param instanceId an identifier for the current picker session
     */
    public void logMenuOpened(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_MENU, instanceId);
    }

    /**
     * Log metrics to notify that the user has switched to the photos tab
     * @param instanceId an identifier for the current picker session
     */
    public void logSwitchToPhotosTab(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_TAB_PHOTOS_OPEN, instanceId);
    }

    /**
     * Log metrics to notify that the user has switched to the albums tab
     * @param instanceId an identifier for the current picker session
     */
    public void logSwitchToAlbumsTab(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_TAB_ALBUMS_OPEN, instanceId);
    }

    /**
     * Log metrics to notify that the user has opened the device favorites album
     * @param instanceId an identifier for the current picker session
     */
    public void logFavoritesAlbumOpened(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_ALBUM_FAVORITES_OPEN, instanceId);
    }

    /**
     * Log metrics to notify that the user has opened the device camera album
     * @param instanceId an identifier for the current picker session
     */
    public void logCameraAlbumOpened(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_ALBUM_CAMERA_OPEN, instanceId);
    }

    /**
     * Log metrics to notify that the user has opened the device downloads album
     * @param instanceId an identifier for the current picker session
     */
    public void logDownloadsAlbumOpened(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_ALBUM_DOWNLOADS_OPEN, instanceId);
    }

    /**
     * Log metrics to notify that the user has opened the device screenshots album
     * @param instanceId an identifier for the current picker session
     */
    public void logScreenshotsAlbumOpened(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_ALBUM_SCREENSHOTS_OPEN, instanceId);
    }

    /**
     * Log metrics to notify that the user has opened the device videos album
     * @param instanceId an identifier for the current picker session
     */
    public void logVideosAlbumOpened(InstanceId instanceId) {
        logWithInstance(PhotoPickerEvent.PHOTO_PICKER_ALBUM_VIDEOS_OPEN, instanceId);
    }

    /**
     * Log metrics to notify that the user has opened a cloud album
     * @param instanceId an identifier for the current picker session
     * @param position   the position of the album in the recycler view
     */
    public void logCloudAlbumOpened(InstanceId instanceId, int position) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_ALBUM_FROM_CLOUD_OPEN,
                /* uid */ 0, /* packageName */ null, instanceId, position);
    }

    /**
     * Log metrics to notify that the user selected a media item in the main grid
     * @param instanceId an identifier for the current picker session
     * @param position   the position of the album in the recycler view
     */
    public void logSelectedMainGridItem(InstanceId instanceId, int position) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_SELECTED_ITEM_MAIN_GRID,
                /* uid */ 0, /* packageName */ null, instanceId, position);
    }

    /**
     * Log metrics to notify that the user selected a media item in an album
     * @param instanceId an identifier for the current picker session
     * @param position   the position of the album in the recycler view
     */
    public void logSelectedAlbumItem(InstanceId instanceId, int position) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_SELECTED_ITEM_ALBUM,
                /* uid */ 0, /* packageName */ null, instanceId, position);
    }

    /**
     * Log metrics to notify that the user has selected a cloud only media item
     * @param instanceId an identifier for the current picker session
     * @param position   the position of the album in the recycler view
     */
    public void logSelectedCloudOnlyItem(InstanceId instanceId, int position) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_SELECTED_ITEM_CLOUD_ONLY,
                /* uid */ 0, /* packageName */ null, instanceId, position);
    }

    /**
     * Log metrics to notify that the user has previewed an item in the main grid
     * @param specialFormat the special format of the previewed item (used to identify special
     *                      categories like motion photos)
     * @param mimeType      the mime type of the previewed item
     * @param instanceId    an identifier for the current picker session
     * @param position      the position of the album in the recycler view
     */
    public void logPreviewedMainGridItem(
            int specialFormat, String mimeType, InstanceId instanceId, int position) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_PREVIEW_ITEM_MAIN_GRID,
                specialFormat, mimeType, instanceId, position);
    }

    /**
     * Log metrics to notify that the picker has loaded some media items in the main grid
     * @param authority  the authority of the selected cloud provider, null if no non-local items
     * @param instanceId an identifier for the current picker session
     * @param count      the number of media items loaded
     */
    public void logLoadedMainGridMediaItems(String authority, InstanceId instanceId, int count) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_UI_LOADED_PHOTOS,
                /* uid */ 0, authority, instanceId, count);
    }

    /**
     * Log metrics to notify that the picker has loaded some albums
     * @param authority  the authority of the selected cloud provider, null if no non-local albums
     * @param instanceId an identifier for the current picker session
     * @param count      the number of albums loaded
     */
    public void logLoadedAlbums(String authority, InstanceId instanceId, int count) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_UI_LOADED_ALBUMS,
                /* uid */ 0, authority, instanceId, count);
    }

    /**
     * Log metrics to notify that the picker has loaded some media items in an album grid
     * @param authority  the authority of the selected cloud provider, null if no non-local items
     * @param instanceId an identifier for the current picker session
     * @param count      the number of media items loaded
     */
    public void logLoadedAlbumGridMediaItems(String authority, InstanceId instanceId, int count) {
        logger.logWithInstanceIdAndPosition(PhotoPickerEvent.PHOTO_PICKER_UI_LOADED_ALBUM_CONTENTS,
                /* uid */ 0, authority, instanceId, count);
    }

    private void logWithInstance(@NonNull UiEventLogger.UiEventEnum event, InstanceId instance) {
        logger.logWithInstanceId(event, /* uid */ 0, /* packageName */ null, instance);
    }
}
