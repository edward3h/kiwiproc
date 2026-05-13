/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.processor;

import static com.google.common.truth.Truth.assertThat;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import org.junit.jupiter.api.Test;

public class KiwiProcessorTest {
    @Test
    void supportedSourceVersionIsNotHardCoded() {
        // @SupportedSourceVersion(RELEASE_17) causes a warning on JVMs newer than 17;
        // the fix is to remove the annotation and override getSupportedSourceVersion().
        assertThat(KiwiProcessor.class.getAnnotation(SupportedSourceVersion.class))
                .isNull();
    }

    @Test
    void supportedSourceVersionIsLatest() {
        assertThat(new KiwiProcessor().getSupportedSourceVersion()).isEqualTo(SourceVersion.latestSupported());
    }
}
