package com.fixflow.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@SpringBootTest
@TestExecutionListeners({
        DependencyInjectionTestExecutionListener.class
})
class IdentityServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
