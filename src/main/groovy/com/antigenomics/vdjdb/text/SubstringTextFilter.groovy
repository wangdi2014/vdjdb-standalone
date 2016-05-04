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
 * An entry filtering rule based on text substring matching. Only rows that have entry that match the filter are retained.
 */
class SubstringTextFilter extends TextFilter {
    /**
     * Creates a new entry filtering rule
     * @param name column identifier
     * @param value value to be matched as a substring
     * @param negative invert filter
     */
    SubstringTextFilter(String columnId, String value, boolean negative) {
        super(columnId, value.toLowerCase(), negative)
    }

    @Override
    protected boolean passInner(Entry entry) {
        entry.value.toLowerCase().contains(value)
    }
}
