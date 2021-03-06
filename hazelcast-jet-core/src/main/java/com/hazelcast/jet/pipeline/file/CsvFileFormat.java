/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.pipeline.file;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * {@link FileFormat} for CSV files. See {@link FileFormat#csv} for more
 * details.
 *
 * @param <T> type of items a source using this file format will emit
 * @since 4.4
 */
public class CsvFileFormat<T> implements FileFormat<T> {

    /**
     * Format ID for CSV.
     */
    public static final String FORMAT_CSV = "csv";

    private final Class<T> clazz;

    /**
     * Creates a {@code CsvFileFormat}. See {@link FileFormat#csv} for more
     * details.
     *
     * @param clazz type of the object to deserialize CSV lines into
     */
    CsvFileFormat(@Nonnull Class<T> clazz) {
        this.clazz = requireNonNull(clazz, "clazz must not be null");
    }

    /**
     * Returns the type of the object the data source using this format will
     * emit.
     */
    @Nonnull
    public Class<T> clazz() {
        return clazz;
    }

    @Nonnull @Override
    public String format() {
        return FORMAT_CSV;
    }
}
