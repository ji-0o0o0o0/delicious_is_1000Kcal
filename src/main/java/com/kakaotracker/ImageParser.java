package com.kakaotracker;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ImageParser {

    private static final Logger logger = LoggerFactory.getLogger(ImageParser.class);
    private static List<String> loadMembers(){
        List<String> members = new ArrayList<>();
        try (InputStream is = ImageParser.class.getClassLoader().getResourceAsStream("members.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    members.add(line.trim());
                }
            }
        }catch(Exception e){
            logger.error("member.txt 읽기 실패: {}", e.getMessage());
        }
        return  members;
    }

    public List<CommentRecord> parse(String imagePath, String date){
        List<String> members = loadMembers();

        Tesseract tesseract = new Tesseract();
        String tessDataPath = getClass().getClassLoader().getResource("tessdata").getPath();
        if (tessDataPath.startsWith("/")) {
            tessDataPath = tessDataPath.substring(1);
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
                boolean exerciseFail = line.contains("운실") || line.contains("운동실패") || line.contains("운동 실패");
                boolean dietFail = line.contains("식실") || line.contains("식단실패") || line.contains("식단 실패");

                boolean hasExercise = !exerciseFail && (line.contains("운") || line.contains("운동"));
                boolean hasDiet = !dietFail && (line.contains("식") || line.contains("식단"));

                if (hasExercise || hasDiet) {
                    logger.info("파싱 완료 - 날짜: {}, 이름: {}, 운동: {}, 식단: {}", date, currentName, hasExercise, hasDiet);
                    records.add(new CommentRecord(date, currentName, hasExercise, hasDiet));
                    currentName = null;
                }
            }
        }
        return records;

    }
}
