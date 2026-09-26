package com.example.backend.global.init;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

import java.util.*;

@Component
public class DiagnosisMasterReader {
    public static final String SHEET = "상병분류기호(완전코드)";

    public record Entry(String code, String koreanName, String englishName, boolean completeCode,
                        boolean principalAllowed, String infectionClass, String sexRestriction,
                        Integer minimumAge, Integer maximumAge, String medicineType, int sourceRow) {}

    public List<Entry> read(Resource resource) {
        try (var input = resource.getInputStream(); var pkg = OPCPackage.open(input)) {
            var reader = new XSSFReader(pkg);
            reader.setUseReadOnlySharedStringsTable(true);
            var strings = reader.getSharedStringsTable();
            var sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (var sheet = sheets.next()) {
                    if (!SHEET.equals(sheets.getSheetName())) continue;
                    var rows = new Rows();
                    var parser = XMLHelper.newXMLReader();
                    parser.setContentHandler(new XSSFSheetXMLHandler(reader.getStylesTable(), strings,
                            rows, new DataFormatter(Locale.ROOT), false));
                    parser.parse(new InputSource(sheet));
                    if (!rows.headerFound || rows.entries.isEmpty())
                        throw new IllegalArgumentException("상병마스터 헤더 또는 데이터가 없습니다.");
                    return List.copyOf(rows.entries);
                }
            }
            throw new IllegalArgumentException("상병마스터 시트가 없습니다: " + SHEET);
        } catch (Exception e) {
            throw new IllegalStateException("상병마스터 읽기 실패: " + resource.getDescription(), e);
        }
    }

    private static final class Rows implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final Map<Integer, String> cells = new HashMap<>();
        private final Map<String, Integer> columns = new HashMap<>();
        private final List<Entry> entries = new ArrayList<>();
        private boolean headerFound;

        @Override public void startRow(int rowNum) { cells.clear(); }
        @Override public void cell(String reference, String value, XSSFComment comment) {
            cells.put(new CellReference(reference).getCol() & 0xffff, value.strip());
        }
        @Override public void endRow(int rowNum) {
            if (!headerFound) {
                if (!cells.containsValue("상병기호")) return;
                cells.forEach((column, name) -> columns.put(name.replaceAll("\\s+", ""), column));
                for (var name : List.of("상병기호", "한글명", "영문영", "완전코드구분", "주상병사용구분",
                        "법정감염병구분", "성별구분", "상한연령", "하한연령", "양•한방구분")) {
                    if (!columns.containsKey(name)) throw invalid(rowNum, "필수 열 없음: " + name);
                }
                headerFound = true;
                return;
            }
            if (cells.values().stream().allMatch(String::isBlank)) return;
            if (!flag("완전코드구분", rowNum)) throw invalid(rowNum, "완전코드 시트에 불완전코드가 포함되어 있습니다.");
            var code = value("상병기호");
            if (code.isBlank() || code.length() > 20 || value("한글명").isBlank())
                throw invalid(rowNum, "코드 또는 한글명 누락/오류");
            var sex = value("성별구분");
            if (!Set.of("", "X", "Y").contains(sex)) throw invalid(rowNum, "성별구분 오류");
            var min = age("하한연령", rowNum);
            var max = age("상한연령", rowNum);
            if (min != null && max != null && min > max) throw invalid(rowNum, "연령 범위 오류");
            entries.add(new Entry(code, value("한글명"), value("영문영"), flag("완전코드구분", rowNum),
                    flag("주상병사용구분", rowNum), value("법정감염병구분"), sex,
                    min, max, value("양•한방구분"), rowNum + 1));
        }
        private String value(String name) { return cells.getOrDefault(columns.get(name), ""); }
        private boolean flag(String name, int row) {
            var value = value(name);
            if (!value.isEmpty() && !value.equals("N")) throw invalid(row, name + " 값 오류");
            return value.isEmpty();
        }
        private Integer age(String name, int row) {
            var value = value(name);
            if (value.isBlank()) return null;
            try {
                int age = Integer.parseInt(value);
                if (age < 0) throw new NumberFormatException();
                return age;
            } catch (NumberFormatException e) { throw invalid(row, name + " 값 오류"); }
        }
        private IllegalArgumentException invalid(int row, String message) {
            return new IllegalArgumentException("상병마스터 " + (row + 1) + "행: " + message);
        }
    }
}
