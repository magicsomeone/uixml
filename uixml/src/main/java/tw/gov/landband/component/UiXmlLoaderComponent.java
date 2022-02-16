package tw.gov.landband.component;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.underscore.U;

@Component
public class UiXmlLoaderComponent {
	private static final Logger logger = LoggerFactory.getLogger(UiXmlLoaderComponent.class);
//	@Value("${folder.json}")
//	private String jsonFolder;
	@Value("${folder.xml.template}")
	private String templateXmlFolder;
	@Value("${folder.xml.output}")
	private String outputXmlFolder;
	@Value("${folder.xml.input}")
	private String inputXmlFolder;
	@Value("${folder.xlsx}")
	private String xlsxFolder;
	@Value("${file.xlsx}")
	private String xlsxPath;
	private List<String> languages = List.of("ENG", "CHS", "CHT");

	private record Document(String language, String fileName, String pathName) {
	}

	private record Label(String label, String labelName) {
	}

	private record Content(String language, String label, String labelName) {
	}

	private void cleanFolder(String folderName) {
		File dir = new File(folderName);
		if (dir.exists()) {
			try {
				FileUtils.cleanDirectory(dir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			dir.mkdirs();
		}
	}

	public void load() {
		cleanFolder(templateXmlFolder);
		cleanFolder(xlsxFolder);

		ConcurrentMap<String, ConcurrentMap<String, String>> docMap = languages.parallelStream().map(language -> {
			final String filePath = inputXmlFolder + "/" + language;
			List<Document> documents = new ArrayList<>();
			try (Stream<Path> paths = Files.walk(Paths.get(filePath))) {
				paths.filter(Files::isRegularFile)
						.filter(path -> path.toFile().getName().toLowerCase().endsWith(".xml")) //
						.map(path -> new Document(language, FileNameUtils.getBaseName(path.toString()),
								path.toString()))
						.forEach(documents::add);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return documents;
		}).flatMap(List::stream).collect(Collectors.groupingByConcurrent(Document::fileName,
				Collectors.toConcurrentMap(Document::language, Document::pathName)));

		try (OutputStream outputStream = Files.newOutputStream(Paths.get(xlsxPath));
				Workbook workbook = new XSSFWorkbook();) {
			CellStyle headerStyle = workbook.createCellStyle();
			headerStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setAlignment(HorizontalAlignment.CENTER);
			setBolder(headerStyle);

			XSSFFont contentFont = ((XSSFWorkbook) workbook).createFont();
			contentFont.setFontName("新細明體");

			CellStyle styleLeft = workbook.createCellStyle();
			CellStyle styleRight = workbook.createCellStyle();
			setBolder(styleLeft, styleRight);
			styleLeft.setFont(contentFont);
			styleLeft.setFillForegroundColor(IndexedColors.WHITE.getIndex());
			styleLeft.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			styleLeft.setAlignment(HorizontalAlignment.LEFT);

			styleRight.setFont(contentFont);
			styleRight.setFillForegroundColor(IndexedColors.WHITE.getIndex());
			styleRight.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			styleRight.setAlignment(HorizontalAlignment.RIGHT);

			XSSFFont headerFont = ((XSSFWorkbook) workbook).createFont();
			headerFont.setFontName("新細明體");
//				font.setFontHeightInPoints((short) 14);
			headerFont.setBold(true);
			headerFont.setColor(IndexedColors.BLACK.getIndex());
			headerStyle.setFont(headerFont);

			docMap.entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).forEach(entry -> {
				final String fileName = entry.getKey();
				ConcurrentMap<String, String> langMap = entry.getValue();

				Sheet sheet = workbook.createSheet(fileName);
				sheet.setColumnWidth(0, 14000);
				sheet.setColumnWidth(1, 4000);
				sheet.setColumnWidth(2, 4000);
				sheet.setColumnWidth(3, 4000);

				Row header = sheet.createRow(0);

				Cell headerCell;
				headerCell = header.createCell(0);
				headerCell.setCellValue("LABEL");
				headerCell.setCellStyle(headerStyle);

				for (int i = 0; i < languages.size(); i++) {
					headerCell = header.createCell(i + 1);
					headerCell.setCellValue(languages.get(i));
					headerCell.setCellStyle(headerStyle);
				}

				var groupings = languages.parallelStream().map(language -> {
					final String languageTemplateXmlFolder = templateXmlFolder + "/" + language;
					File languageTemplateXmlDir = new File(languageTemplateXmlFolder);
					if (!languageTemplateXmlDir.exists()) {
						languageTemplateXmlDir.mkdirs();
					}
					List<Label> labels = new ArrayList<>();
					Optional.ofNullable(langMap.get(language)).map(Path::of).map(path -> {
						try {
							return Files.readString(path);
						} catch (MalformedInputException e) {
//							logger.warn(e.getMessage(), e);
						} catch (IOException e) {
							logger.error(e.getMessage(), e);
						}
						return null;
					}).filter(Objects::nonNull).map(U::xmlToJson).map(JSONObject::new).ifPresent(jsonObject -> {
						findAllByTag(labels, jsonObject, "");
						try {
//								Files.writeString(Path.of(jsonFolder + "/" + fileName + ".json"),
//										jsonObject.toString(4));
							Files.writeString(Path.of(languageTemplateXmlFolder + "/" + fileName + ".xml"),
									U.jsonToXml(jsonObject.toString(4)));
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
					return labels.parallelStream()
							.map(label -> new Content(language, label.label(), label.labelName()));
				}).flatMap(Function.identity()).collect(Collectors.groupingByConcurrent(Content::label,
						Collectors.toConcurrentMap(Content::language, Content::labelName)));
				logger.info("groupings size={}", groupings.size());

				groupings.entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).forEach(labelEntry -> {
					final String label = labelEntry.getKey();
					var labelMap = labelEntry.getValue();
					Cell cell;

					Row contentRow = sheet.createRow(sheet.getLastRowNum() + 1);
					cell = contentRow.createCell(0);
					cell.setCellValue(label);
					cell.setCellStyle(styleLeft);

					for (int i = 0; i < languages.size(); i++) {
						final String language = languages.get(i);
						cell = contentRow.createCell(i + 1);
						cell.setCellValue(labelMap.get(language));
						cell.setCellStyle(styleRight);
					}
				});
			});
			workbook.write(outputStream);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	private void setBolder(CellStyle... styles) {
		List.of(styles).stream().forEach(style -> {
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
		});
	}

	private void findAllByTag(List<Label> labels, JSONArray array, String prefixKey) {
		IntStream.range(0, array.length()).forEach(index -> {
			final String currentKey = "".equals(prefixKey) ? Integer.toString(index)
					: prefixKey + "." + Integer.toString(index);
			if (array.get(index) instanceof JSONObject jsonObject) {
				findAllByTag(labels, jsonObject, currentKey);
			} else if (array.get(index) instanceof JSONArray jsonArray) {
				findAllByTag(labels, jsonArray, currentKey);
			}
		});
	}

	private void findAllByTag(List<Label> labels, JSONObject object, String prefixKey) {
		Iterator<String> keys = object.keys();

		while (keys.hasNext()) {
			final String key = keys.next();
			final String label = "".equals(prefixKey) ? key : prefixKey + "." + key;
			if (object.get(key) instanceof JSONObject jsonObject) {
				findAllByTag(labels, jsonObject, label);
			} else if (object.get(key) instanceof JSONArray jsonArray) {
				findAllByTag(labels, jsonArray, label);
			} else if (object.get(key) instanceof String str) {
				if (key.endsWith("LBL") || key.endsWith("TITLE") || "#text".equals(key)) {
					labels.add(new Label(label, str));
					object.put(key, "${" + label + "}");
				}
			}
		}
	}

}
