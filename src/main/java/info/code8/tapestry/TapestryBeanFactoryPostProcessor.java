package info.code8.tapestry;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Created by code8 on 11/6/15.
 */

public class TapestryBeanFactoryPostProcessor
        implements BeanFactoryPostProcessor, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(TapestryBeanFactoryPostProcessor.class);
    protected final ConfigurableWebServerApplicationContext applicationContext;
    private Registry registry = null;
    private TapestryAppInitializer appInitializer = null;

    public TapestryBeanFactoryPostProcessor(ConfigurableWebServerApplicationContext applicationContext) {
        super();
        this.applicationContext = applicationContext;
    }

    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        Collection<String> packagesToScan = findPackagesToScan(applicationContext);
        String appModuleClass = findAppModule(packagesToScan, applicationContext.getEnvironment());
        

        String filterName = appModuleClass.substring(appModuleClass.lastIndexOf('.') + 1).replace("Module", "");
        SymbolProvider combinedProvider = setupTapestryContext(appModuleClass, filterName);
        String executionMode = combinedProvider.valueForSymbol(SymbolConstants.EXECUTION_MODE);
        logger.info("TB: About to start Tapestry app module: {}, filterName: {}, executionMode: {} ", appModuleClass, filterName, executionMode);
        appInitializer = new TapestryAppInitializer(logger, combinedProvider, filterName, executionMode);
        appInitializer.addModules(new SpringModuleDef(applicationContext));
        appInitializer.addModules(AssetSourceModule.class);
        logger.info("TB: creating tapestry registry");
        registry = appInitializer.createRegistry();
        
        beanFactory.addBeanPostProcessor(new TapestryFilterPostProcessor());

        registerTapestryServices(applicationContext.getBeanFactory(),
                combinedProvider.valueForSymbol(InternalConstants.TAPESTRY_APP_PACKAGE_PARAM) + ".services",
                registry);

        // This will scan and find TapestryFilter which in turn will be post
        // processed be TapestryFilterPostProcessor completing tapestry initialisation
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner((BeanDefinitionRegistry) applicationContext);
        scanner.scan(TapestryBeanFactoryPostProcessor.class.getPackage().getName());

    }

    private class TapestryFilterPostProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean.getClass() == TapestryFilter.class) {
                logger.info("TB: About to start TapestryFilter, begin Registry initialization");
                registry.performRegistryStartup();
                registry.cleanupThread();
                appInitializer.announceStartup();
                logger.info("TB: About to start TapestryFilter, Registry initialization complete");
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            return bean;
        }

    }

    protected SymbolProvider setupTapestryContext(String appModuleClass, String filterName) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Map<String, Object> tapestryContext = new HashMap<>();

        tapestryContext.put("tapestry.filter-name", filterName);

        String servletContextPath;
        
        if (environment.containsProperty("server.servlet.context-path")) {
            servletContextPath = environment.getProperty("server.servlet.context-path");
        } else if (environment.containsProperty("server.context-path")) {
            servletContextPath = environment.getProperty("server.context-path");
        } else if (environment.containsProperty(SymbolConstants.CONTEXT_PATH)) {
            servletContextPath = environment.getProperty(SymbolConstants.CONTEXT_PATH);
        } else {
            servletContextPath = "";
        }
        
        tapestryContext.put(SymbolConstants.CONTEXT_PATH, servletContextPath);

        String executionMode = environment.getProperty(SymbolConstants.EXECUTION_MODE, "production");
        tapestryContext.put(SymbolConstants.EXECUTION_MODE, executionMode);

        String rootPackageName = appModuleClass.substring(0, appModuleClass.lastIndexOf('.')).replace(".services", "");
        tapestryContext.put(InternalConstants.TAPESTRY_APP_PACKAGE_PARAM, rootPackageName);

        environment.getPropertySources().addFirst(new MapPropertySource("tapestry-context", tapestryContext));

        return new DelegatingSymbolProvider(
                new SystemPropertiesSymbolProvider(),
                new SingleKeySymbolProvider(SymbolConstants.CONTEXT_PATH, servletContextPath),
                new SingleKeySymbolProvider(InternalConstants.TAPESTRY_APP_PACKAGE_PARAM, rootPackageName),
                new SingleKeySymbolProvider(SymbolConstants.EXECUTION_MODE, executionMode));
    }

    protected Collection<String> findPackagesToScan(ConfigurableApplicationContext applicationContext) {
        Set<String> packages = new HashSet<>();
        Object springApplication = applicationContext.getBeansWithAnnotation(SpringBootApplication.class).values().iterator()
                .next();
        packages.add(springApplication.getClass().getPackage().getName());
        return packages;
    }

    protected String findAppModule(Collection<String> packages, Environment environment) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TapestryApplication.class));
        for (String pack : packages) {
            Set<BeanDefinition> definitions = scanner.findCandidateComponents(pack);
            if (!definitions.isEmpty()) {
                return definitions.iterator().next().getBeanClassName();
            }
        }
        throw new RuntimeException("TapestryApplication not found. Use @TapestryApplication to mark module.");
    }

    protected void registerTapestryServices(ConfigurableListableBeanFactory beanFactory, String servicesPackage,
            Registry registry) {
        ServiceActivityScoreboard scoreboard = registry.getService(ServiceActivityScoreboard.class);
        scoreboard.getServiceActivity().forEach(service -> {
            if (service.getServiceInterface().getPackage().getName().startsWith(servicesPackage)
                    || !service.getMarkers().isEmpty() || service.getServiceInterface().getName().contains("tapestry5")) {
                Object proxy = registry.getService(service.getServiceId(), (Class<?>) service.getServiceInterface());
                beanFactory.registerResolvableDependency(service.getServiceInterface(), proxy);
                logger.debug("TB: tapestry service {} exposed to spring", service.getServiceId());
            }
        });
        beanFactory.registerResolvableDependency(Registry.class, registry);
        logger.info("TB: tapestry Registry registered with spring (Still pending initialization)");
    }
}
