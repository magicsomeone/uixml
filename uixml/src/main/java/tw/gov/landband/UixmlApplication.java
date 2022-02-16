package tw.gov.landband;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import tw.gov.landband.component.UiXmlGeneratorComponent;
import tw.gov.landband.component.UiXmlLoaderComponent;

@SpringBootApplication
public class UixmlApplication implements CommandLineRunner {
	@Autowired
	private UiXmlLoaderComponent loaderComponent;
	@Autowired
	private UiXmlGeneratorComponent generatorComponent;

	public static void main(String[] args) {
		SpringApplication.run(UixmlApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
//		loaderComponent.load();
		generatorComponent.generate();
		System.exit(0);
	}

}
