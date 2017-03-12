package info.code8.tapestry;

import org.apache.tapestry5.SymbolConstants;
import org.apache.tapestry5.internal.InternalConstants;
import org.apache.tapestry5.internal.SingleKeySymbolProvider;
import org.apache.tapestry5.internal.TapestryAppInitializer;
import org.apache.tapestry5.internal.util.DelegatingSymbolProvider;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.internal.services.SystemPropertiesSymbolProvider;
import org.apache.tapestry5.ioc.services.ServiceActivityScoreboard;
import org.apache.tapestry5.ioc.services.SymbolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.context.ConfigurableWebApplicationContext;

import java.util.*;

/**
 * Created by code8 on 11/6/15.
 */

public class TapestryBeanFactoryPostProcessor implements BeanFactoryPostProcessor, ApplicationContextInitializer {
    private Logger logger = LoggerFactory.getLogger(getClass());

    protected ConfigurableWebApplicationContext applicationContext;
    protected Registry registry;

    protected Registry makeTapestryRegistry(SymbolProvider symbolProvider, String filterName) {
        String executionMode = symbolProvider.valueForSymbol(SymbolConstants.EXECUTION_MODE);
        TapestryAppInitializer appInitializer = new TapestryAppInitializer(logger, symbolProvider, filterName, executionMode);
        appInitializer.addModules(new SpringModuleDef(applicationContext));
        appInitializer.addModules(AssetSourceModule.class);
        Registry registry = appInitializer.createRegistry();
        registry.performRegistryStartup();
        appInitializer.announceStartup();
        registry.cleanupThread();
        return registry;
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        if (applicationContext instanceof EmbeddedWebApplicationContext) {
            this.applicationContext = (ConfigurableWebApplicationContext) applicationContext;
            applicationContext.addBeanFactoryPostProcessor(this);
        } else {
            throw new RuntimeException("tapestry-spring-boot works only with EmbeddedWebApplicationContext (Supplied context class was" + applicationContext.getClass() + ")");
        }
    }

    protected Collection<String> findPackagesToScan(ConfigurableApplicationContext applicationContext) {
        Set<String> packages = new HashSet<>();
        Object springApplication = applicationContext.getBeansWithAnnotation(SpringBootApplication.class).values().iterator().next();
        packages.add(springApplication.getClass().getPackage().getName());
        return packages;
    }

    protected String findAppModule(Collection<String> packages, Environment environment) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TapestryApplication.class));

        for(String pack : packages) {
            Set<BeanDefinition> definitions = scanner.findCandidateComponents(pack);
            if (!definitions.isEmpty()) {
                return definitions.iterator().next().getBeanClassName();
            }
        }

        throw new RuntimeException("TapestryApplication not found. Use @TapestryApplication to mark module.");
    }

    protected SymbolProvider setupTapestryContext(String appModuleClass, String filterName) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Map<String, Object> tapestryContext = new HashMap<>();

        tapestryContext.put("tapestry.filter-name", filterName);

        String servletContextPath = environment.getProperty(SymbolConstants.CONTEXT_PATH, "");
        tapestryContext.put(SymbolConstants.CONTEXT_PATH, servletContextPath);

        String executionMode = environment.getProperty(SymbolConstants.EXECUTION_MODE, "production");
        tapestryContext.put(SymbolConstants.EXECUTION_MODE, executionMode);

        String rootPackageName = appModuleClass.substring(0, appModuleClass.lastIndexOf('.')).replace(".services","");
        tapestryContext.put(InternalConstants.TAPESTRY_APP_PACKAGE_PARAM, rootPackageName);

        environment.getPropertySources().addFirst(new MapPropertySource("tapestry-context", tapestryContext));

        return new DelegatingSymbolProvider(
                new SystemPropertiesSymbolProvider(),
                new SingleKeySymbolProvider(SymbolConstants.CONTEXT_PATH, servletContextPath),
                new SingleKeySymbolProvider(InternalConstants.TAPESTRY_APP_PACKAGE_PARAM, rootPackageName),
                new SingleKeySymbolProvider(SymbolConstants.EXECUTION_MODE, executionMode));
    }

    protected void registerTapestryServices(ConfigurableListableBeanFactory beanFactory, String servicesPackage) {
        ServiceActivityScoreboard scoreboard = registry.getService(ServiceActivityScoreboard.class);
        scoreboard.getServiceActivity().forEach(service-> {
            if (service.getServiceInterface().getPackage().getName().startsWith(servicesPackage)
                    || !service.getMarkers().isEmpty() || service.getServiceInterface().getName().contains("tapestry5")) {
                Object proxy = registry.getService(service.getServiceId(), service.getServiceInterface());
                beanFactory.registerResolvableDependency(service.getServiceInterface(), proxy);
            }
        });

        beanFactory.registerResolvableDependency(Registry.class, registry);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        Collection<String> packagesToScan = findPackagesToScan(applicationContext);
        String appModuleClass = findAppModule(packagesToScan, applicationContext.getEnvironment());
        logger.debug("Found tapestry app module: " + appModuleClass);
        String filterName = appModuleClass.substring(appModuleClass.lastIndexOf('.') + 1).replace("Module", "");
        SymbolProvider combinedProvider = setupTapestryContext(appModuleClass, filterName);
        registry = makeTapestryRegistry(combinedProvider, filterName);
        registerTapestryServices(applicationContext.getBeanFactory(),
                combinedProvider.valueForSymbol(InternalConstants.TAPESTRY_APP_PACKAGE_PARAM) + ".services");

        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner((BeanDefinitionRegistry) applicationContext);
        scanner.scan(getClass().getPackage().getName());
    }
}
