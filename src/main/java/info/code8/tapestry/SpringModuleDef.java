package info.code8.tapestry;

import org.apache.tapestry5.internal.AbstractContributionDef;
import org.apache.tapestry5.ioc.*;
import org.apache.tapestry5.ioc.def.ContributionDef;
import org.apache.tapestry5.ioc.def.DecoratorDef;
import org.apache.tapestry5.ioc.def.ModuleDef;
import org.apache.tapestry5.ioc.def.ServiceDef;
import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.apache.tapestry5.plastic.PlasticUtils;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A wrapper that converts a Spring {@link ApplicationContext} into a set of service definitions,
 * compatible with
 * Tapestry 5 IoC, for the beans defined in the context, as well as the context itself.
 */
public class SpringModuleDef implements ModuleDef {
    private final Map<String, ServiceDef> services = CollectionFactory.newMap();
    private final ApplicationContext applicationContext;

    public SpringModuleDef(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        addServiceDefsForSpringBeans(applicationContext);
    }

    private void addServiceDefsForSpringBeans(ApplicationContext context) {
        for (final String beanName : context.getBeanDefinitionNames()) {
            String trueName = beanName.startsWith("&") ? beanName.substring(1) : beanName;

            services.put(trueName, new SpringBeanServiceDef(trueName, context));
        }
    }


    @Override
    public Class getBuilderClass() {
        return null;
    }

    /**
     * Returns a contribution, "SpringBean", to the MasterObjectProvider service. It is ordered
     * after the built-in
     * contributions.
     */
    @Override
    public Set<ContributionDef> getContributionDefs() {
        ContributionDef def = createContributionToMasterObjectProvider();

        return CollectionFactory.newSet(def);
    }

    private ContributionDef createContributionToMasterObjectProvider() {

        return new AbstractContributionDef() {
            @Override
            public String getServiceId() {
                return "MasterObjectProvider";
            }

            @Override
            @SuppressWarnings("unchecked")
            public void contribute(ModuleBuilderSource moduleSource, ServiceResources resources, OrderedConfiguration configuration) {
                final OperationTracker tracker = resources.getTracker();

                final ObjectProvider springBeanProvider = new ObjectProvider() {
                    @Override
                    public <T> T provide(Class<T> objectType, AnnotationProvider annotationProvider, ObjectLocator locator) {

                        Map beanMap = applicationContext.getBeansOfType(objectType);

                        switch (beanMap.size()) {
                            case 0:
                                return null;

                            case 1:

                                Object bean = beanMap.values().iterator().next();

                                return objectType.cast(bean);

                            default:

                                String message = String
                                        .format(
                                                "Spring context contains %d beans assignable to type %s: %s.",
                                                beanMap.size(), PlasticUtils.toTypeName(objectType), InternalUtils.joinSorted(beanMap.keySet()));

                                throw new IllegalArgumentException(message);
                        }
                    }
                };

                final ObjectProvider springBeanProviderInvoker = new ObjectProvider() {
                    @Override
                    public <T> T provide(final Class<T> objectType, final AnnotationProvider annotationProvider, final ObjectLocator locator) {
                        return tracker.invoke(
                                "Resolving dependency by searching Spring ApplicationContext",
                                () -> springBeanProvider.provide(objectType, annotationProvider, locator));
                    }
                };

                ObjectProvider outerCheck = springBeanProviderInvoker::provide;
                configuration.add("SpringBean", outerCheck, "after:AnnotationBasedContributions", "after:ServiceOverride");
            }
        };
    }

    @Override
    public Set<DecoratorDef> getDecoratorDefs() {
        return Collections.emptySet();
    }

    @Override
    public String getLoggerName() {
        return SpringModuleDef.class.getName();
    }

    @Override
    public ServiceDef getServiceDef(String serviceId) {
        return services.get(serviceId);
    }

    @Override
    public Set<String> getServiceIds() {
        return services.keySet();
    }
}

