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

package com.android.photopicker.core.configuration

import android.content.Intent
import android.os.SystemProperties
import com.android.photopicker.core.navigation.PhotopickerDestinations

/** Check system properties to determine if the device is considered debuggable */
private val buildIsDebuggable = SystemProperties.getInt("ro.debuggable", 0) == 1

/** The default selection maximum size if not set by the caller */
const val DEFAULT_SELECTION_LIMIT = 1

/** Enum that describes the current runtime environment of the Photopicker. */
enum class PhotopickerRuntimeEnv {
    ACTIVITY,
    EMBEDDED,
}

/**
 * Data object that represents a possible configuration state of the Photopicker.
 *
 * @property runtimeEnv The current Photopicker runtime environment, this should never be changed
 *   during configuration updates.
 * @property action the [Intent#getAction] that Photopicker is currently serving.
 * @property intent the [Intent] that Photopicker was launched with.
 * @property callingPackage the package name of the caller
 * @property callingPackageUid the uid of the caller
 * @property callingPackageLabel the display label of the caller that can be shown to the user
 * @property mimeTypes the mimetypes to filter all media requests with for the current session.
 * @property pickImagesInOrder whether to show check marks as ordered number values for selected
 *   media.
 * @property selectionLimit the value of [MediaStore.EXTRA_PICK_IMAGES_MAX] with a default value of
 *   [DEFAULT_SELECTION_LIMIT], and max value of [MediaStore.getPickImagesMaxLimit()] if it was not
 *   set or set to too large a limit.
 * @property startDestination the start destination that should be consider the "home" view the user
 *   is shown for the session.
 * @property flags a snapshot of the relevant flags in [DeviceConfig]. These are not live values.
 * @property deviceIsDebuggable if the device is running a build which has [ro.debuggable == 1]
 */
data class PhotopickerConfiguration(
    val runtimeEnv: PhotopickerRuntimeEnv = PhotopickerRuntimeEnv.ACTIVITY,
    val action: String,
    val callingPackage: String? = null,
    val callingPackageUid: Int? = null,
    val callingPackageLabel: String? = null,
    val intent: Intent? = null,
    val mimeTypes: ArrayList<String> = arrayListOf("image/*", "video/*"),
    val pickImagesInOrder: Boolean = false,
    val selectionLimit: Int = DEFAULT_SELECTION_LIMIT,
    val startDestination: PhotopickerDestinations = PhotopickerDestinations.DEFAULT,
    val deviceIsDebuggable: Boolean = buildIsDebuggable,
    val flags: PhotopickerFlags = PhotopickerFlags(),
)
