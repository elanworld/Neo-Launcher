/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.model.data;

import static android.text.TextUtils.isEmpty;
import static androidx.core.util.Preconditions.checkNotNull;
import static com.android.launcher3.logger.LauncherAtom.Attribute.EMPTY_LABEL;
import static com.android.launcher3.logger.LauncherAtom.Attribute.MANUAL_LABEL;
import static com.android.launcher3.logger.LauncherAtom.Attribute.SUGGESTED_LABEL;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.text.TextUtils;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.FolderNameInfos;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.Attribute;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ContentWriter;
import com.saggitt.omega.folder.FirstItemProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Represents a folder containing shortcuts or apps.
 */
public class FolderInfo extends ItemInfo {

    public static final int NO_FLAGS = 0x00000000;

    /**
     * The folder is locked in sorted mode
     */
    public static final int FLAG_ITEMS_SORTED = 0x00000001;

    /**
     * It is a work folder
     */
    public static final int FLAG_WORK_FOLDER = 0x00000002;

    /**
     * The multi-page animation has run for this folder
     */
    public static final int FLAG_MULTI_PAGE_ANIMATION = 0x00000004;

    public static final int FLAG_MANUAL_FOLDER_NAME = 0x00000008;

    public static final int FLAG_COVER_MODE = 0x00000016;

    /**
     * Different states of folder label.
     */
    public enum LabelState {
        // Folder's label is not yet assigned( i.e., title == null). Eligible for auto-labeling.
        UNLABELED(Attribute.UNLABELED),

        // Folder's label is empty(i.e., title == ""). Not eligible for auto-labeling.
        EMPTY(EMPTY_LABEL),

        // Folder's label is one of the non-empty suggested values.
        SUGGESTED(SUGGESTED_LABEL),

        // Folder's label is non-empty, manually entered by the user
        // and different from any of suggested values.
        MANUAL(MANUAL_LABEL);

        private final LauncherAtom.Attribute mLogAttribute;

        LabelState(Attribute logAttribute) {
            this.mLogAttribute = logAttribute;
        }
    }

    public static final String EXTRA_FOLDER_SUGGESTIONS = "suggest";

    public int options;

    public FolderNameInfos suggestedFolderNames;

    /**
     * The apps and shortcuts
     */
    public ArrayList<WorkspaceItemInfo> contents = new ArrayList<>();

    public String swipeUpAction;
    private ArrayList<FolderListener> mListeners = new ArrayList<>();
    public FirstItemProvider firstItemProvider = new FirstItemProvider(this);
    private Drawable cached;
    private String cachedIcon;

    public FolderInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
        user = Process.myUserHandle();
    }

    /**
     * Add an app or shortcut
     *
     * @param item
     */
    public void add(WorkspaceItemInfo item, boolean animate) {
        add(item, contents.size(), animate);
    }

    /**
     * Add an app or shortcut for a specified rank.
     */
    public void add(WorkspaceItemInfo item, int rank, boolean animate) {
        rank = Utilities.boundToRange(rank, 0, contents.size());
        contents.add(rank, item);
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onAdd(item, rank);
        }
        itemsChanged(animate);
    }

    /**
     * Remove an app or shortcut. Does not change the DB.
     *
     * @param item
     */
    public void remove(WorkspaceItemInfo item, boolean animate) {
        removeAll(Collections.singletonList(item), animate);
    }

    public void setTitle(CharSequence title) {
        this.title = title;
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onTitleChanged(title);
        }
    }

    /**
     * Remove all matching app or shortcut. Does not change the DB.
     */
    public void removeAll(List<WorkspaceItemInfo> items, boolean animate) {
        contents.removeAll(items);
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onRemove(items);
        }
        itemsChanged(animate);
    }

    @Override
    public void onAddToDatabase(ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites.TITLE, title)
                .put(LauncherSettings.Favorites.OPTIONS, options);
    }

    public void addListener(FolderListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(FolderListener listener) {
        mListeners.remove(listener);
    }

    public void itemsChanged(boolean animate) {
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onItemsChanged(animate);
        }
    }

    public interface FolderListener {
        void onAdd(WorkspaceItemInfo item, int rank);

        void onRemove(List<WorkspaceItemInfo> item);

        void onItemsChanged(boolean animate);

        void onTitleChanged(CharSequence title);

        default void onIconChanged() {
            // do nothing
        }
    }

    public boolean hasOption(int optionFlag) {
        return (options & optionFlag) != 0;
    }

    /**
     * @param option    flag to set or clear
     * @param isEnabled whether to set or clear the flag
     * @param writer    if not null, save changes to the db.
     */
    public void setOption(int option, boolean isEnabled, ModelWriter writer) {
        int oldOptions = options;
        if (isEnabled) {
            options |= option;
        } else {
            options &= ~option;
        }
        if (writer != null && oldOptions != options) {
            writer.updateItemInDatabase(this);
        }
    }

    public boolean isCoverMode() {
        return hasOption(FLAG_COVER_MODE);
    }

    public void setCoverMode(boolean enable, ModelWriter modelWriter) {
        setOption(FLAG_COVER_MODE, enable, modelWriter);
    }

    public WorkspaceItemInfo getCoverInfo() {
        return firstItemProvider.getFirstItem();
    }

    public void setSwipeUpAction(@NonNull Context context, @Nullable String action) {
        swipeUpAction = action;
        ModelWriter.modifyItemInDatabase(context, this, swipeUpAction, true);
    }

    public CharSequence getIconTitle(Folder folder) {
        if(!isCoverMode()){
            if (!TextUtils.equals(folder.getDefaultFolderName(), title)) {
                return title;
            }else {
                return folder.getDefaultFolderName();
            }
        }
        else{
            WorkspaceItemInfo info = getCoverInfo();
            if (info.customTitle != null) {
                return info.customTitle;
            }
            return info.title;
        }
    }

    public ComponentKey toComponentKey() {
        return new ComponentKey(new ComponentName("com.saggitt.omega.folder", String.valueOf(id)), Process.myUserHandle());
    }

    /**
     * DO NOT USE OUTSIDE CUSTOMINFOPROVIDER
     */
    public void onIconChanged() {
        for (FolderListener listener : mListeners) {
            listener.onIconChanged();
        }
    }

    @Override
    protected String dumpProperties() {
        return String.format("%s; labelState=%s", super.dumpProperties(), getLabelState());
    }

    @Override
    public LauncherAtom.ItemInfo buildProto(FolderInfo fInfo) {
        LauncherAtom.FolderIcon.Builder folderIcon = LauncherAtom.FolderIcon.newBuilder().setCardinality(contents.size());
        if (LabelState.SUGGESTED.equals(getLabelState())) {
            folderIcon.setLabelInfo(title.toString());
        }
        return getDefaultItemInfoBuilder()
                .setFolderIcon(folderIcon)
                .setRank(rank)
                .setAttribute(getLabelState().mLogAttribute)
                .setContainerInfo(getContainerInfo())
                .build();
    }

    @Override
    public void setTitle(@Nullable CharSequence title, ModelWriter modelWriter) {
        // Updating label from null to empty is considered as false touch.
        // Retaining null title(ie., UNLABELED state) allows auto-labeling when new items added.
        if (isEmpty(title) && this.title == null) {
            return;
        }

        // Updating title to same value does not change any states.
        if (title != null && title.equals(this.title)) {
            return;
        }

        this.title = title;
        LabelState newLabelState =
                title == null ? LabelState.UNLABELED
                        : title.length() == 0 ? LabelState.EMPTY :
                        getAcceptedSuggestionIndex().isPresent() ? LabelState.SUGGESTED
                                : LabelState.MANUAL;

        if (newLabelState.equals(LabelState.MANUAL)) {
            options |= FLAG_MANUAL_FOLDER_NAME;
        } else {
            options &= ~FLAG_MANUAL_FOLDER_NAME;
        }
        if (modelWriter != null) {
            modelWriter.updateItemInDatabase(this);
        }
    }

    /**
     * Returns current state of the current folder label.
     */
    public LabelState getLabelState() {
        return title == null ? LabelState.UNLABELED
                : title.length() == 0 ? LabelState.EMPTY :
                hasOption(FLAG_MANUAL_FOLDER_NAME) ? LabelState.MANUAL
                        : LabelState.SUGGESTED;
    }

    @Override
    public ItemInfo makeShallowCopy() {
        FolderInfo folderInfo = new FolderInfo();
        folderInfo.copyFrom(this);
        folderInfo.contents = this.contents;
        return folderInfo;
    }

    /**
     * Returns {@link LauncherAtom.FolderIcon} wrapped as {@link LauncherAtom.ItemInfo} for logging.
     */
    @Override
    public LauncherAtom.ItemInfo buildProto() {
        return buildProto(null);
    }

    /**
     * Returns index of the accepted suggestion.
     */
    public OptionalInt getAcceptedSuggestionIndex() {
        String newLabel = checkNotNull(title,
                "Expected valid folder label, but found null").toString();
        if (suggestedFolderNames == null || !suggestedFolderNames.hasSuggestions()) {
            return OptionalInt.empty();
        }
        CharSequence[] labels = suggestedFolderNames.getLabels();
        return IntStream.range(0, labels.length)
                .filter(index -> !isEmpty(labels[index])
                        && newLabel.equalsIgnoreCase(
                        labels[index].toString()))
                .sequential()
                .findFirst();
    }

    /**
     * Returns {@link FromState} based on current {@link #title}.
     */
    public LauncherAtom.FromState getFromLabelState() {
        switch (getLabelState()) {
            case EMPTY:
                return LauncherAtom.FromState.FROM_EMPTY;
            case MANUAL:
                return LauncherAtom.FromState.FROM_CUSTOM;
            case SUGGESTED:
                return LauncherAtom.FromState.FROM_SUGGESTED;
            case UNLABELED:
            default:
                return LauncherAtom.FromState.FROM_STATE_UNSPECIFIED;
        }
    }

    /**
     * Returns {@link ToState} based on current {@link #title}.
     */
    public LauncherAtom.ToState getToLabelState() {
        if (title == null) {
            return LauncherAtom.ToState.TO_STATE_UNSPECIFIED;
        }

        if (!FeatureFlags.FOLDER_NAME_SUGGEST.get()) {
            return title.length() > 0
                    ? LauncherAtom.ToState.TO_CUSTOM_WITH_SUGGESTIONS_DISABLED
                    : LauncherAtom.ToState.TO_EMPTY_WITH_SUGGESTIONS_DISABLED;
        }

        // TODO: if suggestedFolderNames is null then it infrastructure issue, not
        // ranking issue. We should log these appropriately.
        if (suggestedFolderNames == null || !suggestedFolderNames.hasSuggestions()) {
            return title.length() > 0
                    ? LauncherAtom.ToState.TO_CUSTOM_WITH_EMPTY_SUGGESTIONS
                    : LauncherAtom.ToState.TO_EMPTY_WITH_EMPTY_SUGGESTIONS;
        }

        boolean hasValidPrimary = suggestedFolderNames != null && suggestedFolderNames.hasPrimary();
        if (title.length() == 0) {
            return hasValidPrimary ? LauncherAtom.ToState.TO_EMPTY_WITH_VALID_PRIMARY
                    : LauncherAtom.ToState.TO_EMPTY_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY;
        }

        OptionalInt accepted_suggestion_index = getAcceptedSuggestionIndex();
        if (!accepted_suggestion_index.isPresent()) {
            return hasValidPrimary ? LauncherAtom.ToState.TO_CUSTOM_WITH_VALID_PRIMARY
                    : LauncherAtom.ToState.TO_CUSTOM_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY;
        }

        switch (accepted_suggestion_index.getAsInt()) {
            case 0:
                return LauncherAtom.ToState.TO_SUGGESTION0;
            case 1:
                return hasValidPrimary ? LauncherAtom.ToState.TO_SUGGESTION1_WITH_VALID_PRIMARY
                        : LauncherAtom.ToState.TO_SUGGESTION1_WITH_EMPTY_PRIMARY;
            case 2:
                return hasValidPrimary ? LauncherAtom.ToState.TO_SUGGESTION2_WITH_VALID_PRIMARY
                        : LauncherAtom.ToState.TO_SUGGESTION2_WITH_EMPTY_PRIMARY;
            case 3:
                return hasValidPrimary ? LauncherAtom.ToState.TO_SUGGESTION3_WITH_VALID_PRIMARY
                        : LauncherAtom.ToState.TO_SUGGESTION3_WITH_EMPTY_PRIMARY;
            default:
                // fall through
        }
        return LauncherAtom.ToState.TO_STATE_UNSPECIFIED;
    }

    public boolean useIconMode(Context context) {
        return isCoverMode();
    }

    /*private boolean hasCustomIcon(Context context) {
        Launcher launcher = OmegaLauncher.getLauncher(context);
        return getIconInternal(launcher) != null;
    }*/

    public boolean usingCustomIcon(Context context) {
        return !isCoverMode();
    }

    private Drawable getIconInternal(Launcher launcher) {
        /*CustomInfoProvider<FolderInfo> infoProvider = CustomInfoProvider.Companion.forItem(launcher, this);
        //CustomIconEntry entry = infoProvider == null ? null : infoProvider.getIcon(this);
        if (entry != null) {
            entry.getIcon();
            if (!entry.getIcon().equals(cachedIcon)) {
                IconPack pack = IconPackProvider.INSTANCE.get(launcher)
                        .getIconPackOrSystem(entry.getPackPackageName());
                if (pack != null) {
                    cached = pack.getIcon(entry, launcher.getDeviceProfile().inv.fillResIconDpi);
                    cachedIcon = entry.getIcon();
                }
            }
            if (cached != null) {
                return cached.mutate();
            }
        }*/
        return null;
    }

    public Drawable getIcon(Context context) {
        Launcher launcher = Launcher.getLauncher(context);
        Drawable icn = getIconInternal(launcher);
        if (icn != null) return icn;
        if (isCoverMode()) return getCoverInfo().newIcon(context);
        return getFolderIcon(launcher);
    }

    public Drawable getFolderIcon(Launcher launcher) {
        int iconSize = launcher.getDeviceProfile().iconSizePx;
        FrameLayout dummy = new FrameLayout(launcher, null);
        FolderIcon icon = FolderIcon.inflateIcon(R.layout.folder_icon, launcher, dummy, this);
        icon.isCustomIcon = false;
        icon.getFolderBackground().setStartOpacity(1f);
        Bitmap b = BitmapRenderer.createHardwareBitmap(iconSize, iconSize, out -> {
            out.translate(iconSize / 2f, 0);
            // TODO: make folder icons more visible in front of the bottom sheet
            // out.drawColor(Color.RED);
            icon.draw(out);
        });
        icon.unbind();
        return new BitmapDrawable(launcher.getResources(), b);
    }
}
