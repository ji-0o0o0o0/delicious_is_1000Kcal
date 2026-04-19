package com.kakaotracker;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

    public void upload(List<CommentRecord> records,String imagePath) {
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

            records.sort(Comparator.comparingInt(a -> allMembers.indexOf(a.getName())));

            for (CommentRecord record : records) {
                int rowIndex = findRowIndex(service, spreadsheetId, record.getDate(), record.getName());

                if (rowIndex == -1) {
                    // 맨 밑 다음 행 번호 찾기
                    ValueRange response = service.spreadsheets().values()
                            .get(spreadsheetId, "원본기록!A:A")
                            .execute();
                    rowIndex = response.getValues() == null ? 3 : response.getValues().size() + 1;
                }

                String formula = "=IF(AND(C" + rowIndex + "=\"✅\",D" + rowIndex + "=\"✅\"),\"완료\",IF(C" + rowIndex + "=\"✅\",\"운동만\",IF(D" + rowIndex + "=\"✅\",\"식단만\",\"미완료\")))";

                List<Object> row = Arrays.asList(
                        record.getDate(),
                        record.getName(),
                        record.isExercise() ? "✅" : "❌",
                        record.isDiet() ? "✅" : "❌",
                        formula,
                        ""
                );

                String range = "원본기록!A" + rowIndex + ":F" + rowIndex;
                ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
                service.spreadsheets().values()
                        .update(spreadsheetId, range, body)
                        .setValueInputOption("USER_ENTERED")
                        .execute();

                logger.info("업로드 - {}, {}, {}", record.getDate(), record.getName(), record.getStatus());
            }
            SheetsService.sortByDate(service, spreadsheetId);
            logger.info("날짜 기준 정렬 완료");

            moveImageToDone(imagePath);

        } catch (Exception e) {
            logger.error("업로드 실패: {}", e.getMessage(), e);
        }
    }

    // 처리 완료된 이미지 done 폴더로 이동
    private void moveImageToDone(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) return;

            File doneDir = new File(imageFile.getParent() + "/done");
            if (!doneDir.exists()) doneDir.mkdirs();

            File destFile = new File(doneDir, imageFile.getName());
            if (imageFile.renameTo(destFile)) {
                logger.info("이미지 이동 완료 - {}", destFile.getPath());
            } else {
                logger.warn("이미지 이동 실패 - {}", imagePath);
            }
        } catch (Exception e) {
            logger.error("이미지 이동 실패: {}", e.getMessage(), e);
        }
    }

}