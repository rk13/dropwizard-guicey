package ru.vyarus.dropwizard.guice.module.installer.feature.jersey.provider;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Injector;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.spi.internal.ValueFactoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vyarus.dropwizard.guice.module.installer.FeatureInstaller;
import ru.vyarus.dropwizard.guice.module.installer.install.JerseyInstaller;
import ru.vyarus.dropwizard.guice.module.installer.install.binding.BindingInstaller;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;
import ru.vyarus.dropwizard.guice.module.installer.util.FeatureUtils;
import ru.vyarus.java.generics.resolver.GenericsResolver;

import javax.inject.Singleton;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.ext.*;
import java.util.Set;

import static ru.vyarus.dropwizard.guice.module.installer.util.FeatureUtils.is;
import static ru.vyarus.dropwizard.guice.module.installer.util.JerseyBinding.*;

/**
 * Jersey provider installer.
 * Looks for classes annotated with {@code @javax.ws.rs.ext.Provider} and register bindings in HK context.
 * <p>If provider is annotated with {@code HK2Managed} it's instance will be created by HK2, not guice.
 * This is important when extensions directly depends on HK beans (no way to wrap with {@code Provider}
 * or if it's eager extension, which instantiated by HK immediately (when hk-guice contexts not linked yet).</p>
 * <p>In some cases {@code @LazyBinding} could be an alternative to {@code HK2Managed}</p>
 *
 * @author Vyacheslav Rusakov
 * @see ru.vyarus.dropwizard.guice.module.installer.feature.jersey.HK2Managed
 * @see ru.vyarus.dropwizard.guice.module.installer.install.binding.LazyBinding
 * @since 10.10.2014
 */
@Order(30)
public class JerseyProviderInstaller implements FeatureInstaller<Object>,
        BindingInstaller, JerseyInstaller<Object> {

    private static final Set<Class<?>> EXTENSION_TYPES = ImmutableSet.<Class<?>>of(
            ExceptionMapper.class,
            ParamConverterProvider.class,
            ContextResolver.class,
            MessageBodyReader.class,
            MessageBodyWriter.class,
            ReaderInterceptor.class,
            WriterInterceptor.class,
            ContainerRequestFilter.class,
            ContainerResponseFilter.class,
            DynamicFeature.class,
            ValueFactoryProvider.class,
            InjectionResolver.class,
            ApplicationEventListener.class
    );

    private final Logger logger = LoggerFactory.getLogger(JerseyProviderInstaller.class);
    private final ProviderReporter reporter = new ProviderReporter();

    @Override
    public boolean matches(final Class<?> type) {
        return FeatureUtils.hasAnnotation(type, Provider.class);
    }

    @Override
    public <T> void install(final Binder binder, final Class<? extends T> type, final boolean lazy) {
        final boolean isHkManaged = isHK2Managed(type);
        if (isHkManaged && lazy) {
            logger.warn("@LazyBinding is ignored, because @HK2Managed set: {}", type.getName());
        }
        if (!isHkManaged && !lazy) {
            // force singleton
            binder.bind(type).in(Singleton.class);
        }
        reporter.provider(type, isHkManaged, lazy);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void install(final AbstractBinder binder, final Injector injector, final Class<Object> type) {
        if (is(type, Factory.class)) {
            // register factory directly (without wrapping)
            bindFactory(binder, injector, type);

        } else {
            // support multiple extension interfaces on one type
            final Set<Class<?>> extensions = Sets.intersection(EXTENSION_TYPES,
                    GenericsResolver.resolve(type).getGenericsInfo().getComposingTypes());
            if (!extensions.isEmpty()) {
                for (Class<?> ext : extensions) {
                    bindSpecificComponent(binder, injector, type, ext);
                }
            } else {
                // no known extension found
                bindComponent(binder, injector, type);
            }
        }
    }

    @Override
    public void report() {
        reporter.report();
    }
}
