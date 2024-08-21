package org.ethelred.kiwiproc.processor.generator;

import javax.lang.model.element.Element;
import org.jspecify.annotations.Nullable;

public interface ElementSupplier {
    @Nullable Element getElement();
}
