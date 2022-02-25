package tw.gov.landband.component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UiXmlGeneratorComponent {
	private static final Logger logger = LoggerFactory.getLogger(UiXmlGeneratorComponent.class);
	@Value("${folder.xml.template}")
	private String templateXmlFolder;
	@Value("${folder.xml.output}")
	private String outputXmlFolder;
	@Value("${folder.xlsx}")
	private String xlsxFolder;
	@Value("${file.xlsx}")
	private String xlsxPath;
	private List<String> languages = List.of("ENG", "CHS", "CHT");

	private record Content(String language, String label, String labelName) {
	}

	private void cleanFolder(String folderName) {
		File dir = new File(folderName);
		if (dir.exists()) {
			try {
				FileUtils.cleanDirectory(dir);
			} catch (IOException e) {
				logger.error("Directory remove failed.");
			}
		} else {
			dir.mkdirs();
		}
	}

	public void generate() {
		cleanFolder(outputXmlFolder);

		try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(Path.of(xlsxPath)));) {
			final int sheetSize = workbook.getNumberOfSheets();
			logger.info("Sheet size={}", sheetSize);
			for (int sheetNum = 0; sheetNum < sheetSize; sheetNum++) {
				Sheet sheet = workbook.getSheetAt(sheetNum);
				final String fileName = sheet.getSheetName();
				final int rowSize = sheet.getPhysicalNumberOfRows();
				logger.info("Sheet name: {}, Row size={}", fileName, rowSize);
				List<Content> contents = new ArrayList<>();
				for (int rowNum = 1; rowNum < rowSize; rowNum++) {
					Optional.ofNullable(sheet.getRow(rowNum)).ifPresent(row -> {
						final String label = row.getCell(0, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK)
								.getStringCellValue();
						final String engName = row.getCell(1, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK)
								.getStringCellValue();
						final String chsName = row.getCell(2, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK)
								.getStringCellValue();
						final String chtName = row.getCell(3, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK)
								.getStringCellValue();
						contents.add(new Content("ENG", label, engName));
						contents.add(new Content("CHS", label, chsName));
						contents.add(new Content("CHT", label, chtName));
					});
				}
				var languageMap = contents.parallelStream().collect(Collectors.groupingByConcurrent(Content::language,
						Collectors.toConcurrentMap(Content::label, Content::labelName)));
				logger.info("languageMap size={}", languageMap.size());

				languages.parallelStream().forEach(language -> {
					final String languageTemplateXmlFolder = templateXmlFolder + "/" + language;
					final String filePath = languageTemplateXmlFolder + "/" + fileName + ".xml";
					File file = new File(filePath);
					if (file.exists()) {
						Optional.ofNullable(languageMap.get(language)).ifPresent(labelMap -> {
							try {
								Optional<String> xmlStringOpt = Optional
										.ofNullable(Files.readString(Path.of(filePath)));
								var replaces = labelMap.entrySet().stream().map(labelEntry -> {
									final String key = "${" + labelEntry.getKey() + "}";
									final String replacement = labelEntry.getValue();
									Function<String, String> replace = xmlString -> xmlString.replace(key, replacement);
									return replace;
								}).toList();

								for (Function<String, String> replace : replaces) {
									xmlStringOpt = xmlStringOpt.map(replace);
								}
								xmlStringOpt.ifPresent(xmlString -> {
									final String outputLanguageXmlFolder = outputXmlFolder + "/" + language;
									File outputLanguageXmlDir = new File(outputLanguageXmlFolder);
									if (!outputLanguageXmlDir.exists()) {
										outputLanguageXmlDir.mkdirs();
									}
									final String outputLanguageXmlPath = outputXmlFolder + "/" + language + "/"
											+ fileName + ".xml";
									try {
										Files.write(Path.of(outputLanguageXmlPath), xmlString.getBytes());
									} catch (IOException e) {
										logger.error("File write failed.");
									}
								});
							} catch (IOException e) {
								logger.error("File read failed.");
							}
						});
					}
				});
			}
		} catch (IOException e) {
			logger.error("XLSX file read failed");
		}
	}
}
