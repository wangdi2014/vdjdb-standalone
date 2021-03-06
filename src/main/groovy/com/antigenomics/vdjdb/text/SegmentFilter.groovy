/*
 * Copyright 2015 Mikhail Shugay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.antigenomics.vdjdb.text

import com.antigenomics.vdjdb.db.Entry


/**
 * Internal
 */
class SegmentFilter extends TextFilter {
    private final Collection<String> segments

    SegmentFilter(String columnId, String value) {
        super(columnId, value, false)
        segments = getSegmentSet(value)
    }

    @Override
    protected boolean passInner(Entry entry) {
        if (checkAutoPass(segments))
            return true

        def entrySegments = getSegmentSet(entry.value)

        if (checkAutoPass(entrySegments))
            return true

        entrySegments.any { segments.contains(it) } || segments.any { entrySegments.contains(it) }
    }

    private static boolean checkAutoPass(Collection<String> set) {
        set.empty || set.contains(".")
    }

    private static Collection<String> getSegmentSet(String value) {
        value.toUpperCase().split(",").collect { it.split("\\*")[0] }
    }
}
