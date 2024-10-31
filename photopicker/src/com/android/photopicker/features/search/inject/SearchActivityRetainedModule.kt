/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.photopicker.features.search.inject

import android.util.Log
import com.android.photopicker.data.DataService
import com.android.photopicker.features.search.data.FakeSearchDataServiceImpl
import com.android.photopicker.features.search.data.SearchDataService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

/**
 * Injection Module for search feature specific dependencies, that provides access to objects bound
 * to the ActivityRetainedScope.
 *
 * These can be injected by requesting the type with the [@ActivityRetainedScoped] qualifier.
 *
 * The module outlives the individual activities (and survives configuration changes), but is bound
 * to a single Photopicker session.
 *
 * Note: Jobs that are launched in the [CoroutineScope] provided by this module will be
 * automatically cancelled when the ActivityRetainedScope is ended.
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
class SearchActivityRetainedModule {
    companion object {
        val TAG: String = "SearchActivityModule"
    }

    // Avoid initialization until it's actually needed.
    private lateinit var searchDataService: SearchDataService

    /** Provider for an implementation of [SearchDataService]. */
    @Provides
    @ActivityRetainedScoped
    fun provideSearchDataService(dataService: DataService): SearchDataService {
        if (::searchDataService.isInitialized) {
            return searchDataService
        } else {
            Log.d(
                SearchDataService.TAG,
                "SearchDataService requested but not yet initialized." +
                    " Initializing SearchDataService.",
            )

            // TODO(b/361043596): Switch with actual implementation when ready.
            searchDataService = FakeSearchDataServiceImpl(dataService)
            return searchDataService
        }
    }
}
