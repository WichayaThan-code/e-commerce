package com.thaishopfun.translation;

public record TranslationRequestedEvent(TranslationEntityType entityType, String entityId) {}
