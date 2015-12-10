package code8;

import code8.tapestry.services.SomeInterface;
import org.apache.tapestry5.services.RequestGlobals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.servlet.ViewResolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@WebIntegrationTest
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Launcher.class)
public class DemoApplicationTests {

	@Autowired
	ApplicationContext applicationContext;

	@Autowired
	RequestGlobals requestGlobals;

	@Autowired
	SomeInterface someInterface;

	@Autowired
	@Qualifier("mvcViewResolver")
	ViewResolver viewResolver;

	@Test
	public void contextLoads() {
		assertNotNull(applicationContext);
	}

	@Test
	public void tapestryServiceToSpringInjection() {
		assertNotNull(requestGlobals);
	}

	@Test
	public void springServiceToTapestryInjection() {
		assertNotNull(someInterface);
		assertEquals(someInterface.testMethod(), viewResolver);
	}
}
