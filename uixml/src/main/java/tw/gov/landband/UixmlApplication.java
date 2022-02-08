package tw.gov.landband;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import tw.gov.landband.component.LoadXmlComponent;

@SpringBootApplication
public class UixmlApplication implements CommandLineRunner {
	@Autowired
	private LoadXmlComponent loadXmlComponent;

	public static void main(String[] args) {
		SpringApplication.run(UixmlApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		loadXmlComponent.process();
		System.exit(0);
	}

}
