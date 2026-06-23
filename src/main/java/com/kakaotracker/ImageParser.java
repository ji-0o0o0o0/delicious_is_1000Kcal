package com.kakaotracker;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ImageParser {

    private static final Logger logger = LoggerFactory.getLogger(ImageParser.class);

    public List<CommentRecord> parse(String imagePath, String date) {
        List<String> members = SheetsService.loadMembers();

        Tesseract tesseract = new Tesseract();
        String tessDataPath;
        try {
            File jarFile = new File(ImageParser.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            if (jarFile.getName().endsWith(".jar")) {
                tessDataPath = jarFile.getParent() + "/tessdata";
            } else {
                tessDataPath = getClass().getClassLoader().getResource("tessdata").getPath();
                if (tessDataPath.startsWith("/")) {
                    tessDataPath = tessDataPath.substring(1);
                }
            }
        } catch (Exception e) {
            logger.error("tessdata 경로 설정 실패: {}", e.getMessage(), e);
            return new ArrayList<>();
        }

        if (tessDataPath.startsWith("/")) {
            tessDataPath = tessDataPath.substring(1);
        }
        logger.info("tessdata 경로: {}", tessDataPath);
        File tessDataDir = new File(tessDataPath);
        if (!tessDataDir.exists()) {
            logger.error("tessdata 폴더를 찾을 수 없습니다: {}", tessDataPath);
            return new ArrayList<>();
        }
        tesseract.setDatapath(tessDataPath);
        tesseract.setDatapath(tessDataPath);

// 1차: kor
        tesseract.setLanguage("kor");
        String text1 = "";
        try {
            text1 = tesseract.doOCR(new File(imagePath));
        } catch (TesseractException e) {
            logger.error("OCR 실패:{}", e.getMessage());
            return new ArrayList<>();
        }
        logger.info("OCR 결과 : \n {}", text1);
        List<CommentRecord> records1 = parseText(text1, date, members);

        // kor에서 못 찾은 멤버 확인
        List<String> foundMembers = records1.stream()
                .map(CommentRecord::getName)
                .toList();
        List<String> missingMembers = members.stream()
                .filter(m -> !foundMembers.contains(m))
                .collect(Collectors.toList());

        // 2차:kor+eng (못 찾은 멤버가 있을 때만)
        if (!missingMembers.isEmpty()) {
            tesseract.setLanguage("kor+eng");
            String text2 = "";
            try {
                text2 = tesseract.doOCR(new File(imagePath));
            } catch (TesseractException e) {
                logger.error("OCR 2차 실패:{}", e.getMessage());
            }
            logger.info("OCR 2차 결과 : \n {}", text2);
            List<CommentRecord> records2 = parseText(text2, date, missingMembers);
            records1.addAll(records2);
        }

        return records1;

    }

    private List<CommentRecord> parseText(String text, String date, List<String> members) {
        List<CommentRecord> records = new ArrayList<>();
        String[] lines = text.split("\n");

        String currentName = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            for (String member : members) {
                if (line.contains(member)) {
                    currentName = member;
                    break;
                }
            }

            if (currentName != null) {
                boolean hasCheat = line.contains("치팅") || line.contains("ㅊㅌ") ||line.contains("ㅅㄷ") || line.contains("😋");

                if (hasCheat) {
                    records.add(new CommentRecord(date, currentName, false, false, true,false));
                    currentName = null;
                    continue;
                }


                boolean bothFail = line.contains("운식실") || line.contains("식운실")
                        || line.contains("운식 실") || line.contains("식운 실")
                        || line.contains("운 식 실") || line.contains("식 운 실")
                        || line.contains("운동식단실패") || line.contains("식단운동실패")
                        || line.contains("운동식단 실패") || line.contains("식단운동 실패")
                        || line.contains("운동 식단 실패") || line.contains("식단 운동 실패");
                boolean exerciseFail = bothFail || line.contains("운실")|| line.contains("운 실") || line.contains("운동실패") || line.contains("운동 실패")
                        || line.contains("운 실패");
                boolean dietFail = bothFail || line.contains("식실") || line.contains("식 실") || line.contains("식단실패") || line.contains("식단 실패")
                        || line.contains("식 실패");

                if (line.contains("실패") && !line.contains("성공") && !line.contains("완료")) {
                    if (!exerciseFail && !dietFail && !bothFail) {
                        exerciseFail = true;
                        dietFail = true;
                    }
                }
                boolean hasExercise = !exerciseFail && (line.contains("운") || line.contains("운동"));
                boolean hasDiet = !dietFail && (line.contains("식") || line.contains("식단"));

                boolean hasInjury = line.contains("부상");

                if (hasInjury) {
                    records.add(new CommentRecord(date, currentName, false, hasDiet, false, true));
                    currentName = null;
                    continue;
                }
                if (exerciseFail && dietFail) {
                    records.add(new CommentRecord(date, currentName, false, false, false, false));
                    currentName = null;
                    continue;
                }

                if (hasExercise || hasDiet) {
                    records.add(new CommentRecord(date, currentName, hasExercise, hasDiet, false,false));
                    currentName = null;
                }
            }
        }
        return records;

    }
}
