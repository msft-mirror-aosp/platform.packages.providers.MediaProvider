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

package com.android.providers.media.photopicker.data;

import static android.content.ContentResolver.QUERY_ARG_LIMIT;
import static android.database.DatabaseUtils.dumpCursorToString;

import static com.android.providers.media.PickerUriResolver.PICKER_INTERNAL_URI;
import static com.android.providers.media.photopicker.PickerDataLayer.QUERY_DATE_TAKEN_BEFORE_MS;
import static com.android.providers.media.photopicker.PickerDataLayer.QUERY_ROW_ID;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.CloudMediaProviderContract.AlbumColumns;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;
import com.android.providers.media.PickerUriResolver;
import com.android.providers.media.photopicker.data.model.Category;
import com.android.providers.media.photopicker.data.model.UserId;

import java.util.Arrays;

/**
 * Provides image and video items from {@link MediaStore} collection to the Photo Picker.
 */
public class ItemsProvider {
    private static final String TAG = ItemsProvider.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DUMP_CURSORS = false;

    private final Context mContext;

    public ItemsProvider(Context context) {
        mContext = context;
    }

    private static final Uri URI_MEDIA_ALL;
    private static final Uri URI_MEDIA_LOCAL;
    private static final Uri URI_ALBUMS_ALL;
    private static final Uri URI_ALBUMS_LOCAL;

    static {
        final Uri media = PICKER_INTERNAL_URI.buildUpon()
                .appendPath(PickerUriResolver.MEDIA_PATH).build();
        URI_MEDIA_ALL = media.buildUpon().appendPath(PickerUriResolver.ALL_PATH).build();
        URI_MEDIA_LOCAL = media.buildUpon().appendPath(PickerUriResolver.LOCAL_PATH).build();

        final Uri albums = PICKER_INTERNAL_URI.buildUpon()
                .appendPath(PickerUriResolver.ALBUM_PATH).build();
        URI_ALBUMS_ALL = albums.buildUpon().appendPath(PickerUriResolver.ALL_PATH).build();
        URI_ALBUMS_LOCAL = albums.buildUpon().appendPath(PickerUriResolver.LOCAL_PATH).build();
    }

    /**
     * Returns a {@link Cursor} to all(local + cloud) images/videos based on the param passed for
     * {@code category}, {@code limit}, {@code mimeTypes} and {@code userId}.
     *
     * <p>
     * By default, the returned {@link Cursor} sorts by latest date taken.
     *
     * @param category  the category of items to return. May be cloud, local or merged albums like
     *                  favorites or videos.
     * @param pagingParameters parameters to represent the page for which the items need to be
     *                            returned.
     * @param mimeTypes the mime type of item. {@code null} returns all images/videos that are
     *                 scanned by {@link MediaStore}.
     * @param userId the {@link UserId} of the user to get items as.
     *               {@code null} defaults to {@link UserId#CURRENT_USER}
     *
     * @return {@link Cursor} to images/videos on external storage that are scanned by
     * {@link MediaStore} or returned by cloud provider. The returned cursor is filtered based on
     * params passed, it {@code null} if there are no such images/videos. The Cursor for each item
     * contains {@link android.provider.CloudMediaProviderContract.MediaColumns}
     */
    @Nullable
    public Cursor getAllItems(Category category, PaginationParameters pagingParameters,
            @Nullable String[] mimeTypes,
            @Nullable UserId userId) throws IllegalArgumentException {
        if (DEBUG) {
            Log.d(TAG, "getAllItems() userId=" + userId + " cat=" + category
                    + " mimeTypes=" + Arrays.toString(mimeTypes) + " limit="
                    + pagingParameters.getPageSize() + " dateTakenBeforeMs="
                    + pagingParameters.getDateBeforeMs() + " rowId=" + pagingParameters.getRowId());
            Log.v(TAG, "Thread=" + Thread.currentThread() + "; Stacktrace:", new Throwable());
        }

        Trace.beginSection("ItemsProvider.getAllItems");
        try {
            return queryMedia(URI_MEDIA_ALL, pagingParameters, mimeTypes, category, userId);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Returns a {@link Cursor} to local images/videos based on the param passed for
     * {@code category}, {@code limit}, {@code mimeTypes} and {@code userId}.
     *
     * <p>
     * By default, the returned {@link Cursor} sorts by latest date taken.
     *
     * @param category  the category of items to return. May be local or merged albums like
     *                  favorites or videos.
     * @param pagingParameters parameters to represent the page for which the items need to be
     *                            returned.
     * @param mimeTypes the mime type of item. {@code null} returns all images/videos that are
     *                 scanned by {@link MediaStore}.
     * @param userId the {@link UserId} of the user to get items as.
     *               {@code null} defaults to {@link UserId#CURRENT_USER}
     *
     * @return {@link Cursor} to images/videos on external storage that are scanned by
     * {@link MediaStore}. The returned cursor is filtered based on params passed, it {@code null}
     * if there are no such images/videos. The Cursor for each item contains
     * {@link android.provider.CloudMediaProviderContract.MediaColumns}
     *
     * NOTE: We don't validate the given category is a local album. The behavior is undefined if
     * this method is called with a non-local album.
     */
    @Nullable
    public Cursor getLocalItems(Category category, PaginationParameters pagingParameters,
            @Nullable String[] mimeTypes,
            @Nullable UserId userId) throws IllegalArgumentException {
        if (DEBUG) {
            Log.d(TAG, "getLocalItems() userId=" + userId + " cat=" + category
                    + " mimeTypes=" + Arrays.toString(mimeTypes) + " limit="
                    + pagingParameters.getPageSize() + " dateTakenBeforeMs="
                    + pagingParameters.getDateBeforeMs() + " rowId=" + pagingParameters.getRowId());
            Log.v(TAG, "Thread=" + Thread.currentThread() + "; Stacktrace:", new Throwable());
        }

        Trace.beginSection("ItemsProvider.getLocalItems");
        try {
            return queryMedia(URI_MEDIA_LOCAL, pagingParameters, mimeTypes, category, userId);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Returns a {@link Cursor} to all non-empty categories in which images/videos are categorised.
     * This includes:
     * * A constant list of local categories for on-device images/videos: {@link Category}
     * * Albums provided by selected cloud provider
     *
     * @param mimeTypes the mime type of item. {@code null} returns all images/videos that are
     *                 scanned by {@link MediaStore}.
     * @param userId the {@link UserId} of the user to get categories as.
     *               {@code null} defaults to {@link UserId#CURRENT_USER}.
     *
     * @return {@link Cursor} for each category would contain {@link AlbumColumns#ALL_PROJECTION}
      * in the relative order.
     */
    @Nullable
    public Cursor getAllCategories(@Nullable String[] mimeTypes, @Nullable UserId userId) {
        if (DEBUG) {
            Log.d(TAG, "getAllCategories() userId=" + userId
                    + " mimeTypes=" + Arrays.toString(mimeTypes));
            Log.v(TAG, "Thread=" + Thread.currentThread() + "; Stacktrace:", new Throwable());
        }

        Trace.beginSection("ItemsProvider.getAllCategories");
        try {
            return queryAlbums(URI_ALBUMS_ALL, mimeTypes, userId);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Returns a {@link Cursor} to all non-empty categories in which images/videos are categorised.
     * This includes a constant list of local categories for on-device images/videos.
     *
     * @param mimeTypes the mime type of item. {@code null} returns all images/videos that are
     *                 scanned by {@link MediaStore}.
     * @param userId the {@link UserId} of the user to get categories as.
     *               {@code null} defaults to {@link UserId#CURRENT_USER}.
     *
     * @return {@link Cursor} for each category would contain {@link AlbumColumns#ALL_PROJECTION}
     * in the relative order.
     */
    @Nullable
    public Cursor getLocalCategories(@Nullable String[] mimeTypes, @Nullable UserId userId) {
        if (DEBUG) {
            Log.d(TAG, "getLocalCategories() userId=" + userId
                    + " mimeTypes=" + Arrays.toString(mimeTypes));
            Log.v(TAG, "Thread=" + Thread.currentThread() + "; Stacktrace:", new Throwable());
        }

        Trace.beginSection("ItemsProvider.getLocalCategories");
        try {
            return queryAlbums(URI_ALBUMS_LOCAL, mimeTypes, userId);
        } finally {
            Trace.endSection();
        }
    }

    @Nullable
    private Cursor queryMedia(@NonNull Uri uri, PaginationParameters paginationParameters,
            String[] mimeTypes, @NonNull Category category, @Nullable UserId userId)
            throws IllegalStateException {
        if (userId == null) {
            userId = UserId.CURRENT_USER;
        }

        if (DEBUG) {
            Log.d(TAG, "queryMedia() userId=" + userId + " uri=" + uri + " cat=" + category
                    + " mimeTypes=" + Arrays.toString(mimeTypes) + " limit="
                    + paginationParameters.getPageSize() + " date_taken_before_ms = "
                    + paginationParameters.getDateBeforeMs() + " row_id = "
                    + paginationParameters.getRowId());
            Log.v(TAG, "Thread=" + Thread.currentThread() + "; Stacktrace:", new Throwable());
        }
        Trace.beginSection("ItemsProvider.queryMedia");

        final Bundle extras = new Bundle();
        Cursor result = null;
        try (ContentProviderClient client = userId.getContentResolver(mContext)
                .acquireUnstableContentProviderClient(MediaStore.AUTHORITY)) {
            if (client == null) {
                Log.e(TAG, "Unable to acquire unstable content provider for "
                        + MediaStore.AUTHORITY);
                return null;
            }
            extras.putInt(QUERY_ARG_LIMIT, paginationParameters.getPageSize());
            if (mimeTypes != null) {
                extras.putStringArray(MediaStore.QUERY_ARG_MIME_TYPE, mimeTypes);
            }
            extras.putString(MediaStore.QUERY_ARG_ALBUM_ID, category.getId());
            extras.putString(MediaStore.QUERY_ARG_ALBUM_AUTHORITY, category.getAuthority());
            if (paginationParameters.getRowId() >= 0
                    && paginationParameters.getDateBeforeMs() >= 0) {
                extras.putInt(QUERY_ROW_ID, paginationParameters.getRowId());
                extras.putLong(QUERY_DATE_TAKEN_BEFORE_MS, paginationParameters.getDateBeforeMs());
            }

            result = client.query(uri, /* projection */ null, extras,
                    /* cancellationSignal */ null);
            return result;
        } catch (RemoteException | NameNotFoundException ignored) {
            // Do nothing, return null.
            Log.e(TAG, "Failed to query merged media with extras: "
                    + extras + ". userId = " + userId, ignored);
            return null;
        } finally {
            Trace.endSection();
            if (DEBUG) {
                if (result == null) {
                    Log.d(TAG, "queryMedia()'s result is null");
                } else {
                    Log.d(TAG, "queryMedia() loaded " + result.getCount() + " items");
                    if (DEBUG_DUMP_CURSORS) {
                        Log.v(TAG, dumpCursorToString(result));
                    }
                }
            }
        }
    }

    @Nullable
    private Cursor queryAlbums(@NonNull Uri uri, @Nullable String[] mimeTypes,
                @Nullable UserId userId) {
        if (userId == null) {
            userId = UserId.CURRENT_USER;
        }

        if (DEBUG) {
            Log.d(TAG, "queryAlbums() userId=" + userId + " uri=" + uri
                    + " mimeTypes=" + Arrays.toString(mimeTypes));
            Log.v(TAG, "Thread=" + Thread.currentThread() + "; Stacktrace:", new Throwable());
        }
        Trace.beginSection("ItemsProvider.queryAlbums");

        final Bundle extras = new Bundle();
        Cursor result = null;
        try (ContentProviderClient client = userId.getContentResolver(mContext)
                .acquireUnstableContentProviderClient(MediaStore.AUTHORITY)) {
            if (client == null) {
                Log.e(TAG, "Unable to acquire unstable content provider for "
                        + MediaStore.AUTHORITY);
                return null;
            }
            if (mimeTypes != null) {
                extras.putStringArray(MediaStore.QUERY_ARG_MIME_TYPE, mimeTypes);
            }

            result = client.query(uri, /* projection */ null, extras,
                    /* cancellationSignal */ null);
            return result;
        } catch (RemoteException | NameNotFoundException ignored) {
            // Do nothing, return null.
            Log.w(TAG, "Failed to query merged albums with extras: "
                    + extras + ". userId = " + userId, ignored);
            return null;
        } finally {
            Trace.endSection();
            if (DEBUG) {
                if (result == null) {
                    Log.d(TAG, "queryAlbums()'s result is null");
                } else {
                    Log.d(TAG, "queryAlbums() loaded " + result.getCount() + " items");
                    if (DEBUG_DUMP_CURSORS) {
                        Log.v(TAG, dumpCursorToString(result));
                    }
                }
            }
        }
    }

    public static Uri getItemsUri(String id, String authority, UserId userId) {
        final Uri uri = PickerUriResolver.getMediaUri(authority).buildUpon()
                .appendPath(id).build();

        if (userId.equals(UserId.CURRENT_USER)) {
            return uri;
        }

        return createContentUriForUser(uri, userId.getUserHandle());
    }

    private static Uri createContentUriForUser(Uri uri, UserHandle userHandle) {
        if (SdkLevel.isAtLeastS()) {
            return ContentProvider.createContentUriForUser(uri, userHandle);
        }

        return createContentUriForUserImpl(uri, userHandle);
    }

    /**
     * This method is a copy of {@link ContentProvider#createContentUriForUser(Uri, UserHandle)}
     * which is a System API added in Android S.
     */
    private static Uri createContentUriForUserImpl(Uri uri, UserHandle userHandle) {
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            throw new IllegalArgumentException(String.format(
                    "Given URI [%s] is not a content URI: ", uri));
        }

        int userId = userHandle.getIdentifier();
        if (uriHasUserId(uri)) {
            if (String.valueOf(userId).equals(uri.getUserInfo())) {
                return uri;
            }
            throw new IllegalArgumentException(String.format(
                    "Given URI [%s] already has a user ID, different from given user handle [%s]",
                    uri,
                    userId));
        }

        Uri.Builder builder = uri.buildUpon();
        builder.encodedAuthority(
                "" + userHandle.getIdentifier() + "@" + uri.getEncodedAuthority());
        return builder.build();
    }

    private static boolean uriHasUserId(Uri uri) {
        if (uri == null) return false;
        return !TextUtils.isEmpty(uri.getUserInfo());
    }
}
