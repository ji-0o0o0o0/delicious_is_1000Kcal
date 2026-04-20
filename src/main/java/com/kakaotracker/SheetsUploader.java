package com.kakaotracker;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class SheetsUploader {

    private static final Logger logger = LoggerFactory.getLogger(SheetsUploader.class);
    private static final String APPLICATION_NAME = "kakao-tracker";

    public void upload(List<CommentRecord> records,String imagePath) {
        try {

            Sheets service = SheetsService.getService();
            String spreadsheetId = ConfigLoader.get("spreadsheet.id");

            // 원본기록 전체 한 번만 읽기
            ValueRange allData = service.spreadsheets().values()
                    .get(spreadsheetId, "원본기록!A3:B")
                    .execute();
            List<List<Object>> allRows = allData.getValues();
            // 헤더 없으면 추가
            if (allRows == null || allRows.size() < 2) {
                SheetsService.ensureRawDataHeader(service, spreadsheetId);
            }

            String date = records.isEmpty() ? "" : records.get(0).getDate();
            List<String> allMembers = SheetsService.loadMembers();

            List<String> parsedNames = records.stream()
                    .map(CommentRecord::getName)
                    .toList();

            for (String member : allMembers) {
                if (!parsedNames.contains(member)) {
                    records.add(new CommentRecord(date, member, false, false, false));
                    logger.info("미완료 추가 - {}, {}", date, member);
                }
            }

            records.sort(Comparator.comparingInt(a -> allMembers.indexOf(a.getName())));

            for (CommentRecord record : records) {
                // 행 번호 찾기 (메모리에서)
                int rowIndex = -1;
                if (allRows != null) {
                    for (int i = 0; i < allRows.size(); i++) {
                        List<Object> r = allRows.get(i);
                        if (r.size() >= 2 && r.get(0).equals(record.getDate()) && r.get(1).equals(record.getName())) {
                            rowIndex = i + 3;
                            break;
                        }
                    }
                }

                if (rowIndex == -1) {
                    rowIndex = allRows == null ? 3 : allRows.size() + 3;
                    // allRows에 빈 행 추가해서 다음 행 번호 계산에 반영
                    if (allRows == null) allRows = new ArrayList<>();
                    allRows.add(Arrays.asList(record.getDate(), record.getName()));
                }

                String exerciseVal = record.isCheat() ? "😋" : (record.isExercise() ? "✅" : "❌");
                String dietVal = record.isCheat() ? "😋" : (record.isDiet() ? "✅" : "❌");

                String formula = "=IF(OR(C" + rowIndex + "=\"😋\",D" + rowIndex + "=\"😋\"),\"치팅\",IF(AND(C" + rowIndex + "=\"✅\",D" + rowIndex + "=\"✅\"),\"완료\",IF(C" + rowIndex + "=\"✅\",\"운동만\",IF(D" + rowIndex + "=\"✅\",\"식단만\",\"미완료\"))))";

                List<Object> row = Arrays.asList(
                        record.getDate(),
                        record.getName(),
                        exerciseVal,
                        dietVal,
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