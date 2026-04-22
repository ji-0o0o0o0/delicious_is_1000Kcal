package com.kakaotracker;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        tesseract.setLanguage("kor");

        String text="";
        try {
            text = tesseract.doOCR(new File(imagePath));
        }catch (TesseractException e){
            logger.error("OCR 실패:{}", e.getMessage());
            return new ArrayList<>();
        }

        logger.info("OCR 결과 : \n {}",text);
        return parseText(text,date,members);

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
                // 치팅 먼저 체크
                boolean hasCheat = line.contains("치팅") || line.contains("ㅊㅌ") ||line.contains("ㅅㄷ") || line.contains("😋");

                if (hasCheat) {
                    logger.info("파싱 완료 - 날짜: {}, 이름: {}, 치팅", date, currentName);
                    records.add(new CommentRecord(date, currentName, false, false, true));
                    currentName = null;
                    continue;
                }

                boolean bothFail = line.contains("운식실") || line.contains("식운실");
                boolean exerciseFail = bothFail || line.contains("운실") || line.contains("운동실패") || line.contains("운동 실패");
                boolean dietFail = bothFail || line.contains("식실") || line.contains("식단실패") || line.contains("식단 실패");

                boolean hasExercise = !exerciseFail && (line.contains("운") || line.contains("운동"));
                boolean hasDiet = !dietFail && (line.contains("식") || line.contains("식단"));

                if (hasExercise || hasDiet) {
                    logger.info("파싱 완료 - 날짜: {}, 이름: {}, 운동: {}, 식단: {}", date, currentName, hasExercise, hasDiet);
                    records.add(new CommentRecord(date, currentName, hasExercise, hasDiet, false));
                    currentName = null;
                }
            }
        }
        return records;

    }
}
