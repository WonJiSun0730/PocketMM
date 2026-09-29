package com.example.projectmm;

import com.example.projectmm.application.dialogue.DialogueGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:projectmm-context-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false"
})
class ProjectmmApplicationTests {
	@MockitoBean
	private DialogueGateway dialogueGateway;

	@Test
	void contextLoads() {
	}

}
