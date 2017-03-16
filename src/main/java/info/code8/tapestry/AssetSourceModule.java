package info.code8.tapestry;

import java.util.Locale;

import org.apache.tapestry5.Asset;
import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.ioc.Resource;
import org.apache.tapestry5.ioc.annotations.Decorate;
import org.apache.tapestry5.ioc.services.SymbolSource;
import org.apache.tapestry5.services.AssetSource;

/**
 * Created by code8 on 11/9/15.
 */
public class AssetSourceModule {
    @Decorate(serviceInterface = AssetSource.class)
    public static AssetSource decorateAssetSource(AssetSource origin, SymbolSource symbolSource) {
        return new AssetSource() {
            @Override
            public Asset getAsset(Resource baseResource, String path, Locale locale) {
                return origin.getAsset(baseResource, convert(path), locale);
            }

            @Override
            public Resource resourceForPath(String path) {
                return origin.resourceForPath(convert(path));
            }

            @Override
            public Asset getClasspathAsset(String path, Locale locale) {
                return origin.getClasspathAsset(path, locale);
            }

            @Override
            public Asset getContextAsset(String path, Locale locale) {
                return origin.getContextAsset(convert(path), locale);
            }

            @Override
            public Asset getClasspathAsset(String path) {
                return origin.getClasspathAsset(path);
            }

            @Override
            public Asset getUnlocalizedAsset(String path) {
                return origin.getUnlocalizedAsset(convert(path));
            }

            @Override
            public Asset getExpandedAsset(String path) {

                return origin.getUnlocalizedAsset(convert(symbolSource.expandSymbols(path)));
            }

            @Override
            public Asset getComponentAsset(ComponentResources resources, String path, String libraryName) {
                return origin.getComponentAsset(resources, convert(path), libraryName);
            }

            private String convert(String path) {
                if (path.contains("context:")) {
                    path = path.replace("context:", "classpath:");

                    if (path.contains("WEB-INF/")) {
                        return path.replace("WEB-INF/", "");
                    } else {
                        if (path.startsWith("/")) {
                            path = path.substring(1);
                        }

                        if (path.contains(":")) {
                            int separatorIndex = path.indexOf(":");
                            return path.substring(0, separatorIndex) + ":" + "/META-INF/assets/" + path.substring(separatorIndex + 1);
                        } else {
                            return "/META-INF/assets/" + path;
                        }
                    }
                } else {
                    return path;
                }
            }
        };
    }

}
