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
package com.android.providers.media.photopicker.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.android.providers.media.R;
import com.android.providers.media.photopicker.PhotoPickerActivity;
import com.android.providers.media.photopicker.data.Selection;
import com.android.providers.media.photopicker.data.UserIdManager;
import com.android.providers.media.photopicker.viewmodel.PickerViewModel;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * The base abstract Tab fragment
 */
public abstract class TabFragment extends Fragment {

    protected PickerViewModel mPickerViewModel;
    protected Selection mSelection;
    protected ImageLoader mImageLoader;
    protected AutoFitRecyclerView mRecyclerView;

    private ExtendedFloatingActionButton mProfileButton;
    private UserIdManager mUserIdManager;
    private boolean mHideProfileButton;
    private View mEmptyView;
    private TextView mEmptyTextView;

    private Button mAddButton;
    private View mBottomBar;
    private Animation mSlideUpAnimation;
    private Animation mSlideDownAnimation;

    @ColorInt
    private int mButtonIconAndTextColor;

    @ColorInt
    private int mButtonBackgroundColor;

    @ColorInt
    private int mButtonDisabledIconAndTextColor;

    @ColorInt
    private int mButtonDisabledBackgroundColor;

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.fragment_picker_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mImageLoader = new ImageLoader(getContext());
        mRecyclerView = view.findViewById(R.id.picker_tab_recyclerview);
        mRecyclerView.setHasFixedSize(true);
        mPickerViewModel = new ViewModelProvider(requireActivity()).get(PickerViewModel.class);
        mSelection = mPickerViewModel.getSelection();

        mEmptyView = view.findViewById(android.R.id.empty);
        mEmptyTextView = mEmptyView.findViewById(R.id.empty_text_view);

        final int[] attrsDisabled =
                new int[]{R.attr.pickerDisabledProfileButtonColor,
                        R.attr.pickerDisabledProfileButtonTextColor};
        final TypedArray taDisabled = getContext().obtainStyledAttributes(attrsDisabled);
        mButtonDisabledBackgroundColor = taDisabled.getColor(/* index */ 0, /* defValue */ -1);
        mButtonDisabledIconAndTextColor = taDisabled.getColor(/* index */ 1, /* defValue */ -1);
        taDisabled.recycle();

        final int[] attrs =
                new int[]{R.attr.pickerProfileButtonColor, R.attr.pickerProfileButtonTextColor};
        final TypedArray ta = getContext().obtainStyledAttributes(attrs);
        mButtonBackgroundColor = ta.getColor(/* index */ 0, /* defValue */ -1);
        mButtonIconAndTextColor = ta.getColor(/* index */ 1, /* defValue */ -1);
        ta.recycle();

        mProfileButton = getActivity().findViewById(R.id.profile_button);
        mUserIdManager = mPickerViewModel.getUserIdManager();

        final boolean canSelectMultiple = mSelection.canSelectMultiple();
        if (canSelectMultiple) {
            mAddButton = getActivity().findViewById(R.id.button_add);
            mAddButton.setOnClickListener(v -> {
                ((PhotoPickerActivity) getActivity()).setResultAndFinishSelf();
            });

            final Button viewSelectedButton = getActivity().findViewById(R.id.button_view_selected);
            // Transition to PreviewFragment on clicking "View Selected".
            viewSelectedButton.setOnClickListener(v -> {
                mSelection.prepareSelectedItemsForPreviewAll();
                PreviewFragment.show(getActivity().getSupportFragmentManager(),
                        PreviewFragment.getArgsForPreviewOnViewSelected());
            });

            // Get bottom bar, size, and load animations for bottom bar
            final int bottomBarSize = (int) getResources().getDimension(
                    R.dimen.picker_bottom_bar_size);
            mBottomBar = getActivity().findViewById(R.id.picker_bottom_bar);
            mSlideUpAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
            mSlideDownAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.slide_down);

            mSelection.getSelectedItemCount().observe(this, selectedItemListSize -> {
                updateVisibilityAndAnimateBottomBar(selectedItemListSize);

                final int bottomGap = selectedItemListSize == 0 ? 0 : getBottomGapForRecyclerView(
                        bottomBarSize);
                mRecyclerView.setPadding(0, 0, 0, bottomGap);

                updateProfileButtonVisibility();
            });
        }

        // Initial setup
        setUpProfileButtonWithListeners(mUserIdManager.isMultiUserProfiles());

        // Observe for cross profile access changes.
        final LiveData<Boolean> crossProfileAllowed = mUserIdManager.getCrossProfileAllowed();
        if (crossProfileAllowed != null) {
            crossProfileAllowed.observe(this, isCrossProfileAllowed -> {
                setUpProfileButton();
            });
        }

        // Observe for multi-user changes.
        final LiveData<Boolean> isMultiUserProfiles = mUserIdManager.getIsMultiUserProfiles();
        if (isMultiUserProfiles != null) {
            isMultiUserProfiles.observe(this, this::setUpProfileButtonWithListeners);
        }
    }

    private void updateVisibilityAndAnimateBottomBar(int selectedItemListSize) {
        if (!mSelection.canSelectMultiple()) {
            return;
        }

        if (selectedItemListSize == 0) {
            if (mBottomBar.getVisibility() == View.VISIBLE) {
                mBottomBar.setVisibility(View.GONE);
                mBottomBar.startAnimation(mSlideDownAnimation);
            }
        } else {
            if (mBottomBar.getVisibility() == View.GONE) {
                mBottomBar.setVisibility(View.VISIBLE);
                mBottomBar.startAnimation(mSlideUpAnimation);
            }
            mAddButton.setText(generateAddButtonString(getContext(), selectedItemListSize));
        }
    }

    private void setUpListenersForProfileButton() {
        mProfileButton.setOnClickListener(v -> onClickProfileButton());
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) {
                    mProfileButton.hide();
                } else {
                    updateProfileButtonVisibility();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRecyclerView != null) {
            mRecyclerView.clearOnScrollListeners();
        }
    }

    private void setUpProfileButtonWithListeners(boolean isMultiUserProfile) {
        if (isMultiUserProfile) {
            setUpListenersForProfileButton();
        } else {
            mRecyclerView.clearOnScrollListeners();
        }
        setUpProfileButton();
    }

    private void setUpProfileButton() {
        updateProfileButtonVisibility();
        if (!mUserIdManager.isMultiUserProfiles()) {
            return;
        }

        updateProfileButtonContent(mUserIdManager.isManagedUserSelected());
        updateProfileButtonColor(/* isDisabled */ !mUserIdManager.isCrossProfileAllowed());
    }

    private boolean shouldShowProfileButton() {
        return mUserIdManager.isMultiUserProfiles() && !mHideProfileButton &&
                (!mSelection.canSelectMultiple() ||
                        mSelection.getSelectedItemCount().getValue() == 0);
    }

    private void onClickProfileButton() {
        if (!mUserIdManager.isCrossProfileAllowed()) {
            ProfileDialogFragment.show(getActivity().getSupportFragmentManager());
        } else {
            changeProfile();
        }
    }

    private void changeProfile() {
        if (mUserIdManager.isManagedUserSelected()) {
            // TODO(b/190024747): Add caching for performance before switching data to and fro
            // work profile
            mUserIdManager.setPersonalAsCurrentUserProfile();

        } else {
            // TODO(b/190024747): Add caching for performance before switching data to and fro
            // work profile
            mUserIdManager.setManagedAsCurrentUserProfile();
        }

        updateProfileButtonContent(mUserIdManager.isManagedUserSelected());

        mPickerViewModel.updateItems();
        mPickerViewModel.updateCategories();
    }

    private void updateProfileButtonContent(boolean isManagedUserSelected) {
        final int iconResId;
        final int textResId;
        if (isManagedUserSelected) {
            iconResId = R.drawable.ic_personal_mode;
            textResId = R.string.picker_personal_profile;
        } else {
            iconResId = R.drawable.ic_work_outline;
            textResId = R.string.picker_work_profile;
        }
        mProfileButton.setIconResource(iconResId);
        mProfileButton.setText(textResId);
    }

    private void updateProfileButtonColor(boolean isDisabled) {
        final int textAndIconColor =
                isDisabled ? mButtonDisabledIconAndTextColor : mButtonIconAndTextColor;
        final int backgroundTintColor =
                isDisabled ? mButtonDisabledBackgroundColor : mButtonBackgroundColor;

        mProfileButton.setTextColor(ColorStateList.valueOf(textAndIconColor));
        mProfileButton.setIconTint(ColorStateList.valueOf(textAndIconColor));
        mProfileButton.setBackgroundTintList(ColorStateList.valueOf(backgroundTintColor));
    }

    protected int getBottomGapForRecyclerView(int bottomBarSize) {
        return bottomBarSize;
    }

    protected void hideProfileButton(boolean hide) {
        mHideProfileButton = hide;
        updateProfileButtonVisibility();
    }

    private void updateProfileButtonVisibility() {
        if (shouldShowProfileButton()) {
            mProfileButton.show();
        } else {
            mProfileButton.hide();
        }
    }

    protected void setEmptyMessage(int resId) {
        mEmptyTextView.setText(resId);
    }

    /**
     * If we show the {@link #mEmptyView}, hide the {@link #mRecyclerView}. If we don't hide the
     * {@link #mEmptyView}, show the {@link #mRecyclerView}
     */
    protected void updateVisibilityForEmptyView(boolean shouldShowEmptyView) {
        mEmptyView.setVisibility(shouldShowEmptyView ? View.VISIBLE : View.GONE);
        mRecyclerView.setVisibility(shouldShowEmptyView ? View.GONE : View.VISIBLE);
    }

    private static String generateAddButtonString(Context context, int size) {
        final String sizeString = NumberFormat.getInstance(Locale.getDefault()).format(size);
        final String template = context.getString(R.string.picker_add_button_multi_select);
        return TextUtils.expandTemplate(template, sizeString).toString();
    }
}
