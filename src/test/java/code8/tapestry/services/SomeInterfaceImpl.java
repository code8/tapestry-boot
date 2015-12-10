package code8.tapestry.services;

import org.apache.tapestry5.annotations.Service;
import org.springframework.web.servlet.ViewResolver;

/**
 * Created by code8 on 12/10/15.
 */
public class SomeInterfaceImpl implements SomeInterface {
    final ViewResolver springViewResolver;

    public SomeInterfaceImpl(@Service("mvcViewResolver") ViewResolver springViewResolver) {
        this.springViewResolver = springViewResolver;
    }

    @Override
    public ViewResolver testMethod() {
        return springViewResolver;
    }
}
