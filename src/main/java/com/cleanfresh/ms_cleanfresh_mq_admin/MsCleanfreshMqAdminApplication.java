package com.cleanfresh.ms_cleanfresh_mq_admin;

import com.cleanfresh.ms_cleanfresh_mq_admin.config.MqProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MqProperties.class)
public class MsCleanfreshMqAdminApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsCleanfreshMqAdminApplication.class, args);
	}

}
