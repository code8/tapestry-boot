package info.code8.tapestry;

import java.util.Collections;
import java.util.Set;

import org.apache.tapestry5.ioc.ObjectCreator;
import org.apache.tapestry5.ioc.ScopeConstants;
import org.apache.tapestry5.ioc.ServiceBuilderResources;
import org.apache.tapestry5.ioc.def.ServiceDef2;
import org.springframework.context.ApplicationContext;

/**
 * Created by code8 on 11/8/15.
 */
public class SpringBeanServiceDef implements ServiceDef2
{
    private final String beanName;

    private final ApplicationContext context;

    public SpringBeanServiceDef(String beanName, ApplicationContext context)
    {
        this.beanName = beanName;
        this.context = context;
    }

    @Override
    public boolean isPreventDecoration()
    {
        return true;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public ObjectCreator createServiceCreator(ServiceBuilderResources resources)
    {
        return new ObjectCreator()
        {
            @Override
            public Object createObject()
            {
                return context.getBean(beanName);
            }

            @Override
            public String toString()
            {
                return String.format("ObjectCreator<Spring Bean '%s'>", beanName);
            }
        };
    }

    @Override
    public String getServiceId()
    {
        return beanName;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Set<Class> getMarkers() {
        return Collections.emptySet();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class getServiceInterface() {
        return context.getType(beanName);
    }

    @Override
    public String getServiceScope() {
        return ScopeConstants.DEFAULT;
    }

    @Override
    public boolean isEagerLoad() {
        return false;
    }
}

