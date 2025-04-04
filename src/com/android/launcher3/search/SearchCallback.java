/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.search;

import java.util.ArrayList;
import java.util.List;

/**
 * An interface for receiving search results.
 *
 * @param <T> Search Result type
 */
public interface SearchCallback<T> {

    /**
     * Called when the search from primary source is complete.
     *
     * @param items list of search results
     */
    void onSearchResult(String query, ArrayList<T> items, List<String> suggestions);

    /**
     * Called when the search from secondary source is complete.
     *
     * @param items list of search results
     */
    void onAppendSearchResult(String query, ArrayList<T> items);

    /**
     * Called when the search results should be cleared.
     */
    void clearSearchResult();

    /**
     * Called when the user presses enter/search on their keyboard
     *
     * @return whether the event was handled
     */
    boolean onSubmitSearch(String query);
}

