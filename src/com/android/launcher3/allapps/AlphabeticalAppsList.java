/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.saggitt.omega.util.Config.SORT_AZ;
import static com.saggitt.omega.util.Config.SORT_BY_COLOR;
import static com.saggitt.omega.util.Config.SORT_MOST_USED;
import static com.saggitt.omega.util.Config.SORT_ZA;

import android.content.Context;
import android.graphics.Color;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LabelComparator;
import com.saggitt.omega.OmegaLauncher;
import com.saggitt.omega.allapps.AppColorComparator;
import com.saggitt.omega.allapps.AppUsageComparator;
import com.saggitt.omega.data.AppTracker;
import com.saggitt.omega.data.AppTrackerRepository;
import com.saggitt.omega.groups.DrawerFolderInfo;
import com.saggitt.omega.preferences.OmegaPreferences;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * The alphabetically sorted list of applications.
 */
public class AlphabeticalAppsList implements AllAppsStore.OnUpdateListener {

    public static final String TAG = "AlphabeticalAppsList";

    private static final int FAST_SCROLL_FRACTION_DISTRIBUTE_BY_ROWS_FRACTION = 0;
    private static final int FAST_SCROLL_FRACTION_DISTRIBUTE_BY_NUM_SECTIONS = 1;

    private final int mFastScrollDistributionMode = FAST_SCROLL_FRACTION_DISTRIBUTE_BY_NUM_SECTIONS;
    private final WorkAdapterProvider mWorkAdapterProvider;

    private final OmegaPreferences prefs;

    private List<String> mSearchSuggestions;

    private final BaseDraggingActivity mLauncher;

    // The set of apps from the system
    private final List<AppInfo> mApps = new ArrayList<>();
    private final AllAppsStore mAllAppsStore;

    // The number of results in current adapter
    private int mAccessibilityResultsCount = 0;
    // The current set of adapter items
    private final ArrayList<AdapterItem> mAdapterItems = new ArrayList<>();
    // The set of sections that we allow fast-scrolling to (includes non-merged sections)
    private final List<FastScrollSectionInfo> mFastScrollerSections = new ArrayList<>();

    // The of ordered component names as a result of a search query
    private ArrayList<AdapterItem> mSearchResults;
    private AllAppsGridAdapter mAdapter;
    private final AppInfoComparator mAppNameComparator;
    private final AppColorComparator mAppColorComparator;
    private final int mNumAppsPerRow;
    private int mNumAppRowsInAdapter;
    private ItemInfoMatcher mItemFilter;

    public AlphabeticalAppsList(Context context, AllAppsStore appsStore,
                                WorkAdapterProvider adapterProvider) {
        mAllAppsStore = appsStore;
        mLauncher = BaseDraggingActivity.fromContext(context);
        mAppNameComparator = new AppInfoComparator(context);
        mAppColorComparator = new AppColorComparator(context);
        mWorkAdapterProvider = adapterProvider;
        mNumAppsPerRow = mLauncher.getDeviceProfile().inv.numColumns;
        mAllAppsStore.addUpdateListener(this);
        prefs = Utilities.getOmegaPrefs(context);
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    @Override
    public void onAppsUpdated() {
        // Clear the list of apps
        mApps.clear();

        for (AppInfo app : mAllAppsStore.getApps()) {
            if (mItemFilter == null || mItemFilter.matches(app, null) || hasFilter()) {
                mApps.add(app);
            }
        }

        // Sort the list of apps
        sortApps(prefs.getSortMode());

        // As a special case for some languages (currently only Simplified Chinese), we may need to
        // coalesce sections
        Locale curLocale = mLauncher.getResources().getConfiguration().locale;
        boolean localeRequiresSectionSorting = curLocale.equals(Locale.SIMPLIFIED_CHINESE);
        if (localeRequiresSectionSorting) {
            // Compute the section headers. We use a TreeMap with the section name comparator to
            // ensure that the sections are ordered when we iterate over it later
            TreeMap<String, ArrayList<AppInfo>> sectionMap = new TreeMap<>(new LabelComparator());
            for (AppInfo info : mApps) {
                // Add the section to the cache
                String sectionName = info.sectionName;

                // Add it to the mapping
                ArrayList<AppInfo> sectionApps = sectionMap.get(sectionName);
                if (sectionApps == null) {
                    sectionApps = new ArrayList<>();
                    sectionMap.put(sectionName, sectionApps);
                }
                sectionApps.add(info);
            }

            // Add each of the section apps to the list in order
            mApps.clear();
            for (Map.Entry<String, ArrayList<AppInfo>> entry : sectionMap.entrySet()) {
                mApps.addAll(entry.getValue());
            }
        }

        // Recompose the set of adapter items from the current set of apps
        updateAdapterItems();
    }

    public void updateItemFilter(ItemInfoMatcher itemFilter) {
        this.mItemFilter = itemFilter;
        onAppsUpdated();
    }

    /**
     * Sets the adapter to notify when this dataset changes.
     */
    public void setAdapter(AllAppsGridAdapter adapter) {
        mAdapter = adapter;
    }

    /**
     * Returns all the apps.
     */
    public List<AppInfo> getApps() {
        return mApps;
    }

    private void sortApps(int sortType) {
        switch (sortType) {
            case SORT_ZA:
                mApps.sort((p2, p1) -> Collator
                        .getInstance()
                        .compare(p1.title, p2.title));
                break;

            case SORT_MOST_USED:
                AppTrackerRepository repository = AppTrackerRepository.Companion.getINSTANCE().get(mLauncher);
                List<AppTracker> appsCounter = repository.getAppsCount();
                AppUsageComparator mostUsedComparator = new AppUsageComparator(appsCounter);
                mApps.sort(mostUsedComparator);
                break;

            case SORT_BY_COLOR:
                mApps.sort(mAppColorComparator);
                break;
            case SORT_AZ:
            default:
                mApps.sort(mAppNameComparator);
                break;
        }
    }

    /**
     * Returns fast scroller sections of all the current filtered applications.
     */
    public List<FastScrollSectionInfo> getFastScrollerSections() {
        return mFastScrollerSections;
    }

    /**
     * Returns the current filtered list of applications broken down into their sections.
     */
    public List<AdapterItem> getAdapterItems() {
        return mAdapterItems;
    }

    /**
     * Returns the child adapter item with IME launch focus.
     */
    public AdapterItem getFocusedChild() {
        if (mAdapterItems.size() == 0 || getFocusedChildIndex() == -1) {
            return null;
        }
        return mAdapterItems.get(getFocusedChildIndex());
    }

    /**
     * Returns the index of the child with IME launch focus.
     */
    public int getFocusedChildIndex() {
        for (AdapterItem item : mAdapterItems) {
            if (item.isCountedForAccessibility()) {
                return mAdapterItems.indexOf(item);
            }
        }
        return -1;
    }

    /**
     * Returns the number of rows of applications
     */
    public int getNumAppRows() {
        return mNumAppRowsInAdapter;
    }

    /**
     * Returns the number of applications in this list.
     */
    public int getNumFilteredApps() {
        return mAccessibilityResultsCount;
    }

    /**
     * Returns whether there are is a filter set.
     */
    public boolean hasFilter() {
        return (mSearchResults != null);
    }

    /**
     * Returns whether there are no filtered results.
     */
    public boolean hasNoFilteredResults() {
        return (mSearchResults != null)
                && mAccessibilityResultsCount == 0
                && (mSearchSuggestions != null)
                && mSearchSuggestions.isEmpty();
    }

    /**
     * Returns whether there are suggestions.
     */
    public boolean hasSuggestions() {
        return mSearchSuggestions != null && !mSearchSuggestions.isEmpty();
    }

    /**
     * Sets results list for search
     */
    public boolean setSearchResults(ArrayList<AdapterItem> results) {
        if (!Objects.equals(results, mSearchResults)) {
            mSearchResults = results;
            updateAdapterItems();
            return true;
        }
        return false;
    }

    public boolean setSearchSuggestions(List<String> suggestions) {
        if (mSearchSuggestions != suggestions) {
            mSearchSuggestions = suggestions;
            onAppsUpdated();
            return true;
        }
        return false;
    }

    public boolean appendSearchResults(ArrayList<AdapterItem> results) {
        if (mSearchResults != null && results != null && results.size() > 0) {
            updateSearchAdapterItems(results, mSearchResults.size());
            refreshRecyclerView();
            return true;
        }
        return false;
    }

    void updateSearchAdapterItems(ArrayList<AdapterItem> list, int offset) {
        for (int i = 0; i < list.size(); i++) {
            AdapterItem adapterItem = list.get(i);
            adapterItem.position = offset + i;
            mAdapterItems.add(adapterItem);

            if (adapterItem.isCountedForAccessibility()) {
                mAccessibilityResultsCount++;
            }
        }
    }

    private void refillAdapterItems() {
        String lastSectionName = null;
        FastScrollSectionInfo lastFastScrollerSectionInfo = null;
        int position = 0;
        int appIndex = 0;
        int folderIndex = 0;

        // Prepare to update the list of sections, filtered apps, etc.
        mAccessibilityResultsCount = 0;
        mFastScrollerSections.clear();
        mAdapterItems.clear();

        // Recreate the filtered and sectioned apps (for convenience for the grid layout) from the
        // ordered set of sections

        // Search suggestions should be all the way to the top
        if (hasFilter() && hasSuggestions()) {
            for (String suggestion : mSearchSuggestions) {
                mAdapterItems.add(AdapterItem.asSearchSuggestion(position++, suggestion));
            }
        }

        if (!hasFilter()) {
            mAccessibilityResultsCount = mApps.size();
            if (mWorkAdapterProvider != null) {
                position += mWorkAdapterProvider.addWorkItems(mAdapterItems);
                if (!mWorkAdapterProvider.shouldShowWorkApps()) {
                    return;
                }
            }

            for (DrawerFolderInfo info : getFolderInfos()) {
                String sectionName = "#";

                // Create a new section if the section names do not match
                if (!sectionName.equals(lastSectionName)) {
                    lastSectionName = sectionName;
                    lastFastScrollerSectionInfo = new FastScrollSectionInfo(sectionName, Color.WHITE);
                    mFastScrollerSections.add(lastFastScrollerSectionInfo);
                }

                info.setAppsStore(mAllAppsStore);
                // Create an folder item
                AdapterItem appItem = AdapterItem
                        .asFolder(position++, sectionName, info, folderIndex++);
                if (lastFastScrollerSectionInfo.fastScrollToItem == null) {
                    lastFastScrollerSectionInfo.fastScrollToItem = appItem;
                }
                mAdapterItems.add(appItem);
            }

            Set<ComponentKey> folderFilters = getFolderFilteredApps();
            for (AppInfo info : getFiltersAppInfos()) {

                if (!hasFilter() && folderFilters.contains(info.toComponentKey())) {
                    continue;
                }

                String sectionName = info.sectionName;

                // Create a new section if the section names do not match
                if (!sectionName.equals(lastSectionName)) {
                    lastSectionName = sectionName;
                    lastFastScrollerSectionInfo = new FastScrollSectionInfo(sectionName, Color.WHITE);
                    mFastScrollerSections.add(lastFastScrollerSectionInfo);
                }

                // Create an app item
                AdapterItem appItem = AdapterItem.asApp(position++, sectionName, info,
                        appIndex++);
                if (lastFastScrollerSectionInfo.fastScrollToItem == null) {
                    lastFastScrollerSectionInfo.fastScrollToItem = appItem;
                }

                mAdapterItems.add(appItem);
            }
        }

        if (hasFilter()) {
            updateSearchAdapterItems(mSearchResults, 0);
            if (!FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
                // Append the search market item
                if (hasNoFilteredResults()) {
                    mAdapterItems.add(AdapterItem.asEmptySearch(position++));
                } else {
                    mAdapterItems.add(AdapterItem.asAllAppsDivider(position++));
                }
                mAdapterItems.add(AdapterItem.asMarketSearch(position++));

            }
        }
        if (mNumAppsPerRow != 0) {
            // Update the number of rows in the adapter after we do all the merging (otherwise, we
            // would have to shift the values again)
            int numAppsInSection = 0;
            int numAppsInRow = 0;
            int rowIndex = -1;
            for (AdapterItem item : mAdapterItems) {
                item.rowIndex = 0;
                if (AllAppsGridAdapter.isDividerViewType(item.viewType)) {
                    numAppsInSection = 0;
                } else if (AllAppsGridAdapter.isIconViewType(item.viewType)) {
                    if (numAppsInSection % mNumAppsPerRow == 0) {
                        numAppsInRow = 0;
                        rowIndex++;
                    }
                    item.rowIndex = rowIndex;
                    item.rowAppIndex = numAppsInRow;
                    numAppsInSection++;
                    numAppsInRow++;
                }
            }
            mNumAppRowsInAdapter = rowIndex + 1;

            // Pre-calculate all the fast scroller fractions
            switch (mFastScrollDistributionMode) {
                case FAST_SCROLL_FRACTION_DISTRIBUTE_BY_ROWS_FRACTION:
                    float rowFraction = 1f / mNumAppRowsInAdapter;
                    for (FastScrollSectionInfo info : mFastScrollerSections) {
                        AdapterItem item = info.fastScrollToItem;
                        if (!AllAppsGridAdapter.isIconViewType(item.viewType)) {
                            info.touchFraction = 0f;
                            continue;
                        }

                        float subRowFraction = item.rowAppIndex * (rowFraction / mNumAppsPerRow);
                        info.touchFraction = item.rowIndex * rowFraction + subRowFraction;
                    }
                    break;
                case FAST_SCROLL_FRACTION_DISTRIBUTE_BY_NUM_SECTIONS:
                    float perSectionTouchFraction = 1f / mFastScrollerSections.size();
                    float cumulativeTouchFraction = 0f;
                    for (FastScrollSectionInfo info : mFastScrollerSections) {
                        AdapterItem item = info.fastScrollToItem;
                        if (!AllAppsGridAdapter.isIconViewType(item.viewType)) {
                            info.touchFraction = 0f;
                            continue;
                        }
                        info.touchFraction = cumulativeTouchFraction;
                        cumulativeTouchFraction += perSectionTouchFraction;
                    }
                    break;
            }
        }
    }

    private List<AppInfo> getFiltersAppInfos() {
        if (mSearchResults == null) {
            return mApps;
        }
        ArrayList<AppInfo> result = new ArrayList<>();
        for (AdapterItem app : mSearchResults) {
            AppInfo match = mAllAppsStore.getApp(app.appInfo.toComponentKey());
            if (match != null) {
                result.add(match);
            } else {
                //Add hidden apps to search results when the preference is enabled
                ArrayList<AppInfo> apps = OmegaLauncher.getLauncher(mLauncher.getApplicationContext()).getHiddenApps();
                for (AppInfo info : apps) {
                    if (info.componentName.getPackageName().equals(app.appInfo.componentName.getPackageName())) {
                        result.add(info);
                    }
                }
            }
        }

        return result;
    }

    private List<DrawerFolderInfo> getFolderInfos() {
        LauncherAppState app = LauncherAppState.getInstance(mLauncher);
        LauncherModel model = app.getModel();
        ModelWriter modelWriter = model.getWriter(false, true);
        return Utilities.getOmegaPrefs(mLauncher)
                .getAppGroupsManager()
                .getDrawerFolders()
                .getFolderInfos(this, modelWriter);
    }

    private Set<ComponentKey> getFolderFilteredApps() {

        return Utilities.getOmegaPrefs(mLauncher)
                .getAppGroupsManager()
                .getDrawerFolders()
                .getHiddenComponents();
    }

    public void reset() {
        updateAdapterItems();
    }

    /**
     * Updates the set of filtered apps with the current filter. At this point, we expect
     * mCachedSectionNames to have been calculated for the set of all apps in mApps.
     */
    public void updateAdapterItems() {
        refillAdapterItems();
        refreshRecyclerView();
    }

    private void refreshRecyclerView() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Info about a fast scroller section, depending if sections are merged, the fast scroller
     * sections will not be the same set as the section headers.
     */
    public static class FastScrollSectionInfo {
        // The section name
        public String sectionName;
        // The AdapterItem to scroll to for this section
        public AdapterItem fastScrollToItem;
        // The touch fraction that should map to this fast scroll section info
        public float touchFraction;
        // The color of this fast scroll section
        public int color;

        public FastScrollSectionInfo(String sectionName, int color) {
            this.sectionName = sectionName;
            this.color = color;
        }
    }
}
