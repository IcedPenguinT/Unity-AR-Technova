/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.CacheableOutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.file.RelativeFilePathResolver;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;

public class DefaultTaskCacheabilityResolver implements TaskCacheabilityResolver {
    private static final CachingDisabledReason CACHING_NOT_ENABLED = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching has not been enabled for the task");
    private static final CachingDisabledReason NO_OUTPUTS_DECLARED = new CachingDisabledReason(CachingDisabledReasonCategory.NO_OUTPUTS_DECLARED, "No outputs declared");

    private final RelativeFilePathResolver relativeFilePathResolver;

    public DefaultTaskCacheabilityResolver(RelativeFilePathResolver relativeFilePathResolver) {
        this.relativeFilePathResolver = relativeFilePathResolver;
    }

    @Override
    public Optional<CachingDisabledReason> shouldDisableCaching(
        boolean hasDeclaredOutputs,
        ImmutableSortedSet<OutputFilePropertySpec> outputFileProperties,
        TaskInternal task,
        Collection<SelfDescribingSpec<TaskInternal>> cacheIfSpecs,
        Collection<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs,
        @Nullable OverlappingOutputs overlappingOutputs
    ) {
        if (cacheIfSpecs.isEmpty()) {
            return Optional.of(CACHING_NOT_ENABLED);
        }

        if (!hasDeclaredOutputs) {
            return Optional.of(NO_OUTPUTS_DECLARED);
        }

        if (overlappingOutputs != null) {
            String relativePath = relativeFilePathResolver.resolveForDisplay(overlappingOutputs.getOverlappedFilePath());
            return Optional.of(new CachingDisabledReason(CachingDisabledReasonCategory.OVERLAPPING_OUTPUTS,
                "Gradle does not know how file '" + relativePath + "' was created (output property '" + overlappingOutputs.getPropertyName() + "'). Task output caching requires exclusive access to output paths to guarantee correctness (i.e. multiple tasks are not allowed to produce output in the same location)."));
        }

        for (OutputFilePropertySpec spec : outputFileProperties) {
            if (!(spec instanceof CacheableOutputFilePropertySpec)) {
                return Optional.of(new CachingDisabledReason(
                    CachingDisabledReasonCategory.NON_CACHEABLE_OUTPUT,
                    "Output property '" + spec.getPropertyName() + "' contains a file tree"
                ));
            }
        }

        for (SelfDescribingSpec<TaskInternal> cacheIfSpec : cacheIfSpecs) {
            if (!cacheIfSpec.isSatisfiedBy(task)) {
                return Optional.of(new CachingDisabledReason(
                    CachingDisabledReasonCategory.ENABLE_CONDITION_NOT_SATISFIED,
                    "'" + cacheIfSpec.getDisplayName() + "' not satisfied"
                ));
            }
        }

        for (SelfDescribingSpec<TaskInternal> doNotCacheIfSpec : doNotCacheIfSpecs) {
            if (doNotCacheIfSpec.isSatisfiedBy(task)) {
                return Optional.of(new CachingDisabledReason(
                    CachingDisabledReasonCategory.DISABLE_CONDITION_SATISFIED,
                    "'" + doNotCacheIfSpec.getDisplayName() + "' satisfied"
                ));
            }
        }

        return Optional.empty();
    }
}
