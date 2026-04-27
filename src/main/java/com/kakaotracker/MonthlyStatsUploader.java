package com.kakaotracker;

import com.google.api.services.sheets.v4.Sheets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class MonthlyStatsUploader {

    private static final Logger logger = LoggerFactory.getLogger(MonthlyStatsUploader.class);
    private static final String MONTHLY_SHEET = "월간통계";

    public void uploadMonthlyStats() {
        try {
            Sheets service = SheetsService.getService();
            String spreadsheetId = ConfigLoader.get("spreadsheet.id");
            List<String> members = SheetsService.loadMembers();

            LocalDate firstDay = LocalDate.parse(ConfigLoader.get("monthly.start.date"));
            LocalDate lastDay = LocalDate.parse(ConfigLoader.get("monthly.end.date"));
            int totalDays = (int) (lastDay.toEpochDay() - firstDay.toEpochDay()) + 1;

            // 전체 기간 통계
            Map<String, int[]> totalStats = SheetsService.calculateStats(service, spreadsheetId, members, firstDay, lastDay);

            // 주차별 치팅 보너스 계산
            Map<String, Integer> cheatBonus = new LinkedHashMap<>();
            for (String member : members) cheatBonus.put(member, 0);

            LocalDate weekStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            while (!weekStart.isAfter(lastDay)) {
                LocalDate weekEnd = weekStart.plusDays(6);
                // 실제 기간과 겹치는 부분만
                LocalDate effectiveStart = weekStart.isBefore(firstDay) ? firstDay : weekStart;
                LocalDate effectiveEnd = weekEnd.isAfter(lastDay) ? lastDay : weekEnd;

                Map<String, int[]> weekStats = SheetsService.calculateStats(service, spreadsheetId, members, effectiveStart, effectiveEnd);

                for (String member : members) {
                    int weekCheat = Math.min(weekStats.get(member)[3], 1); // 주당 최대 1회
                    cheatBonus.put(member, cheatBonus.get(member) + weekCheat);
                }
                weekStart = weekStart.plusWeeks(1);
            }

            // 달성률 계산
            List<String[]> resultRows = new ArrayList<>();
            for (String member : members) {
                int[] s = totalStats.get(member);
                int bonus = cheatBonus.get(member);
                double rate = (Math.min(s[2] + bonus, totalDays) / (double) totalDays) * 100;
                int totalCheat = s[3];
                String cheatStatus = totalCheat == 0 ? "미사용" : totalCheat == 1 ? "1회" : totalCheat + "회";

                resultRows.add(new String[]{
                        member,
                        s[0] + "/" + totalDays + "일",
                        s[1] + "/" + totalDays + "일",
                        s[2] + "/" + totalDays + "일",
                        cheatStatus,
                        String.format("%.0f%%", rate)
                });
            }

            resultRows.sort((a, b) -> Integer.parseInt(b[5].replace("%", "")) - Integer.parseInt(a[5].replace("%", "")));

            // 공동 1등 처리
            String topRate = resultRows.get(0)[5];
            List<String> mvps = new ArrayList<>();
            for (String[] r : resultRows) {
                if (r[5].equals(topRate)) mvps.add(r[0]);
                else break;
            }
            String mvpText = String.join(", ", mvps);

            String title = String.format("## %d.%02d.%02d ~ %d.%02d.%02d (%d일)",
                    firstDay.getYear() % 100, firstDay.getMonthValue(), firstDay.getDayOfMonth(),
                    lastDay.getYear() % 100, lastDay.getMonthValue(), lastDay.getDayOfMonth(),
                    totalDays);

            List<List<Object>> insertRows = SheetsService.createStatsHeader(title, mvpText, topRate, "🏆 MVP: ");

            int rank = 1;
            for (int i = 0; i < resultRows.size(); i++) {
                if (i > 0) {
                    int prevRate = Integer.parseInt(resultRows.get(i-1)[5].replace("%", ""));
                    int currRate = Integer.parseInt(resultRows.get(i)[5].replace("%", ""));
                    if (currRate < prevRate) rank = i + 1;
                }
                String[] r = resultRows.get(i);
                List<Object> row = new ArrayList<>(Arrays.asList(r[0], r[1], r[2], r[3], r[4], r[5], rank + "위", "", ""));
                insertRows.add(row);
            }

            insertRows.add(Arrays.asList("", "", "", "", "", "", "", "", ""));

            SheetsService.ensureSheetTitle(service, spreadsheetId, MONTHLY_SHEET, "📊 월간 통계");
            SheetsService.deleteExistingStats(service, spreadsheetId, MONTHLY_SHEET, title);
            SheetsService.insertRowsAtTop(service, spreadsheetId, MONTHLY_SHEET, insertRows);

            logger.info("월간 통계 업로드 완료 - {}, MVP: {}", title, mvpText);

        } catch (Exception e) {
            logger.error("월간 통계 업로드 실패: {}", e.getMessage(), e);
        }
    }
}