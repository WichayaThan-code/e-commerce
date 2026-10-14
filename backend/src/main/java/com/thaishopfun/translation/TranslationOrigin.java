package com.thaishopfun.translation;

public enum TranslationOrigin {
    MACHINE,
    HUMAN,
    MANUAL;

    public boolean manual() {
        return this == HUMAN || this == MANUAL;
    }
}
