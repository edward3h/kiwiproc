/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.lang.annotation.*;

@RecordBuilder.Template(
        options =
                @RecordBuilder.Options(
                        builderMode = RecordBuilder.BuilderMode.STAGED_REQUIRED_ONLY,
                        //                        skipStagingForInitializedComponents = true,
                        nullablePattern = "Nullable",
                        addFunctionalMethodsToWith = true,
                        addSingleItemCollectionBuilders = true,
                        useImmutableCollections = true,
                        onceOnlyAssignment = true))
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Inherited
public @interface KiwiRecordBuilder {}
