/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.photopicker.features.selectionbar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.android.photopicker.R
import com.android.photopicker.core.ActivityModule
import com.android.photopicker.core.Background
import com.android.photopicker.core.ConcurrencyModule
import com.android.photopicker.core.Main
import com.android.photopicker.core.configuration.PhotopickerConfiguration
import com.android.photopicker.core.configuration.provideTestConfigurationFlow
import com.android.photopicker.core.events.Event
import com.android.photopicker.core.events.Events
import com.android.photopicker.core.events.LocalEvents
import com.android.photopicker.core.features.FeatureToken
import com.android.photopicker.core.features.FeatureManager
import com.android.photopicker.core.features.LocalFeatureManager
import com.android.photopicker.core.selection.LocalSelection
import com.android.photopicker.core.selection.Selection
import com.android.photopicker.data.model.Media
import com.android.photopicker.features.PhotopickerFeatureBaseTest
import com.android.photopicker.features.simpleuifeature.SimpleUiFeature
import com.android.photopicker.inject.PhotopickerTestModule
import com.android.photopicker.tests.HiltTestActivity
import com.google.common.truth.Truth.assertWithMessage
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@UninstallModules(
    ActivityModule::class,
    ConcurrencyModule::class,
)
@HiltAndroidTest
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SelectionBarFeatureTest : PhotopickerFeatureBaseTest() {

    /* Hilt's rule needs to come first to ensure the DI container is setup for the test. */
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule(activityClass = HiltTestActivity::class.java)

    /* Setup dependencies for the UninstallModules for the test class. */
    @Module @InstallIn(SingletonComponent::class) class TestModule : PhotopickerTestModule()

    val testDispatcher = StandardTestDispatcher()

    /* Overrides for ActivityModule */
    @BindValue @Main val mainScope: TestScope = TestScope(testDispatcher)
    @BindValue @Background var testBackgroundScope: CoroutineScope = mainScope.backgroundScope

    /* Overrides for the ConcurrencyModule */
    @BindValue @Main val mainDispatcher: CoroutineDispatcher = testDispatcher
    @BindValue @Background val backgroundDispatcher: CoroutineDispatcher = testDispatcher

    @BindValue val context: Context = getTestableContext()

    @Inject lateinit var selection: Selection<Media>
    @Inject lateinit var featureManager: FeatureManager
    @Inject lateinit var events: Events

    val TEST_TAG_SELECTION_BAR = "selection_bar"
    val MEDIA_ITEM =
        Media.Image(
            mediaId = "1",
            pickerId = 1L,
            authority = "a",
            uri =
                Uri.EMPTY.buildUpon()
                    .apply {
                        scheme("content")
                        authority("a")
                        path("$1")
                    }
                    .build(),
            dateTakenMillisLong = 123456789L,
            sizeInBytes = 1000L,
            mimeType = "image/png",
            standardMimeTypeExtension = 1,
        )

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testSelectionBarIsAlwaysEnabled() {

        val configOne = PhotopickerConfiguration(action = "TEST_ACTION")
        assertWithMessage("SelectionBarFeature is not always enabled for TEST_ACTION")
            .that(SelectionBarFeature.Registration.isEnabled(configOne))
            .isEqualTo(true)

        val configTwo = PhotopickerConfiguration(action = MediaStore.ACTION_PICK_IMAGES)
        assertWithMessage("SelectionBarFeature is not always enabled")
            .that(SelectionBarFeature.Registration.isEnabled(configTwo))
            .isEqualTo(true)

        val configThree = PhotopickerConfiguration(action = Intent.ACTION_GET_CONTENT)
        assertWithMessage("SelectionBarFeature is not always enabled")
            .that(SelectionBarFeature.Registration.isEnabled(configThree))
            .isEqualTo(true)
    }

    @Test
    fun testSelectionBarFeatureRegistersEvents() {

        val feature = SelectionBarFeature()

        assertWithMessage("Unexpected events in Registration.")
            .that(feature.eventsProduced)
            .isEqualTo(setOf(Event.MediaSelectionConfirmed::class.java))
    }

    @Test
    fun testSelectionBarIsShown() {

        mainScope.runTest {
            composeTestRule.setContent {
                CompositionLocalProvider(
                    LocalFeatureManager provides featureManager,
                    LocalSelection provides selection,
                    LocalEvents provides events
                ) {
                    SelectionBar(modifier = Modifier.testTag(TEST_TAG_SELECTION_BAR))
                }
            }
            composeTestRule.onNode(hasTestTag(TEST_TAG_SELECTION_BAR)).assertDoesNotExist()
            selection.add(MEDIA_ITEM)
            advanceTimeBy(100)
            composeTestRule.waitForIdle()

            composeTestRule
                .onNode(hasTestTag(TEST_TAG_SELECTION_BAR))
                .assertExists()
                .assertIsDisplayed()
        }
    }

    @Test
    fun testSelectionBarShowsSecondaryAction() {

        val testFeatureRegistrations =
            setOf(
                SelectionBarFeature.Registration,
                SimpleUiFeature.Registration,
            )

        mainScope.runTest {
            featureManager =
                FeatureManager(
                    provideTestConfigurationFlow(scope = this.backgroundScope),
                    this.backgroundScope,
                    testFeatureRegistrations,
                )

            composeTestRule.setContent {
                CompositionLocalProvider(
                    LocalFeatureManager provides featureManager,
                    LocalSelection provides selection,
                    LocalEvents provides events
                ) {
                    SelectionBar(modifier = Modifier.testTag(TEST_TAG_SELECTION_BAR))
                }
            }

            composeTestRule.onNode(hasText(SimpleUiFeature.BUTTON_LABEL)).assertDoesNotExist()
            selection.add(MEDIA_ITEM)
            advanceTimeBy(100)
            composeTestRule.waitForIdle()

            composeTestRule
                .onNode(hasText(SimpleUiFeature.BUTTON_LABEL))
                .assertExists()
                .assertIsDisplayed()
        }
    }

    @Test
    fun testSelectionBarPrimaryAction() {

        mainScope.runTest {
            val eventsSent = mutableListOf<Event>()
            backgroundScope.launch { events.flow.toList(eventsSent) }

            composeTestRule.setContent {
                CompositionLocalProvider(
                    LocalFeatureManager provides featureManager,
                    LocalSelection provides selection,
                    LocalEvents provides events
                ) {
                    SelectionBar(modifier = Modifier.testTag(TEST_TAG_SELECTION_BAR))
                }
            }

            // Populate selection with an item, and wait for animations to complete.
            selection.add(MEDIA_ITEM)
            advanceTimeBy(100)
            composeTestRule.waitForIdle()

            val resources = getTestableContext().getResources()
            val buttonLabel =
                resources.getString(
                    R.string.photopicker_add_button_label,
                    selection.snapshot().size
                )

            // Find the button, ensure it has a registered click handler, is displayed.
            composeTestRule
                .onNode(hasText(buttonLabel))
                .assertIsDisplayed()
                .assert(hasClickAction())
                .performClick()

            advanceTimeBy(100)
            assertWithMessage("Expected event was not dispatched")
                .that(eventsSent)
                .contains(Event.MediaSelectionConfirmed(FeatureToken.SELECTION_BAR.token))
        }
    }
}