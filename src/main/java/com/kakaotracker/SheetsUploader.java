package com.kakaotracker;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class SheetsUploader {

    private static final Logger logger = LoggerFactory.getLogger(SheetsUploader.class);
    private static final String APPLICATION_NAME = "kakao-tracker";

    private Sheets getSheetsService() throws Exception {
        Properties props = new Properties();
        InputStream configIs = SheetsUploader.class.getClassLoader().getResourceAsStream("config.properties");
        if (configIs == null) {
            throw new IllegalStateException("config.properties 파일을 찾을 수 없습니다.");
        }
        props.load(configIs);
        String credentialsFile = props.getProperty("credentials.file");

        InputStream credIs = SheetsUploader.class.getClassLoader().getResourceAsStream(credentialsFile);
        if (credIs == null) {
            throw new IllegalStateException("credentials 파일을 찾을 수 없습니다: " + credentialsFile);
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(credIs)
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/spreadsheets"));

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private String getSpreadsheetId() throws Exception {
        Properties props = new Properties();
        InputStream is = SheetsUploader.class.getClassLoader().getResourceAsStream("config.properties");
        if (is == null) throw new IllegalStateException("config.properties 파일을 찾을 수 없습니다.");
        props.load(is);
        return props.getProperty("spreadsheet.id");
    }

    // 중복 행의 행 번호 반환 (없으면 -1)
    private int findRowIndex(Sheets service, String spreadsheetId, String date, String name) throws Exception {
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, "원본기록!A:B")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null) return -1;

        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.size() >= 2 && row.get(0).equals(date) && row.get(1).equals(name)) {
                return i + 1; // 시트는 1부터 시작
            }
        }
        return -1;
    }

    public void upload(List<CommentRecord> records) {
        try {
            Sheets service = getSheetsService();
            String spreadsheetId = getSpreadsheetId();

            String date = records.isEmpty() ? "" : records.get(0).getDate();
            List<String> allMembers = loadMembers();

            List<String> parsedNames = records.stream()
                    .map(CommentRecord::getName)
                    .toList();

            for (String member : allMembers) {
                if (!parsedNames.contains(member)) {
                    records.add(new CommentRecord(date, member, false, false));
                    logger.info("미완료 추가 - {}, {}", date, member);
                }
            }

            // members.txt 순서대로 정렬
            records.sort((a, b) -> allMembers.indexOf(a.getName()) - allMembers.indexOf(b.getName()));

            for (CommentRecord record : records) {
                List<Object> row = Arrays.asList(
                        record.getDate(),
                        record.getName(),
                        record.isExercise() ? "✅" : "❌",
                        record.isDiet() ? "✅" : "❌",
                        record.getStatus()
                );

                int rowIndex = findRowIndex(service, spreadsheetId, record.getDate(), record.getName());

                if (rowIndex == -1) {
                    // 신규 추가
                    ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
                    service.spreadsheets().values()
                            .append(spreadsheetId, "원본기록!A:E", body)
                            .setValueInputOption("RAW")
                            .execute();
                    logger.info("신규 추가 - {}, {}, {}", record.getDate(), record.getName(), record.getStatus());
                } else {
                    // 기존 행 업데이트
                    String range = "원본기록!A" + rowIndex + ":E" + rowIndex;
                    ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
                    service.spreadsheets().values()
                            .update(spreadsheetId, range, body)
                            .setValueInputOption("RAW")
                            .execute();
                    logger.info("업데이트 - {}, {}, {}", record.getDate(), record.getName(), record.getStatus());
                }
            }

        } catch (Exception e) {
            logger.error("업로드 실패: {}", e.getMessage(), e);
        }
    }

    private List<String> loadMembers() {
        List<String> members = new ArrayList<>();
        try (InputStream is = SheetsUploader.class.getClassLoader().getResourceAsStream("members.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) members.add(line.trim());
            }
        } catch (Exception e) {
            logger.error("members.txt 읽기 실패: {}", e.getMessage(), e);
        }
        return members;
    }
}