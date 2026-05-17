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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
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
    // ==================== 제외기간 ====================
    public static void ensureExclusionHeader(Sheets service, String spreadsheetId) throws Exception {
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, "제외기간!A1:E2")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values != null && values.size() >= 2) return;

        List<List<Object>> header = new ArrayList<>();
        header.add(Arrays.asList("📋 제외기간", "", "", "", ""));
        header.add(Arrays.asList("이름", "시작날짜", "종료날짜", "사유", "표시"));

        ValueRange body = new ValueRange().setValues(header);
        service.spreadsheets().values()
                .update(spreadsheetId, "제외기간!A1", body)
                .setValueInputOption("RAW")
                .execute();
    }
    public static void addExclusionIfAbsent(Sheets service, String spreadsheetId, String name, LocalDate date, String reason, String display) throws Exception {
        // 해당 주 월~일 계산
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        String startStr = weekStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String endStr = weekEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 이미 등록되어 있는지 체크
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, "제외기간!A:E")
                .execute();
        List<List<Object>> values = response.getValues();
        if (values != null) {
            for (List<Object> row : values) {
                if (row.size() >= 3 && row.get(0).equals(name)
                        && row.get(1).equals(startStr)) {
                    return; // 이미 있으면 추가 안 함
                }
            }
        }

        // 추가
        List<Object> newRow = Arrays.asList(name, startStr, endStr, reason, display);
        int nextRow = values == null ? 3 : values.size() + 1;
        ValueRange body = new ValueRange().setValues(Collections.singletonList(newRow));
        service.spreadsheets().values()
                .update(spreadsheetId, "제외기간!A" + nextRow, body)
                .setValueInputOption("RAW")
                .execute();

        logger.info("제외기간 등록 - {}, {}~{}, {}", name, startStr, endStr, reason);
    }
    public static Map<String, List<String>> getExclusionReasons(Sheets service, String spreadsheetId, LocalDate startDate, LocalDate endDate) throws Exception {
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, "제외기간!A:E")
                .execute();

        List<List<Object>> values = response.getValues();
        Map<String, List<String>> result = new LinkedHashMap<>(); // 이름 -> 사유 목록

        if (values == null) return result;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (List<Object> row : values) {
            if (row.size() < 5) continue;
            String name = row.get(0).toString();
            try {
                LocalDate excStart = LocalDate.parse(row.get(1).toString(), fmt);
                LocalDate excEnd = LocalDate.parse(row.get(2).toString(), fmt);
                String display = row.get(4).toString();

                // 해당 기간이 startDate~endDate 와 겹치는지 체크
                if (!excEnd.isBefore(startDate) && !excStart.isAfter(endDate)) {
                    result.computeIfAbsent(name, k -> new ArrayList<>()).add(display);
                }
            } catch (Exception ignored) {}
        }
        return result;
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

    public static List<List<Object>> createStatsHeader(String title, String mvpText, String topRate, String mvpLabel) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.asList("", "", "", "", "", "", "", "", ""));
        rows.add(Arrays.asList(title, "","", mvpLabel + mvpText + " (" + topRate + ")", "", "", "", "", ""));
        rows.add(Arrays.asList("이름", "운동 달성", "식단 달성", "둘다 달성", "치팅여부", "달성률", "순위", "비고", ""));
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
        for (String member : members) stats.put(member, new int[]{0, 0, 0, 0});

        for (List<Object> row : allRows) {
            if (row.size() < 5) continue;
            String dateStr = row.get(0).toString();
            String name = row.get(1).toString();
            boolean exercise = "✅".equals(row.get(2).toString());
            boolean diet = "✅".equals(row.get(3).toString());
            boolean cheat = "😋".equals(row.get(2).toString());
            boolean injury = "🤕".equals(row.get(2).toString());

            try {
                LocalDate date = LocalDate.parse(dateStr, fmt);
                if (!date.isBefore(startDate) && !date.isAfter(endDate) && stats.containsKey(name)) {
                    if (cheat) {
                        stats.get(name)[3]++;
                    } else if (injury) {
                        if (diet) stats.get(name)[1]++;
                    }else {
                        if (exercise) stats.get(name)[0]++;
                        if (diet) stats.get(name)[1]++;
                        if (exercise && diet) stats.get(name)[2]++;
                    }
                }
            } catch (Exception ignored) {}
        }
        return stats;
    }
    public static List<List<Object>> buildStatsRows(List<String> members, Map<String, int[]> stats, int totalDays, String title, String mvpLabel, Map<String, List<String>> exclusions) {
        List<String[]> resultRows = new ArrayList<>();
        for (String member : members) {
            int[] s = stats.get(member);
            int cheatCount = Math.min(s[3], 1);

            List<String> reasons = exclusions.getOrDefault(member, new ArrayList<>());
            boolean hasExclusion = !reasons.isEmpty();
            String reasonText = String.join(", ", reasons);

            int effectiveDays = totalDays - cheatCount;
            String cheatStatus = hasExclusion ? "-" : s[3] == 0 ? "미사용" : s[3] == 1 ? "사용" : "초과";

            double rate = hasExclusion ? -1 :
                    effectiveDays == 0 ? 0 : (Math.min(s[2] + cheatCount, totalDays) / (double) totalDays) * 100;

            resultRows.add(new String[]{
                    member,
                    s[0] + "/" + totalDays + "일",
                    s[1] + "/" + totalDays + "일",
                    hasExclusion ? "-" : s[2] + "/" + totalDays + "일",
                    cheatStatus,
                    hasExclusion ? "-" : String.format("%.0f%%", rate),
                    reasonText
            });

        }

        resultRows.sort((a, b) -> {
            if (a[5].equals("-")) return 1;
            if (b[5].equals("-")) return -1;
            return Integer.parseInt(b[5].replace("%", "")) - Integer.parseInt(a[5].replace("%", ""));
        });

        // 공동 1등 처리
        String topRate = resultRows.get(0)[5];
        String mvpText = getMvpText(resultRows, 5);

        List<List<Object>> insertRows = createStatsHeader(title,mvpText,topRate,mvpLabel);

        // 공동 순위 처리
        int rank = 1;
        for (int i = 0; i < resultRows.size(); i++) {
            if (i > 0) {
                String prevRate = resultRows.get(i-1)[5];
                String currRate = resultRows.get(i)[5];
                if (!currRate.equals("-") && !prevRate.equals("-") &&
                        Integer.parseInt(currRate.replace("%", "")) < Integer.parseInt(prevRate.replace("%", ""))) {
                    rank = i + 1;
                }
            }
            String[] r = resultRows.get(i);
            boolean memberHasInjury = r[5].equals("-");
            String rankStr = memberHasInjury ? "-" : rank + "위";
            List<Object> row = new ArrayList<>(Arrays.asList(r[0], r[1], r[2], r[3], r[4], r[5], rankStr, r[6], ""));
            insertRows.add(row);
        }
        insertRows.add(Arrays.asList("", "", "", "", "", "", "", "", ""));
        return insertRows;
    }
    public static String getMvpText(List<String[]> resultRows, int rateIndex) {
        String topRate = resultRows.get(0)[rateIndex];
        List<String> mvps = new ArrayList<>();
        for (String[] r : resultRows) {
            if (r[rateIndex].equals(topRate)) mvps.add(r[0]);
            else break;
        }
        return String.join(", ", mvps);
    }
}