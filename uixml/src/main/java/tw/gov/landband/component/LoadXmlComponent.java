package tw.gov.landband.component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tw.gov.landband.SortedProperties;

@Component
public class LoadXmlComponent {
	private static final Logger logger = LoggerFactory.getLogger(LoadXmlComponent.class);

	private void findAllByTag(SortedProperties prop, JSONArray array, String prefixKey) {
		IntStream.range(0, array.length()).forEach(index -> {
			final String currentKey = "".equals(prefixKey) ? Integer.toString(index)
					: prefixKey + "." + Integer.toString(index);
			if (array.get(index) instanceof JSONObject jsonObject) {
				findAllByTag(prop, jsonObject, currentKey);
			} else if (array.get(index) instanceof JSONArray jsonArray) {
				findAllByTag(prop, jsonArray, currentKey);
			}
		});
	}

	private void findAllByTag(SortedProperties prop, JSONObject object, String prefixKey) {
		Iterator<String> keys = object.keys();

		while (keys.hasNext()) {
			final String key = keys.next();
			final String currentKey = "".equals(prefixKey) ? key : prefixKey + "." + key;
			if (object.get(key) instanceof JSONObject jsonObject) {
				findAllByTag(prop, jsonObject, currentKey);
			} else if (object.get(key) instanceof JSONArray jsonArray) {
				findAllByTag(prop, jsonArray, currentKey);
			} else if (object.get(key) instanceof String str) {
				if ("LBL".equals(key) || "TITLE".equals(key) || "BUTTON_LBL".equals(key) || "content".equals(key)) {
					prop.put(currentKey, str);
				}
			}
		}
	}

	public void process() {
		String uixmlFolder = "/home/yicheng/Documents/landbank/js_uixml/UIXML";
		List<String> languages = List.of("ENG", "CHS", "CHT");

		languages.parallelStream().forEach(language -> {
			final String filePath = uixmlFolder + "/" + language;
			try (Stream<Path> paths = Files.walk(Paths.get(filePath))) {
				paths.filter(Files::isRegularFile)
						.filter(path -> path.toFile().getName().toLowerCase().endsWith(".xml")) //
						.forEach(path -> {
							final String fileName = path.toFile().getName();
							final String baseFileName = FilenameUtils.getBaseName(fileName);
							final String ExtensionName = FilenameUtils.getExtension(fileName);
							logger.info("{}.{}", baseFileName, ExtensionName);

							String outputFolderName = "./properties/" + language;
							File properiteFolder = new File(outputFolderName);
							if (!properiteFolder.exists()) {
								properiteFolder.mkdir();
							}
							String propName = outputFolderName + "/" + baseFileName + ".properties";
							try {
								final String xmlString = Files.readString(path);
								JSONObject jsonObj = XML.toJSONObject(xmlString); // converts xml to json

								SortedProperties prop = new SortedProperties();
								findAllByTag(prop, jsonObj, "");
								prop.keys();
								prop.store(new FileOutputStream(propName), "label for pxdoview");
							} catch (IOException e) {
								e.printStackTrace();
							}
						});
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

	}

}
