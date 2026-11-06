package vn.sunvibe.contract;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Puts the wire check in every servlet test context, from {@code src/test/resources} only, so no test class changes and
 * nothing of it reaches the application (contract-wire spec 4.3).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WireContractAutoConfiguration {

	@Bean
	WireContractFilter wireContractFilter() {
		return new WireContractFilter();
	}
}
