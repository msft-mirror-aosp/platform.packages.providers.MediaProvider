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

package com.android.providers.media.photopicker.ui.settings;

import static android.provider.MediaStore.AUTHORITY;
import static android.provider.MediaStore.EXTRA_CLOUD_PROVIDER;
import static android.provider.MediaStore.GET_CLOUD_PROVIDER_CALL;
import static android.provider.MediaStore.GET_CLOUD_PROVIDER_RESULT;
import static android.provider.MediaStore.SET_CLOUD_PROVIDER_CALL;

import static java.util.Objects.requireNonNull;

import android.content.ContentProviderClient;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.lifecycle.ViewModel;

import com.android.providers.media.ConfigStore;
import com.android.providers.media.R;
import com.android.providers.media.photopicker.data.CloudProviderInfo;
import com.android.providers.media.photopicker.data.model.UserId;
import com.android.providers.media.photopicker.util.CloudProviderUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SettingsCloudMediaViewModel stores cloud media app settings data for each profile.
 */
public class SettingsCloudMediaViewModel extends ViewModel {
    public static final String NONE_PREF_KEY = "none";
    private static final String TAG = "SettingsFragVM";

    @NonNull
    private final Context mContext;
    @NonNull
    private final List<CloudMediaProviderOption> mProviderOptions;
    @NonNull
    private final UserId mUserId;
    @Nullable
    private String mSelectedProviderAuthority;

    public SettingsCloudMediaViewModel(@NonNull Context context, @NonNull UserId userId) {
        super();

        mContext = requireNonNull(context);
        mUserId = requireNonNull(userId);
        mProviderOptions = new ArrayList<>();
        mSelectedProviderAuthority = null;
    }

    @NonNull
    public List<CloudMediaProviderOption> getProviderOptions() {
        return mProviderOptions;
    }

    @Nullable
    public String getSelectedProviderAuthority() {
        return mSelectedProviderAuthority;
    }

    @Nullable
    public String getSelectedPreferenceKey() {
        return getPreferenceKey(mSelectedProviderAuthority);
    }

    /**
     * Fetch and cache the available cloud provider options and the selected provider.
     */
    public void loadData(@NonNull ConfigStore configStore) {
        refreshProviderOptions(configStore);
        refreshSelectedProvider();
    }

    /**
     * Updates the selected cloud provider on disk and in cache.
     * Returns true if the update was successful.
     */
    public boolean updateSelectedProvider(@NonNull String newPreferenceKey) {
        final String newCloudProvider = getProviderAuthority(newPreferenceKey);
        final boolean success = persistSelectedProvider(newCloudProvider);
        if (success) {
            mSelectedProviderAuthority = newCloudProvider;
            return true;
        }
        return false;
    }

    @Nullable
    private String getProviderAuthority(@NonNull String preferenceKey) {
        // For None option, the provider auth should be null to disable cloud media provider.
        return preferenceKey.equals(SettingsCloudMediaViewModel.NONE_PREF_KEY)
                ? null : preferenceKey;
    }

    @Nullable
    private String getPreferenceKey(@Nullable String providerAuthority) {
        return providerAuthority == null
                ? SettingsCloudMediaViewModel.NONE_PREF_KEY : providerAuthority;
    }

    private void refreshProviderOptions(@NonNull ConfigStore configStore) {
        mProviderOptions.clear();
        mProviderOptions.addAll(fetchProviderOptions(configStore));
        mProviderOptions.add(getNoneProviderOption());
    }

    private void refreshSelectedProvider() {
        mSelectedProviderAuthority = fetchCurrentProviderAuthority();
    }

    @NonNull
    private List<CloudMediaProviderOption> fetchProviderOptions(@NonNull ConfigStore configStore) {
        // Get info of available cloud providers.
        List<CloudProviderInfo> cloudProviders =
                CloudProviderUtils.getAllAvailableCloudProviders(
                        mContext, configStore, UserHandle.of(mUserId.getIdentifier()));

        return getProviderOptionsFromCloudProviderInfos(cloudProviders);
    }

    @NonNull
    private List<CloudMediaProviderOption> getProviderOptionsFromCloudProviderInfos(
            @NonNull List<CloudProviderInfo> cloudProviders) {
        // TODO(b/195009187): In case current cloud provider is not part of the allow list, it will
        // not be listed on the Settings page. Handle this case so that it does show up.
        final List<CloudMediaProviderOption> providerOption = new ArrayList<>();
        for (CloudProviderInfo cloudProvider : cloudProviders) {
            providerOption.add(
                    CloudMediaProviderOption
                            .fromCloudProviderInfo(cloudProvider, mContext, mUserId));
        }
        return providerOption;
    }

    @NonNull
    private CloudMediaProviderOption getNoneProviderOption() {
        final Drawable nonePrefIcon = AppCompatResources
                .getDrawable(this.mContext, R.drawable.ic_cloud_picker_off);
        final String nonePrefLabel = this.mContext.getString(R.string.picker_settings_no_provider);

        return new CloudMediaProviderOption(NONE_PREF_KEY, nonePrefLabel, nonePrefIcon);
    }

    @Nullable
    private String fetchCurrentProviderAuthority() {
        try (ContentProviderClient client = getContentProviderClient()) {
            if (client == null) {
                // TODO(b/266927613): Handle the edge case where work profile is turned off while
                // user is on the settings page but work tab's data is not fetched yet.
                throw new IllegalArgumentException("Could not get selected cloud provider because "
                        + "Media Provider client is null.");
            }
            final Bundle result = client.call(GET_CLOUD_PROVIDER_CALL,
                    /* arg */ null, /* extras */ null);
            return result.getString(GET_CLOUD_PROVIDER_RESULT, NONE_PREF_KEY);
        } catch (Exception e) {
            // Since displaying the current cloud provider is the core function of the Settings
            // page, if we're not able to fetch this info, there is no point in displaying this
            // activity.
            throw new IllegalArgumentException("Could not get selected cloud provider", e);
        }
    }

    private boolean persistSelectedProvider(@Nullable String newCloudProvider) {
        try (ContentProviderClient client = getContentProviderClient()) {
            if (client == null) {
                // This could happen when work profile is turned off after opening the Settings
                // page. The work tab would still be visible but the MP process for work profile
                // will not be running.
                return false;
            }
            final Bundle input = new Bundle();
            input.putString(EXTRA_CLOUD_PROVIDER, newCloudProvider);
            client.call(SET_CLOUD_PROVIDER_CALL, /* arg */ null, /* extras */ input);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Could not persist selected cloud provider", e);
            return false;
        }
    }

    @Nullable
    private ContentProviderClient getContentProviderClient()
            throws PackageManager.NameNotFoundException {
        return mUserId.getContentResolver(mContext)
                .acquireUnstableContentProviderClient(AUTHORITY);
    }
}
