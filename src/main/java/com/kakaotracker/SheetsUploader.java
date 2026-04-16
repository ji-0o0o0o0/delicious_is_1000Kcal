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

public class SheetsUploader {

    private static final Logger logger = LoggerFactory.getLogger(SheetsUploader.class);
    private static final String APPLICATION_NAME = "kakao-tracker";

    private int findRowIndex(Sheets service, String spreadsheetId, String date, String name) throws Exception {
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, "원본기록!A:B")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null) return -1;

        for (int i = 2; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.size() >= 2 && row.get(0).equals(date) && row.get(1).equals(name)) {
                return i + 1;
            }
        }
        return -1;
    }

    public void upload(List<CommentRecord> records) {
        try {
            Sheets service = SheetsService.getService();
            String spreadsheetId = ConfigLoader.get("spreadsheet.id");
            SheetsService.ensureRawDataHeader(service, spreadsheetId);

            String date = records.isEmpty() ? "" : records.get(0).getDate();
            List<String> allMembers = SheetsService.loadMembers();

            List<String> parsedNames = records.stream()
                    .map(CommentRecord::getName)
                    .toList();

            for (String member : allMembers) {
                if (!parsedNames.contains(member)) {
                    records.add(new CommentRecord(date, member, false, false));
                    logger.info("미완료 추가 - {}, {}", date, member);
                }
            }

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
                    ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
                    service.spreadsheets().values()
                            .append(spreadsheetId, "원본기록!A3:E", body)
                            .setValueInputOption("RAW")
                            .execute();
                    logger.info("신규 추가 - {}, {}, {}", record.getDate(), record.getName(), record.getStatus());
                } else {
                    String range = "원본기록!A" + rowIndex + ":E" + rowIndex;
                    ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
                    service.spreadsheets().values()
                            .update(spreadsheetId, range, body)
                            .setValueInputOption("RAW")
                            .execute();
                    logger.info("업데이트 - {}, {}, {}", record.getDate(), record.getName(), record.getStatus());
                }
            }
            SheetsService.sortByDate(service, spreadsheetId);
            logger.info("날짜 기준 정렬 완료");

        } catch (Exception e) {
            logger.error("업로드 실패: {}", e.getMessage(), e);
        }
    }

}