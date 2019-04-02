package code8.tapestry.services;

import org.apache.tapestry5.ioc.ServiceBinder;

import info.code8.tapestry.TapestryApplication;

/**
 * Created by code8 on 12/10/15.
 */
@TapestryApplication
public class TapestryMainModule {

    public static void bind(ServiceBinder binder) {
        binder.bind(SomeInterface.class, SomeInterfaceImpl.class);
    }

}
