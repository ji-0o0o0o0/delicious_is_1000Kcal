package com.kakaotracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MainUI extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(MainUI.class);
    private JTextArea logArea;
    private JButton runButton;
    private JTextField dateField;
    private Scheduler globalScheduler;

    public MainUI(Scheduler scheduler) {
        this.globalScheduler = scheduler;
        setTitle("🥗 delicious_is_1000Kcal Tracker");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // 상단 입력 영역
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("날짜 (선택):"));
        dateField = new JTextField(10);
        dateField.setToolTipText("비우면 어제 날짜, 4자리(0422) 또는 6자리(260422)");
        topPanel.add(dateField);

        runButton = new JButton("▶ 실행");
        runButton.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        runButton.addActionListener(e -> onRun());
        topPanel.add(runButton);

        JButton logButton = new JButton("로그 보기");
        logButton.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        logButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File("logs/kakao-tracker.log"));
            } catch (Exception ex) {
                log("로그 파일을 열 수 없습니다: " + ex.getMessage());
            }
        });
        topPanel.add(logButton);

        JButton restartButton = new JButton("재시작");
        restartButton.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        restartButton.addActionListener(e -> {
            log("스케줄러 재시작...");
            if (globalScheduler != null) globalScheduler.stop();
            globalScheduler = new Scheduler();
            globalScheduler.start();
            log("스케줄러 재시작 완료!");
        });
        topPanel.add(restartButton);

        JButton exitButton = new JButton("종료");
        exitButton.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        exitButton.addActionListener(e -> {
            try {
                if (globalScheduler != null) globalScheduler.stop();
                String startupPath = System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\kakaotracker.bat";
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", startupPath);
                pb.start();
            } catch (Exception ex) {
                logger.error("재시작 실패: {}", ex.getMessage(), ex);
            }
            System.exit(0);
        });
        topPanel.add(exitButton);

        add(topPanel, BorderLayout.NORTH);

        // 로그 영역
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void onRun() {
        runButton.setEnabled(false);
        logArea.append("===== 실행 시작 =====\n");

        new Thread(() -> {
            try {
                String dateInput = dateField.getText().trim();
                String dateStr;

                if (dateInput.isEmpty()) {
                    dateStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyMMdd"));
                    log("날짜 미입력 - 어제 날짜 사용: " + dateStr);
                } else if (dateInput.length() == 4) {
                    String yearPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yy"));
                    dateStr = yearPrefix + dateInput;
                    log("4자리 입력 - 연도 자동 추가: " + dateStr);
                } else if (dateInput.length() == 6) {
                    dateStr = dateInput;
                } else {
                    log("날짜 형식 오류! 4자리(0422) 또는 6자리(260422)로 입력해주세요.");
                    return;
                }

                try {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    Transferable content = clipboard.getContents(null);

                    if (content != null && content.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        BufferedImage image = (BufferedImage) content.getTransferData(DataFlavor.imageFlavor);
                        String imagePath = ConfigLoader.get("image.path.prefix") + dateStr + ".png";
                        File imageFile = new File(imagePath);

                        if (imageFile.exists()) {
                            int choice = JOptionPane.showConfirmDialog(this,
                                    dateStr + ".png 파일이 이미 있어요. 덮어쓸까요?",
                                    "파일 존재",
                                    JOptionPane.YES_NO_OPTION);
                            if (choice != JOptionPane.YES_OPTION) {
                                log("취소됨");
                                return;
                            }
                        }

                        ImageIO.write(image, "png", imageFile);
                        log("이미지 저장 완료: " + imagePath);
                    } else {
                        log("클립보드에 이미지 없음 - 일반 실행");
                    }
                } catch (Exception ex) {
                    log("클립보드 접근 실패 - 일반 실행");
                }

                Scheduler scheduler = new Scheduler();
                scheduler.setLogCallback(this::log);
                scheduler.runNow();
                log("===== 실행 완료 =====");

            } catch (Exception ex) {
                log("오류: " + ex.getMessage());
                logger.error("UI 실행 오류: {}", ex.getMessage(), ex);
            } finally {
                SwingUtilities.invokeLater(() -> runButton.setEnabled(true));
            }
        }).start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void showUI(Scheduler scheduler) {
        SwingUtilities.invokeLater(() -> new MainUI(scheduler).setVisible(true));
    }
}