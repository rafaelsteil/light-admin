package org.lightadmin.core.config.bootstrap;

import org.lightadmin.core.config.bootstrap.parsing.configuration.DomainConfigurationSource;
import org.lightadmin.core.config.bootstrap.parsing.configuration.DomainConfigurationSourceFactory;
import org.lightadmin.core.config.bootstrap.parsing.validation.DomainConfigurationSourceValidator;
import org.lightadmin.core.config.bootstrap.parsing.validation.DomainConfigurationSourceValidatorFactory;
import org.lightadmin.core.config.bootstrap.scanning.AdministrationClassScanner;
import org.lightadmin.core.config.bootstrap.scanning.ClassScanner;
import org.lightadmin.core.config.domain.DomainTypeAdministrationConfigurationFactory;
import org.lightadmin.core.config.domain.GlobalAdministrationConfiguration;
import org.lightadmin.reporting.ProblemReporter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;

import java.util.Set;

import static com.google.common.collect.Sets.newLinkedHashSet;
import static org.lightadmin.core.util.LightAdminConfigurationUtils.LIGHT_ADMINISTRATION_BASE_PACKAGE;
import static org.lightadmin.reporting.ProblemReporterFactory.failFastReporter;
import static org.springframework.util.ClassUtils.isAssignableValue;
import static org.springframework.util.StringUtils.tokenizeToStringArray;

public class GlobalAdministrationConfigurationProcessor implements BeanPostProcessor {

    private static final String CONFIG_LOCATION_DELIMITERS = ",; \t\n";

    private final DomainTypeAdministrationConfigurationFactory domainTypeAdministrationConfigurationFactory;
    private final DomainConfigurationSourceFactory domainConfigurationSourceFactory;

    private final DomainConfigurationSourceValidatorFactory configurationSourceValidatorFactory;

    private final Environment environment;

    public GlobalAdministrationConfigurationProcessor(final DomainTypeAdministrationConfigurationFactory domainTypeAdministrationConfigurationFactory,
                                                      final DomainConfigurationSourceFactory domainConfigurationSourceFactory,
                                                      final DomainConfigurationSourceValidatorFactory configurationSourceValidatorFactory,
                                                      final Environment environment) {

        this.domainConfigurationSourceFactory = domainConfigurationSourceFactory;
        this.domainTypeAdministrationConfigurationFactory = domainTypeAdministrationConfigurationFactory;
        this.configurationSourceValidatorFactory = configurationSourceValidatorFactory;

        this.environment = environment;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        if (!isAssignableValue(GlobalAdministrationConfiguration.class, bean)) {
            return bean;
        }

        final GlobalAdministrationConfiguration globalAdministrationConfiguration = (GlobalAdministrationConfiguration) bean;

        final Set<Class> metadataClasses = scanPackageForAdministrationClasses();

        final DomainConfigurationSourceValidator validator = configurationSourceValidatorFactory.getValidator();

        final ProblemReporter problemReporter = failFastReporter();

        for (Class metadataClass : metadataClasses) {
            final DomainConfigurationSource configurationSource = domainConfigurationSourceFactory.createConfigurationSource(metadataClass);

            validator.validate(configurationSource, problemReporter);

            globalAdministrationConfiguration.registerDomainTypeConfiguration(
                    domainTypeAdministrationConfigurationFactory.createAdministrationConfiguration(configurationSource)
            );
        }

        return globalAdministrationConfiguration;
    }

    private Set<Class> scanPackageForAdministrationClasses() {
        final ClassScanner classScanner = new AdministrationClassScanner();

        final Set<Class> administrationConfigs = newLinkedHashSet();
        for (String configurationsBasePackage : configurationsBasePackages()) {
            administrationConfigs.addAll(classScanner.scan(configurationsBasePackage));
        }

        return administrationConfigs;
    }

    private String[] configurationsBasePackages() {
        final String basePackageLocations = environment.getProperty(LIGHT_ADMINISTRATION_BASE_PACKAGE);

        return tokenizeToStringArray(basePackageLocations, CONFIG_LOCATION_DELIMITERS);
    }

    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName) throws BeansException {
        return bean;
    }
}