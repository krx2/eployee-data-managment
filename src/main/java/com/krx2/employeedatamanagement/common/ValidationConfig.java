package com.krx2.employeedatamanagement.common;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Locale;

@Configuration
public class ValidationConfig {

    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        MessageInterpolator defaultInterpolator =
                Validation.byDefaultProvider().configure().getDefaultMessageInterpolator();
        factoryBean.setMessageInterpolator(new FixedLocaleMessageInterpolator(defaultInterpolator, Locale.ENGLISH));
        return factoryBean;
    }
}
