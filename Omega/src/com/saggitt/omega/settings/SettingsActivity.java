/*
 *  Copyright (c) 2020 Omega Launcher
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.saggitt.omega.settings;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentManager.OnBackStackChangedListener;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceFragment.OnPreferenceDisplayDialogCallback;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceRecyclerViewAccessibilityDelegate;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.android.launcher3.SessionCommitReceiver;
import com.android.launcher3.Utilities;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.settings.NotificationDotsPreference;
import com.android.launcher3.settings.PreferenceHighlighter;
import com.jaredrummler.android.colorpicker.ColorPickerDialog;
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;
import com.saggitt.omega.FakeLauncherKt;
import com.saggitt.omega.gestures.ui.GesturePreference;
import com.saggitt.omega.gestures.ui.SelectGestureHandlerFragment;
import com.saggitt.omega.preferences.ColorPreferenceCompat;
import com.saggitt.omega.preferences.ControlledPreference;
import com.saggitt.omega.preferences.PreferenceController;
import com.saggitt.omega.preferences.ResumablePreference;
import com.saggitt.omega.preferences.SearchProviderPreference;
import com.saggitt.omega.preferences.SelectSearchProviderFragment;
import com.saggitt.omega.preferences.StyledIconPreference;
import com.saggitt.omega.preferences.SubPreference;
import com.saggitt.omega.settings.search.SettingsSearchActivity;
import com.saggitt.omega.theme.ThemeOverride;
import com.saggitt.omega.util.Config;
import com.saggitt.omega.util.ContextUtils;
import com.saggitt.omega.util.SettingsObserver;
import com.saggitt.omega.views.SpringRecyclerView;
import com.saggitt.omega.views.ThemedListPreferenceDialogFragment;

import net.gsantner.opoc.format.markdown.SimpleMarkdownParser;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import static androidx.recyclerview.widget.RecyclerView.Adapter;

public class SettingsActivity extends SettingsBaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, OnPreferenceDisplayDialogCallback,
        OnBackStackChangedListener, View.OnClickListener {

    public static final String NOTIFICATION_BADGING = "notification_badging";
    private static final String NOTIFICATION_DOTS_PREFERENCE_KEY = "pref_icon_badging";
    /**
     * Hidden field Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
     */
    private static final String NOTIFICATION_ENABLED_LISTENERS = "enabled_notification_listeners";

    //public final static String SHOW_PREDICTIONS_PREF = "pref_show_predictions";
    public final static String ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one";

    public static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;

    public final static String EXTRA_TITLE = "title";
    public final static String EXTRA_FRAGMENT = "fragment";
    public final static String EXTRA_FRAGMENT_ARGS = "fragmentArgs";
    private boolean isSubSettings;
    protected boolean forceSubSettings = false;
    private boolean hasPreview = false;
    public static String defaultHome = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        savedInstanceState = getRelaunchInstanceState(savedInstanceState);

        String fragmentName = getIntent().getStringExtra(EXTRA_FRAGMENT);
        int content = getIntent().getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0);
        isSubSettings = content != 0 || fragmentName != null || forceSubSettings;
        hasPreview = getIntent().getBooleanExtra(SubSettingsFragment.HAS_PREVIEW, false);

        boolean showSearch = shouldShowSearch();

        super.onCreate(savedInstanceState);

        getDecorLayout().setHideToolbar(showSearch);
        setContentView(showSearch ? R.layout.activity_settings_home : R.layout.activity_settings);

        if (savedInstanceState == null) {
            Fragment fragment = createLaunchFragment(getIntent());

            // Display the fragment as the main content.
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content, fragment)
                    .commit();
        }

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        updateUpButton();
        if (showSearch) {
            Toolbar toolbar = findViewById(R.id.search_action_bar);
            toolbar.setOnClickListener(this);
        }

        if (hasPreview) {
            overrideOpenAnim();
        }
        //Utilities.getDevicePrefs(this).edit().putBoolean(OnboardingProvider.PREF_HAS_OPENED_SETTINGS, true).apply();
        defaultHome = resolveDefaultHome();
    }

    private void updateUpButton() {
        updateUpButton(isSubSettings || getSupportFragmentManager().getBackStackEntryCount() != 0);
    }

    private void updateUpButton(boolean enabled) {
        if (getSupportActionBar() == null) {
            return;
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(enabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackStackChanged() {
        updateUpButton();
    }

    protected boolean shouldShowSearch() {
        return Utilities.getOmegaPrefs(getApplicationContext()).getSettingsSearch() && !isSubSettings;
    }

    @NotNull
    @Override
    protected ThemeOverride.ThemeSet getThemeSet() {
        if (hasPreview) {
            return new ThemeOverride.SettingsTransparent();
        } else {
            return super.getThemeSet();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.search_action_bar) {
            startActivity(new Intent(this, SettingsSearchActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shouldShowSearch()) {
            Toolbar toolbar = findViewById(R.id.search_action_bar);
            toolbar.getMenu().clear();

            toolbar.inflateMenu(R.menu.menu_settings);
            ActionMenuView menuView;
            int count = toolbar.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = toolbar.getChildAt(i);
                if (child instanceof ActionMenuView) {
                    menuView = (ActionMenuView) child;
                    break;
                }
            }
            if (!BuildConfig.APPLICATION_ID.equals(resolveDefaultHome())) {
                toolbar.inflateMenu(R.menu.menu_change_default_home);
            }
            toolbar.setOnMenuItemClickListener(menuItem -> {
                switch (menuItem.getItemId()) {
                    case R.id.action_change_default_home:
                        FakeLauncherKt.changeDefaultHome(this);
                        break;
                    case R.id.action_restart_launcher:
                        Utilities.killLauncher();
                        break;
                    case R.id.action_dev_options:
                        Intent intent = new Intent(this, SettingsActivity.class);
                        intent.putExtra(SettingsActivity.SubSettingsFragment.TITLE,
                                getString(R.string.developer_options_title));
                        intent.putExtra(SettingsActivity.SubSettingsFragment.CONTENT_RES_ID,
                                R.xml.omega_preferences_developer);
                        intent.putExtra(SettingsBaseActivity.EXTRA_FROM_SETTINGS, true);
                        startActivity(intent);
                        break;
                    default:
                        return false;
                }
                return true;
            });
        }
    }

    private String resolveDefaultHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager()
                .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info != null && info.activityInfo != null) {
            return info.activityInfo.packageName;
        } else {
            return null;
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference preference) {
        Fragment fragment;
        if (preference instanceof SubPreference) {
            ((SubPreference) preference).start(this);
            return true;
        } else {
            fragment = Fragment.instantiate(this, preference.getFragment(), preference.getExtras());
        }
        if (fragment instanceof DialogFragment) {
            ((DialogFragment) fragment).show(getSupportFragmentManager(), preference.getKey());
        } else {
            startFragment(this, preference.getFragment(), preference.getExtras(), preference.getTitle());
        }
        return true;
    }

    @Override
    public void finish() {
        super.finish();

        if (hasPreview) {
            overrideCloseAnim();
        }
    }

    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragment caller, Preference pref) {
        return false;
    }

    public static void startFragment(Context context, String fragment, int title) {
        startFragment(context, fragment, null, context.getString(title));
    }

    public static void startFragment(Context context, String fragment, @Nullable Bundle args, @Nullable CharSequence title) {
        context.startActivity(createFragmentIntent(context, fragment, args, title));
    }

    @NotNull
    private static Intent createFragmentIntent(Context context, String fragment, @Nullable Bundle args, @Nullable CharSequence title) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(EXTRA_FRAGMENT, fragment);
        intent.putExtra(EXTRA_FRAGMENT_ARGS, args);
        if (title != null) {
            intent.putExtra(EXTRA_TITLE, title);
        }
        return intent;
    }

    protected Fragment createLaunchFragment(Intent intent) {
        CharSequence title = intent.getCharSequenceExtra(EXTRA_TITLE);
        if (title != null) {
            setTitle(title);
        }
        String fragment = intent.getStringExtra(EXTRA_FRAGMENT);
        if (fragment != null) {
            return Fragment.instantiate(this, fragment, intent.getBundleExtra(EXTRA_FRAGMENT_ARGS));
        }
        int content = intent.getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0);
        return content != 0
                ? SubSettingsFragment.newInstance(getIntent())
                : new LauncherSettingsFragment();
    }

    public abstract static class BaseFragment extends PreferenceFragmentCompat {
        private static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";
        private HighlightablePreferenceGroupAdapter mAdapter;
        private boolean mPreferenceHighlighted = false;
        public String mHighLightKey;

        private RecyclerView.Adapter mCurrentRootAdapter;
        private boolean mIsDataSetObserverRegistered = false;
        private AdapterDataObserver mDataSetObserver =
                new AdapterDataObserver() {
                    @Override
                    public void onChanged() {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount) {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount,
                                                   Object payload) {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeInserted(int positionStart, int itemCount) {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeRemoved(int positionStart, int itemCount) {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                        onDataSetChanged();
                    }
                };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }
        }

        public void highlightPreferenceIfNeeded() {
            if (!isAdded()) {
                return;
            }
            if (mAdapter != null) {
                mAdapter.requestHighlight(requireView(), getListView());
            }
        }

        @SuppressLint("RestrictedApi")
        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
                                                 Bundle savedInstanceState) {
            RecyclerView recyclerView = (RecyclerView) inflater
                    .inflate(getRecyclerViewLayoutRes(), parent, false);
            if (recyclerView instanceof SpringRecyclerView) {
                ((SpringRecyclerView) recyclerView).setShouldTranslateSelf(false);
            }

            recyclerView.setLayoutManager(onCreateLayoutManager());
            recyclerView.setAccessibilityDelegateCompat(
                    new PreferenceRecyclerViewAccessibilityDelegate(recyclerView));

            return recyclerView;
        }

        abstract protected int getRecyclerViewLayoutRes();

        @Override
        public void setDivider(Drawable divider) {
            super.setDivider(null);
        }

        @Override
        public void setDividerHeight(int height) {
            super.setDividerHeight(0);
        }

        @Override
        protected Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
            final Bundle arguments = getActivity().getIntent().getExtras();
            mAdapter = new HighlightablePreferenceGroupAdapter(preferenceScreen,
                    arguments == null
                            ? null : arguments.getString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY),
                    mPreferenceHighlighted);
            return mAdapter;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);

            if (mAdapter != null) {
                outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mAdapter.isHighlightRequested());
            }
        }

        protected void onDataSetChanged() {
            highlightPreferenceIfNeeded();
        }

        public int getInitialExpandedChildCount() {
            return -1;
        }

        @Override
        public void onResume() {
            super.onResume();
            highlightPreferenceIfNeeded();
            if (isAdded() && !mPreferenceHighlighted) {
                PreferenceHighlighter highlighter = createHighlighter();
                if (highlighter != null) {
                    getView().postDelayed(highlighter, DELAY_HIGHLIGHT_DURATION_MILLIS);
                    mPreferenceHighlighted = true;
                }
            }
            dispatchOnResume(getPreferenceScreen());
        }

        private PreferenceHighlighter createHighlighter() {
            if (TextUtils.isEmpty(mHighLightKey)) {
                return null;
            }

            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return null;
            }

            RecyclerView list = getListView();
            PreferenceGroup.PreferencePositionCallback callback = (PreferenceGroup.PreferencePositionCallback) list.getAdapter();
            int position = Objects.requireNonNull(callback).getPreferenceAdapterPosition(mHighLightKey);
            return position >= 0 ? new PreferenceHighlighter(list, position) : null;
        }

        public void dispatchOnResume(PreferenceGroup group) {
            int count = group.getPreferenceCount();
            for (int i = 0; i < count; i++) {
                Preference preference = group.getPreference(i);
                if (preference instanceof ResumablePreference) {
                    ((ResumablePreference) preference).onResume();
                }
                if (preference instanceof PreferenceGroup) {
                    dispatchOnResume((PreferenceGroup) preference);
                }
            }
        }

        @Override
        protected void onBindPreferences() {
            registerObserverIfNeeded();
        }

        @Override
        protected void onUnbindPreferences() {
            unregisterObserverIfNeeded();
        }

        public void registerObserverIfNeeded() {
            if (!mIsDataSetObserverRegistered) {
                if (mCurrentRootAdapter != null) {
                    mCurrentRootAdapter.unregisterAdapterDataObserver(mDataSetObserver);
                }
                mCurrentRootAdapter = getListView().getAdapter();
                mCurrentRootAdapter.registerAdapterDataObserver(mDataSetObserver);
                mIsDataSetObserverRegistered = true;
                onDataSetChanged();
            }
        }

        public void unregisterObserverIfNeeded() {
            if (mIsDataSetObserverRegistered) {
                if (mCurrentRootAdapter != null) {
                    mCurrentRootAdapter.unregisterAdapterDataObserver(mDataSetObserver);
                    mCurrentRootAdapter = null;
                }
                mIsDataSetObserverRegistered = false;
            }
        }

        void onPreferencesAdded(PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference preference = group.getPreference(i);

                if (preference instanceof ControlledPreference) {
                    PreferenceController controller = ((ControlledPreference) preference)
                            .getController();
                    if (controller != null) {
                        if (!controller.onPreferenceAdded(preference)) {
                            i--;
                            continue;
                        }
                    }
                }

                if (preference instanceof PreferenceGroup) {
                    onPreferencesAdded((PreferenceGroup) preference);
                }
            }
        }
    }

    public static class LauncherSettingsFragment extends BaseFragment {
        private boolean mShowDevOptions;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mShowDevOptions = Utilities.getOmegaPrefs(getActivity()).getDeveloperOptionsEnabled();
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            setHasOptionsMenu(true);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.omega_preferences);
            onPreferencesAdded(getPreferenceScreen());
        }

        @Override
        public void onResume() {
            super.onResume();
            requireActivity().setTitle(R.string.settings_button_text);
            requireActivity().setTitleColor(R.color.colorAccent);
            boolean dev = Utilities.getOmegaPrefs(getActivity()).getDeveloperOptionsEnabled();
            if (dev != mShowDevOptions) {
                getActivity().recreate();
            }
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            return super.onPreferenceTreeClick(preference);
        }

        @Override
        public void onCreateOptionsMenu(@NotNull Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.menu_settings, menu);
            if (!BuildConfig.APPLICATION_ID.equals(defaultHome)) {
                inflater.inflate(R.menu.menu_change_default_home, menu);
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_change_default_home:
                    FakeLauncherKt.changeDefaultHome(getContext());
                    break;
                case R.id.action_restart_launcher:
                    Utilities.killLauncher();
                    break;
                case R.id.action_dev_options:
                    Intent intent = new Intent(getContext(), SettingsActivity.class);
                    intent.putExtra(SettingsActivity.SubSettingsFragment.TITLE,
                            getString(R.string.developer_options_title));
                    intent.putExtra(SettingsActivity.SubSettingsFragment.CONTENT_RES_ID,
                            R.xml.omega_preferences_developer);
                    intent.putExtra(SettingsBaseActivity.EXTRA_FROM_SETTINGS, true);
                    startActivity(intent);
                    break;
                default:
                    return false;
            }

            return true;
        }

        @Override
        protected int getRecyclerViewLayoutRes() {
            return Utilities.getOmegaPrefs(getContext()).getSettingsSearch() ? R.layout.preference_home_recyclerview
                    : R.layout.preference_dialog_recyclerview;
        }
    }

    public static class SubSettingsFragment extends BaseFragment implements
            Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
        public static final String TITLE = "title";
        public static final String CONTENT_RES_ID = "content_res_id";
        public static final String HAS_PREVIEW = "has_preview";

        private SystemDisplayRotationLockObserver mRotationLockObserver;
        private IconBadgingObserver mIconBadgingObserver;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Context mContext = getActivity();
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            int preference = getContent();
            ContentResolver resolver = mContext.getContentResolver();
            switch (preference) {
                case R.xml.omega_preferences_desktop:
                    if (!Utilities.ATLEAST_OREO) {
                        getPreferenceScreen().removePreference(
                                findPreference(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY));
                    }
                    // Setup allow rotation preference
                    Preference rotationPref = findPreference(Utilities.ALLOW_ROTATION_PREFERENCE_KEY);
                    if (getResources().getBoolean(R.bool.allow_rotation)) {
                        // Launcher supports rotation by default. No need to show this setting.
                        getPreferenceScreen().removePreference(rotationPref);
                    } else {
                        mRotationLockObserver = new SystemDisplayRotationLockObserver(rotationPref, resolver);

                        // Register a content observer to listen for system setting changes while
                        // this UI is active.
                        mRotationLockObserver.register(Settings.System.ACCELEROMETER_ROTATION);

                        // Initialize the UI once
                        rotationPref.setDefaultValue(Utilities.getAllowRotationDefaultValue(getActivity()));
                    }
                    break;
/*
                case R.xml.omega_preferences_drawer:
                    findPreference(SHOW_PREDICTIONS_PREF).setOnPreferenceChangeListener(this);
                    break;
*/
                case R.xml.omega_preferences_notification:
                    if (getResources().getBoolean(R.bool.notification_dots_enabled)) {
                        NotificationDotsPreference iconBadgingPref = (NotificationDotsPreference) findPreference(NOTIFICATION_DOTS_PREFERENCE_KEY);
                        // Listen to system notification badge settings while this UI is active.
                        mIconBadgingObserver = new IconBadgingObserver(
                                iconBadgingPref, getActivity().getContentResolver(), getFragmentManager());
                        mIconBadgingObserver.register(NOTIFICATION_BADGING, NOTIFICATION_ENABLED_LISTENERS);
                    }
                    break;

                case R.xml.omega_preferences_developer:
                    findPreference("kill").setOnPreferenceClickListener(this);
                    break;

                case R.xml.omega_preferences_about:
                    ContextUtils au = new ContextUtils(getContext());
                    Preference appInfo = findPreference("pref_key__copy_build_information");
                    au.getAppInfoSummary(appInfo);
                    updateProjectTeam(au);
                    break;
            }
        }

        private void updateProjectTeam(ContextUtils au) {
            Preference pref;
            if ((pref = findPreference("pref_key__project_team")) != null && ((PreferenceGroup) pref).getPreferenceCount() == 0) {
                String[] data = (au.readTextfileFromRawRes(R.raw.team, "", "").trim() + "\n\n").split("\n");

                for (int i = 0; i + 2 < data.length; i += 4) {
                    StyledIconPreference person = new StyledIconPreference(au.mContext);
                    person.setTitle(data[i]);
                    person.setSummary(data[i + 1]);
                    person.setIcon(R.drawable.ic_person);
                    try {
                        Uri uri = Uri.parse(data[i + 2]);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        person.setIntent(intent);
                    } catch (Exception ignored) {
                    }
                    appendPreference(person, (PreferenceGroup) pref);
                }

            }
        }

        private boolean appendPreference(Preference pref, @Nullable PreferenceGroup target) {
            if (target == null) {
                if ((target = getPreferenceScreen()) == null) {
                    return false;
                }
            }
            if (pref.getIcon() != null) {
                pref.setIcon(pref.getIcon());
            }
            return target.addPreference(pref);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(getContent());
            onPreferencesAdded(getPreferenceScreen());
        }

        private int getContent() {
            return getArguments().getInt(CONTENT_RES_ID);
        }

        @Override
        public void onResume() {
            super.onResume();
            setActivityTitle();
        }

        protected void setActivityTitle() {
            getActivity().setTitle(getArguments().getString(TITLE));
        }

        @Override
        public void onDestroy() {
            if (mRotationLockObserver != null) {
                mRotationLockObserver.unregister();
                mRotationLockObserver = null;
            }
            super.onDestroy();
        }

        public static SubSettingsFragment newInstance(String title, @XmlRes int content) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, title);
            b.putInt(CONTENT_RES_ID, content);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            switch (preference.getKey()) {
/*                case SHOW_PREDICTIONS_PREF:
                    if ((boolean) newValue) {
                        return true;
                    }
                    SuggestionConfirmationFragment confirmationFragment = new SuggestionConfirmationFragment();
                    confirmationFragment.setTargetFragment(this, 0);
                    confirmationFragment.show(getFragmentManager(), preference.getKey());
                    break;
*/
                case ENABLE_MINUS_ONE_PREF:
                    if (Config.hasPackageInstalled(getActivity(), Config.GOOGLE_QSB)) {
                        return true;
                    }
                    break;
            }
            return false;
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            ContextUtils au = new ContextUtils(getContext());
            if (preference.getKey() != null) {
                switch (preference.getKey()) {
                    case "pref_key__donate":
                        au.openWebBrowser(getString(R.string.app_donate_url));
                        break;

                    case "pref_key__changelog":
                        SimpleMarkdownParser smp = new SimpleMarkdownParser();
                        try {
                            String html = smp.parse(getResources().openRawResource(R.raw.changelog), "", SimpleMarkdownParser.FILTER_ANDROID_TEXTVIEW, SimpleMarkdownParser.FILTER_CHANGELOG).getHtml();
                            au.showDialogWithHtmlTextView(R.string.title__about_changelog, html);
                        } catch (Exception ignored) {

                        }
                        break;

                    case "pref_key__project_license":
                        try {
                            au.showDialogWithHtmlTextView(R.string.category__about_licenses, new SimpleMarkdownParser().parse(
                                    getResources().openRawResource(R.raw.license),
                                    "", SimpleMarkdownParser.FILTER_ANDROID_TEXTVIEW).getHtml());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case "pref_key__open_source":
                        try {
                            au.showDialogWithHtmlTextView(R.string.title__about_open_source, new SimpleMarkdownParser().parse(
                                    getResources().openRawResource(R.raw.opensource),
                                    "", SimpleMarkdownParser.FILTER_ANDROID_TEXTVIEW).getHtml());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case "pref_key__copy_build_information":
                        au.setClipboard(preference.getSummary());
                        break;
                    default:
                        if (preference instanceof ColorPreferenceCompat) {
                            ColorPickerDialog dialog = ((ColorPreferenceCompat) preference).getDialog();
                            dialog.setColorPickerDialogListener(new ColorPickerDialogListener() {
                                public void onColorSelected(int dialogId, int color) {
                                    ((ColorPreferenceCompat) preference).saveValue(color);
                                }

                                public void onDialogDismissed(int dialogId) {
                                }
                            });
                            dialog.show((getActivity()).getSupportFragmentManager(), "color-picker-dialog");
                        } else if (preference.getFragment() != null) {
                            Log.d("Settings", "Opening Fragment: " + preference.getFragment());
                            SettingsActivity.startFragment(getContext(), preference.getFragment(), null, preference.getTitle());
                        }
                }
            }

            return false;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference.getKey().equals("kill"))
                Utilities.killLauncher();
            return false;
        }

        public static SubSettingsFragment newInstance(SubPreference preference) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, (String) preference.getTitle());
            b.putInt(CONTENT_RES_ID, preference.getContent());
            fragment.setArguments(b);
            return fragment;
        }

        public static SubSettingsFragment newInstance(Intent intent) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, intent.getStringExtra(TITLE));
            b.putInt(CONTENT_RES_ID, intent.getIntExtra(CONTENT_RES_ID, 0));
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            final DialogFragment f;
            if (preference instanceof ListPreference) {
                Log.d("success", "onDisplayPreferenceDialog: yay");
                f = ThemedListPreferenceDialogFragment.Companion.newInstance(preference.getKey());
            } else if (preference instanceof GesturePreference) {
                f = SelectGestureHandlerFragment.Companion
                        .newInstance((GesturePreference) preference);
            } else if (preference instanceof PreferenceDialogPreference) {
                f = PreferenceScreenDialogFragment.Companion
                        .newInstance((PreferenceDialogPreference) preference);
            } else if (preference instanceof SearchProviderPreference) {
                f = SelectSearchProviderFragment.Companion
                        .newInstance((SearchProviderPreference) preference);
            } else {
                super.onDisplayPreferenceDialog(preference);
                return;
            }

            f.setTargetFragment(this, 0);
            f.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
        }

        protected int getRecyclerViewLayoutRes() {
            return R.layout.preference_insettable_recyclerview;
        }
    }

    public static class DialogSettingsFragment extends SubSettingsFragment {
        public static DialogSettingsFragment newInstance(String title, @XmlRes int content) {
            DialogSettingsFragment fragment = new DialogSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, title);
            b.putInt(CONTENT_RES_ID, content);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        protected void setActivityTitle() {
        }

        @Override
        protected int getRecyclerViewLayoutRes() {
            return R.layout.preference_dialog_recyclerview;
        }
    }

    public static class NotificationAccessConfirmation extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            String msg = context.getString(R.string.msg_missing_notification_access,
                    context.getString(R.string.derived_app_name));
            return new AlertDialog.Builder(context)
                    .setTitle(R.string.title_missing_notification_access)
                    .setMessage(msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.title_change_settings, this)
                    .create();
        }

        @Override
        public void onStart() {
            super.onStart();
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            ComponentName cn = new ComponentName(getActivity(), NotificationListener.class);
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(":settings:fragment_args_key", cn.flattenToString());
            getActivity().startActivity(intent);
        }
    }

    /**
     * Content observer which listens for system auto-rotate setting changes, and enables/disables
     * the launcher rotation setting accordingly.
     */
    private static class SystemDisplayRotationLockObserver extends SettingsObserver.System {

        private final Preference mRotationPref;

        public SystemDisplayRotationLockObserver(
                Preference rotationPref, ContentResolver resolver) {
            super(resolver);
            mRotationPref = rotationPref;
        }

        @Override
        public void onSettingChanged(boolean enabled) {
            mRotationPref.setEnabled(enabled);
            mRotationPref.setSummary(enabled
                    ? R.string.allow_rotation_desc : R.string.allow_rotation_blocked_desc);
        }
    }

/*
    public static class SuggestionConfirmationFragment extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            if (getTargetFragment() instanceof PreferenceFragmentCompat) {
                Preference preference = ((PreferenceFragmentCompat) getTargetFragment()).findPreference(SHOW_PREDICTIONS_PREF);
                if (preference instanceof TwoStatePreference) {
                    ((TwoStatePreference) preference).setChecked(false);
                }
            }
        }

        @NotNull
        public Dialog onCreateDialog(final Bundle bundle) {
            return new  AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.title_disable_suggestions_prompt)
                    .setMessage(R.string.msg_disable_suggestions_prompt)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.label_turn_off_suggestions, this).create();
        }

        @Override
        public void onStart() {
            super.onStart();
            OmegaUtilsKt.applyAccent(((AlertDialog) Objects.requireNonNull(getDialog())));
        }

        private static void openSetting(Context context) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            context.startActivity(intent);
        }

        private boolean isAccessGranted() {
            try {
                Context context = getContext();
                PackageManager pm = context.getPackageManager();
                ApplicationInfo applicationInfo = pm.getApplicationInfo(BuildConfig.APPLICATION_ID, 0);
                AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
                int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        applicationInfo.uid, applicationInfo.packageName);
                return mode == AppOpsManager.MODE_ALLOWED;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
    }
*/
    /**
     * Content observer which listens for system badging setting changes, and updates the launcher
     * badging setting subtext accordingly.
     */
    private static class IconBadgingObserver extends SettingsObserver.Secure implements Preference.OnPreferenceClickListener {

        private final NotificationDotsPreference mBadgingPref;
        private final ContentResolver mResolver;
        private final FragmentManager mFragmentManager;
        private boolean serviceEnabled = true;

        public IconBadgingObserver(NotificationDotsPreference badgingPref, ContentResolver resolver,
                                   FragmentManager fragmentManager) {
            super(resolver);
            mBadgingPref = badgingPref;
            mResolver = resolver;
            mFragmentManager = fragmentManager;
        }

        @Override
        public void onSettingChanged(boolean enabled) {
            int summary = enabled ? R.string.icon_badging_desc_on : R.string.icon_badging_desc_off;

            if (enabled) {
                // Check if the listener is enabled or not.
                String enabledListeners =
                        Settings.Secure.getString(mResolver, NOTIFICATION_ENABLED_LISTENERS);
                ComponentName myListener =
                        new ComponentName(mBadgingPref.getContext(), NotificationListener.class);
                serviceEnabled = enabledListeners != null &&
                        (enabledListeners.contains(myListener.flattenToString()) ||
                                enabledListeners.contains(myListener.flattenToShortString()));
                if (!serviceEnabled) {
                    summary = R.string.title_missing_notification_access;
                }
            }
            mBadgingPref.setWidgetFrameVisible(!serviceEnabled);
            mBadgingPref.setOnPreferenceClickListener(
                    serviceEnabled && Utilities.ATLEAST_OREO ? null : this);
            mBadgingPref.setSummary(summary);

        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (!Utilities.ATLEAST_OREO && serviceEnabled) {
                ComponentName cn = new ComponentName(preference.getContext(),
                        NotificationListener.class);
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(":settings:fragment_args_key", cn.flattenToString());
                preference.getContext().startActivity(intent);
            } else {
                new SettingsActivity.NotificationAccessConfirmation()
                        .show(mFragmentManager, "notification_access");
            }
            return true;
        }
    }
}
