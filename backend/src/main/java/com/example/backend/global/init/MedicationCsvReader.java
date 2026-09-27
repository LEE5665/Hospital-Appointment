package com.example.backend.global.init;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/** Reads only product-level fields; packaging rows are collapsed by representative KD code. */
@Component
public class MedicationCsvReader {
    public record Entry(String code, String name, String manufacturer, String specification,
                        String category, String productCode, String ingredientCode) {}

    public List<Entry> read(Resource resource, Charset charset) {
        try (var reader = new PushbackReader(new BufferedReader(new InputStreamReader(resource.getInputStream(),
                charset.newDecoder())), 1)) {
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') reader.unread(first);
            var header = row(reader);
            if (header == null) throw new IllegalArgumentException("빈 의약품 CSV입니다.");
            var columns = new HashMap<String, Integer>();
            for (int i = 0; i < header.size(); i++) columns.put(header.get(i).replace("\uFEFF", "").strip(), i);
            for (String required : List.of("대표코드", "표준코드", "한글상품명", "업체명", "약품규격", "전문일반구분",
                    "제품코드(개정후)", "일반명코드(성분명코드)", "취소일자"))
                if (!columns.containsKey(required)) throw new IllegalArgumentException("의약품 CSV 필수 열 없음: " + required);
            var entries = new LinkedHashMap<String, Entry>();
            Set<String> representativeRows = new HashSet<>();
            List<String> cells;
            while ((cells = row(reader)) != null) {
                if (cells.stream().allMatch(String::isBlank)) continue;
                if (cells.size() != header.size()) throw new IllegalArgumentException("의약품 CSV 열 개수가 일치하지 않습니다.");
                final var values = cells;
                java.util.function.Function<String, String> get = key -> values.get(columns.get(key)).strip();
                String category = get.apply("전문일반구분");
                if (!Set.of("전문의약품", "일반의약품", "전문,희귀").contains(category) || !get.apply("취소일자").isEmpty()) continue;
                String code = get.apply("대표코드");
                if (!code.matches("[0-9]{13}") || get.apply("한글상품명").isEmpty())
                    throw new IllegalArgumentException("의약품 대표코드 또는 상품명이 올바르지 않습니다.");
                boolean representative = code.equals(get.apply("표준코드"));
                // Do not turn a package size into a product's dose/strength.
                String specification = representative ? get.apply("약품규격") : "";
                if (specification.equals("없음")) specification = "";
                var entry = new Entry(code, get.apply("한글상품명"), get.apply("업체명"), specification,
                    category, get.apply("제품코드(개정후)"), get.apply("일반명코드(성분명코드)"));
                if (representative) {
                    if (representativeRows.add(code)) entries.put(code, entry);
                } else entries.putIfAbsent(code, entry);
            }
            if (entries.isEmpty()) throw new IllegalArgumentException("적재할 의약품이 없습니다.");
            return List.copyOf(entries.values());
        } catch (IOException e) {
            throw new IllegalStateException("의약품 CSV 읽기 실패", e);
        }
    }

    // RFC-style quoted fields: commas, escaped quotes, and embedded newlines are supported.
    private static List<String> row(PushbackReader reader) throws IOException {
        var cells = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false, closed = false, any = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            any = true;
            if (quoted) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; closed = true; if (next != -1) reader.unread(next); }
                } else field.append((char) ch);
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                cells.add(field.toString()); field.setLength(0); closed = false;
                if (ch != ',') {
                    if (ch == '\r') { int next = reader.read(); if (next != '\n' && next != -1) reader.unread(next); }
                    return cells;
                }
            } else if (ch == '"' && field.isEmpty() && !closed) quoted = true;
            else if (closed || ch == '"') throw new IllegalArgumentException("의약품 CSV 따옴표 형식 오류");
            else field.append((char) ch);
        }
        if (quoted) throw new IllegalArgumentException("의약품 CSV 따옴표가 닫히지 않았습니다.");
        if (!any) return null;
        cells.add(field.toString());
        return cells;
    }
}
