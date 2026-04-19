package com.kakaotracker;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SheetsService {

    private static final Logger logger = LoggerFactory.getLogger(SheetsService.class);
    private static final String APPLICATION_NAME = "kakao-tracker";

    // ==================== 연결/인증 ====================
    public static Sheets getService() throws Exception {
        String credentialsFile = ConfigLoader.get("credentials.file");
        InputStream credIs = SheetsService.class.getClassLoader().getResourceAsStream(credentialsFile);
        if (credIs == null) throw new IllegalStateException("credentials 파일을 찾을 수 없습니다: " + credentialsFile);

        GoogleCredentials credentials = GoogleCredentials.fromStream(credIs)
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/spreadsheets"));

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static Integer getSheetId(Sheets service, String spreadsheetId, String sheetName) throws Exception {
        Spreadsheet spreadsheet = service.spreadsheets().get(spreadsheetId).execute();
        for (Sheet sheet : spreadsheet.getSheets()) {
            if (sheetName.equals(sheet.getProperties().getTitle())) {
                return sheet.getProperties().getSheetId();
            }
        }
        throw new IllegalStateException("시트를 찾을 수 없습니다: " + sheetName);
    }
    // ==================== 멤버 ====================
    public static List<String> loadMembers() {
        List<String> members = new ArrayList<>();
        try {
            // 외부 members.txt 먼저 찾기
            String jarDir = new File(ConfigLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent();
            String externalPath = jarDir + "/members.txt";
            java.io.File externalFile = new java.io.File(externalPath);

            InputStream is;
            if (externalFile.exists()) {
                is = new java.io.FileInputStream(externalFile);
                logger.info("외부 members.txt 로드: {}", externalPath);
            } else {
                is = SheetsService.class.getClassLoader().getResourceAsStream("members.txt");
                logger.info("내부 members.txt 로드");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) members.add(line.trim());
                }
            }
        } catch (Exception e) {
            logger.error("members.txt 읽기 실패: {}", e.getMessage(), e);
        }
        return members;
    }
    // ==================== 원본기록 ====================
    public static void ensureRawDataHeader(Sheets service, String spreadsheetId) throws Exception {
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, "원본기록!A1:E2")
                .execute();

        List<List<Object>> values = response.getValues();
        boolean hasHeader = values != null && values.size() >= 2;
        if (hasHeader) return;

        List<List<Object>> header = new ArrayList<>();
        header.add(Arrays.asList("📋 원본 기록", "", "", "", "", ""));
        header.add(Arrays.asList("날짜", "이름", "운동", "식단", "완료여부", "수정여부"));

        ValueRange body = new ValueRange().setValues(header);
        service.spreadsheets().values()
                .update(spreadsheetId, "원본기록!A1", body)
                .setValueInputOption("RAW")
                .execute();
    }
    public static void sortByDate(Sheets service, String spreadsheetId) throws Exception {
        SortRangeRequest sortRequest = new SortRangeRequest()
                .setRange(new GridRange()
                        .setSheetId(getSheetId(service, spreadsheetId, "원본기록"))
                        .setStartRowIndex(2)
                        .setEndRowIndex(null) // 끝까지
                        .setStartColumnIndex(0)
                        .setEndColumnIndex(6))
                .setSortSpecs(Collections.singletonList(
                        new SortSpec()
                                .setDimensionIndex(0) // A열 기준
                                .setSortOrder("ASCENDING")
                ));

        service.spreadsheets().batchUpdate(spreadsheetId,
                        new BatchUpdateSpreadsheetRequest().setRequests(
                                Collections.singletonList(new Request().setSortRange(sortRequest))))
                .execute();
    }
    public static LocalDate getLastRecordedDate(Sheets service, String spreadsheetId, LocalDate from) throws Exception {
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, "원본기록!A:A")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null) return from;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate lastDate = from;

        for (List<Object> row : values) {
            if (row.isEmpty()) continue;
            try {
                LocalDate date = LocalDate.parse(row.get(0).toString(), fmt);
                if (!date.isBefore(from) && date.isAfter(lastDate)) {
                    lastDate = date;
                }
            } catch (Exception ignored) {}
        }
        return lastDate;
    }

    public static List<ModifiedRow> getModifiedRows(Sheets service, String spreadsheetId) throws Exception {
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, "원본기록!A:F")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null) return new ArrayList<>();

        List<ModifiedRow> modifiedRows = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.size() >= 6 && "Y".equals(row.get(5).toString())) {
                try {
                    LocalDate date = LocalDate.parse(row.get(0).toString(), fmt);
                    modifiedRows.add(new ModifiedRow(i + 1, date));
                } catch (Exception ignored) {}
            }
        }
        return modifiedRows;
    }

    public static void updateModificationStatus(Sheets service, String spreadsheetId, int rowNum, String status) throws Exception {
        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(Collections.singletonList(status)));
        service.spreadsheets().values()
                .update(spreadsheetId, "원본기록!F" + rowNum, body)
                .setValueInputOption("RAW")
                .execute();
    }
    // ==================== 통계 시트 공통 ====================
    public static void ensureSheetTitle(Sheets service, String spreadsheetId, String sheetName, String title) throws Exception {
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!A1")
                .execute();

        List<List<Object>> values = response.getValues();
        boolean hasTitle = values != null && !values.isEmpty() && !values.get(0).isEmpty()
                && values.get(0).get(0).toString().equals(title);

        if (!hasTitle) {
            ValueRange body = new ValueRange().setValues(
                    Collections.singletonList(Collections.singletonList(title))
            );
            service.spreadsheets().values()
                    .update(spreadsheetId, sheetName + "!A1", body)
                    .setValueInputOption("RAW")
                    .execute();
        }
    }

    public static void insertRowsAtTop(Sheets service, String spreadsheetId, String sheetName, List<List<Object>> rows) throws Exception {
        // 항상 2행부터 삽입 (1행은 시트 제목)
        InsertDimensionRequest insertRequest = new InsertDimensionRequest()
                .setRange(new DimensionRange()
                        .setSheetId(getSheetId(service, spreadsheetId, sheetName))
                        .setDimension("ROWS")
                        .setStartIndex(1)
                        .setEndIndex(1 + rows.size()))
                .setInheritFromBefore(false);

        service.spreadsheets().batchUpdate(spreadsheetId,
                        new BatchUpdateSpreadsheetRequest().setRequests(
                                Collections.singletonList(new Request().setInsertDimension(insertRequest))))
                .execute();

        service.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A2", new ValueRange().setValues(rows))
                .setValueInputOption("RAW")
                .execute();
    }

    public static void deleteExistingStats(Sheets service, String spreadsheetId, String sheetName, String title) throws Exception {
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!A:A")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null) return;

        // 해당 title 행 찾기
        int startRow = -1;
        int endRow = -1;
        for (int i = 0; i < values.size(); i++) {
            if (!values.get(i).isEmpty() && values.get(i).get(0).toString().equals(title)) {
                startRow = i;
            }
            // 다음 ## 제목 찾기 (끝 범위)
            if (startRow != -1 && i > startRow && !values.get(i).isEmpty()
                    && values.get(i).get(0).toString().startsWith("##")) {
                endRow = i - 1;
                break;
            }
        }
        if (startRow == -1) return; // 없으면 그냥 리턴
        if (endRow == -1) endRow = values.size() - 1;

        // 빈 줄도 포함해서 삭제 (startRow 위 빈줄 포함)
        int deleteStart = startRow > 0 ? startRow - 1 : startRow;

        DeleteDimensionRequest deleteRequest = new DeleteDimensionRequest()
                .setRange(new DimensionRange()
                        .setSheetId(getSheetId(service, spreadsheetId, sheetName))
                        .setDimension("ROWS")
                        .setStartIndex(deleteStart)
                        .setEndIndex(endRow + 1));

        service.spreadsheets().batchUpdate(spreadsheetId,
                        new BatchUpdateSpreadsheetRequest().setRequests(
                                Collections.singletonList(new Request().setDeleteDimension(deleteRequest))))
                .execute();
    }

    public static List<List<Object>> createStatsHeader(String title) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.asList("", "", "", "", "", "", "", ""));
        rows.add(Arrays.asList(title, "", "", "", "", "", "", ""));
        rows.add(Arrays.asList("이름", "운동 달성", "식단 달성", "둘다 달성", "달성률", "순위", "", ""));
        return rows;
    }

    // ==================== 통계 계산 ====================
    public static Map<String, int[]> calculateStats(Sheets service, String spreadsheetId,
                                                    List<String> members, LocalDate startDate, LocalDate endDate) throws Exception {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, "원본기록!A:E")
                .execute();

        List<List<Object>> allRows = response.getValues();
        if (allRows == null) allRows = new ArrayList<>();

        Map<String, int[]> stats = new LinkedHashMap<>();
        for (String member : members) stats.put(member, new int[]{0, 0, 0});

        for (List<Object> row : allRows) {
            if (row.size() < 5) continue;
            String dateStr = row.get(0).toString();
            String name = row.get(1).toString();
            boolean exercise = "✅".equals(row.get(2).toString());
            boolean diet = "✅".equals(row.get(3).toString());

            try {
                LocalDate date = LocalDate.parse(dateStr, fmt);
                if (!date.isBefore(startDate) && !date.isAfter(endDate) && stats.containsKey(name)) {
                    if (exercise) stats.get(name)[0]++;
                    if (diet) stats.get(name)[1]++;
                    if (exercise && diet) stats.get(name)[2]++;
                }
            } catch (Exception ignored) {}
        }
        return stats;
    }
    public static List<List<Object>> buildStatsRows(List<String> members, Map<String, int[]> stats, int totalDays, String title, String mvpLabel) {
        List<String[]> resultRows = new ArrayList<>();
        for (String member : members) {
            int[] s = stats.get(member);
            double rate = (s[2] / (double) totalDays) * 100;
            resultRows.add(new String[]{
                    member,
                    s[0] + "/" + totalDays + "일",
                    s[1] + "/" + totalDays + "일",
                    s[2] + "/" + totalDays + "일",
                    String.format("%.0f%%", rate)
            });
        }

        resultRows.sort((a, b) -> Integer.parseInt(b[4].replace("%", "")) - Integer.parseInt(a[4].replace("%", "")));

        // 공동 1등 처리
        String topRate = resultRows.get(0)[4];
        List<String> mvps = new ArrayList<>();
        for (String[] r : resultRows) {
            if (r[4].equals(topRate)) mvps.add(r[0]);
            else break;
        }
        String mvpText = String.join(", ", mvps);

        List<List<Object>> insertRows = createStatsHeader(title);

        // 공동 순위 처리
        int rank = 1;
        for (int i = 0; i < resultRows.size(); i++) {
            if (i > 0) {
                int prevRate = Integer.parseInt(resultRows.get(i-1)[4].replace("%", ""));
                int currRate = Integer.parseInt(resultRows.get(i)[4].replace("%", ""));
                if (currRate < prevRate) rank = i + 1;
            }
            String[] r = resultRows.get(i);
            List<Object> row = new ArrayList<>(Arrays.asList(r[0], r[1], r[2], r[3], r[4], rank + "위", "", ""));
            if (i == 0) row.set(7, mvpLabel + mvpText + " (" + topRate + ")");
            insertRows.add(row);
        }
        return insertRows;
    }
}