package com.krx2.employeedatamanagement.common;

import jakarta.validation.MessageInterpolator;

import java.util.Locale;

public class FixedLocaleMessageInterpolator implements MessageInterpolator {

    private final MessageInterpolator delegate;
    private final Locale locale;

    public FixedLocaleMessageInterpolator(MessageInterpolator delegate, Locale locale) {
        this.delegate = delegate;
        this.locale = locale;
    }

    @Override
    public String interpolate(String messageTemplate, Context context) {
        return delegate.interpolate(messageTemplate, context, locale);
    }

    @Override
    public String interpolate(String messageTemplate, Context context, Locale ignoredLocale) {
        return delegate.interpolate(messageTemplate, context, locale);
    }
}
