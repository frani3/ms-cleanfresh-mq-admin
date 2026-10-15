package com.cleanfresh.ms_cleanfresh_mq_admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"mq.user=test", "mq.password=test", "mq.alertas-activas=false"})
class MsCleanfreshMqAdminApplicationTests {

	@Test
	void contextLoads() {
	}

}
