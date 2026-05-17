# 🥗 delicious_is_1000Kcal Tracker

> OCR 기반 카카오톡 커뮤니티 데이터 자동 수집 및 분석 시스템

운동/식단 인증 모임의 수동 기록 업무를 자동화하기 위해 개발된 데이터 파이프라인 프로젝트입니다.
카카오톡의 텍스트 데이터를 OCR로 추출하고, Google Sheets API를 통해 실시간 통계 및 주/월간 대시보드를 구축했습니다.

---

## 🛠 기술 스택

- Java 17
- Tesseract OCR (tess4j 5.8.0)
- Google Sheets API
- Logback
- Java Swing (UI)

---

## 🏗 System Architecture

```text
[Mobile] KakaoTalk Capture
    ↓ (Google Drive Sync)
[PC] Input Directory (YYMMDD.jpg)
    ↓ (Java App - Swing UI / Scheduled Task)
[OCR Engine] Tesseract (Extract Text)
    ↓ (Keyword Parsing & Validation)
[Data Store] Google Sheets (Raw Data)
    ↓ (Auto-Calculation Logic)
[Dashboard] Weekly/Monthly Statistics
```

---

## 📂 구글 시트 구조

### 탭 구조

| 탭 이름 | 설명 |
|---|---|
| `원본기록` | 매일 멤버별 운동/식단 완료 여부 기록 |
| `이번주현황` | 매일 업데이트, 이번 주 진행 상황 실시간 확인 |
| `주간통계` | 매주 월요일 자동 생성, 지난주 확정 결과 누적 |
| `월간통계` | 종료일 다음날 자동 생성, 주차별 치팅 보너스 반영 |

---

### 원본기록

| 열 | 설명 |
|---|---|
| A | 날짜 |
| B | 이름 |
| C | 운동 (✅/❌/😋) |
| D | 식단 (✅/❌/😋) |
| E | 완료여부 (수식 자동 계산) |
| F | 수정여부 (Y: 수정됨 / D: 재실행 완료 / P: 무시) |

#### OCR 오류 수정 방법

1. 구글 시트 원본기록에서 잘못된 데이터 직접 수정
2. 수정여부(F열)에 `Y` 입력
3. 스케줄러가 감지 후 자동으로 통계 재실행
4. 완료 후 `D` 로 자동 변경


<img width="1130" height="698" alt="원본기록" src="https://github.com/user-attachments/assets/8c5b67ed-8073-4046-8bb2-58481331cb85" />

---

### 이번주현황

<img width="981" height="703" alt="이번주현황" src="https://github.com/user-attachments/assets/3ffcea3f-32c7-4bbf-9065-b29e6113dce5" />

---

### 주간통계

<img width="884" height="705" alt="주간통계" src="https://github.com/user-attachments/assets/6e6b19a0-f84d-471b-8f49-7dd68e4a6ad8" />

---

### 월간통계

<img width="887" height="660" alt="월간통계" src="https://github.com/user-attachments/assets/06af1682-87e3-4114-87a4-bda9b0859cfc" />

---
### 제외기간

<img width="800" height="780" alt="제외기간" src="https://github.com/user-attachments/assets/f0e57f27-b303-4978-ad57-05e0069067f1" />

---

## 🚀 사용 방법

### 자동 실행 흐름

```text
PC 켜짐 (시작프로그램 자동 실행)
    ↓
UI 창 + 스케줄러 백그라운드 동시 실행
    ↓
구글 드라이브 동기화 (핸드폰에서 올린 이미지 자동 동기화)
    ↓
매 정시마다 스케줄러 실행
    ↓
수정여부 Y 체크 → 있으면 통계 재실행
최근 7일치 이미지 체크 → 미업로드 날짜 자동 업로드
처리 완료된 이미지 → done/ 폴더로 자동 이동
이번주 현황 탭 자동 업데이트
    ↓
매주 월요일: 주간 통계 자동 생성
종료일 다음날: 월간 통계 자동 생성
```

### UI 수동 실행

1. 카카오톡 공지 댓글 전체 캡처
2. UI 창에서 날짜 입력 (비우면 어제 날짜 자동)
3. **실행** 버튼 클릭
4. 클립보드 이미지 자동 저장 + OCR + 업로드

### UI 버튼 설명

| 버튼 | 설명 |
|---|---|
| 실행 | 클립보드 이미지 저장 + OCR 실행 |
| 로그 보기 | 로그 파일 열기 |
| 설정 열기 | config.properties 파일 열기 |
| 이미지 폴더 | 이미지 저장 폴더 열기 |
| 재시작 | config.properties 변경 후 스케줄러 재시작 |
| 종료 | 스케줄러 종료 후 앱 재실행 (새 jar 적용 시 사용) |


<img width="500" height="300" alt="" src="https://github.com/user-attachments/assets/b7c02fd6-b917-4ada-9633-863c56cca640" />

---

## 🧩 파싱 규칙

### 완료 키워드

| 키워드 | 판단 |
|---|---|
| `운`, `운동`, `운성`, `운동성공`, `운동 성공` 포함 | 운동 완료 ✅ |
| `식`, `식단`, `식성`, `식단성공`, `식단 성공` 포함 | 식단 완료 ✅ |

### 실패 키워드 (완료보다 우선 적용)

| 키워드 | 판단 |
|---|---|
| `운실`, `운동실패`, `운동 실패` 포함 | 운동 실패 ❌ |
| `식실`, `식단실패`, `식단 실패` 포함 | 식단 실패 ❌ |
| `운식실`, `식운실`, `운동식단실패`, `식단운동실패`, `운동 식단 실패`, `식단 운동 실패` 포함 | 둘 다 실패 ❌ |
| `실패` 만 있고 성공/완료 키워드 없음 | 둘 다 실패 ❌ |

### 치팅 키워드

| 키워드 | 판단 |
|---|---|
| `치팅`, `ㅊㅌ`, `ㅅㄷ`, 😋 포함 | 치팅 😋 |

> 치팅은 주 1회 면제권으로, 달성률 계산 시 분자 +1 처리됩니다.

### 부상 키워드

| 키워드 | 판단 |
|---|---|
| `부상`, 🤕 포함 | 부상 🤕 |

> 부상이 있는 주는 달성률/순위에서 제외되며 제외기간 시트에 자동 등록됩니다.


### 데이터 정합성 보장 규칙

- **실패 우선 원칙**: 완료 키워드가 있더라도 실패 키워드가 포함되면 ❌로 처리
- **수동 정정 우선**: 시스템 데이터보다 사용자가 시트에서 직접 수정한 데이터(수정여부 `Y`)를 최우선으로 반영

---

## 📚 상세 문서

- 초기 설정 → [SETUP.md](SETUP.md)
- 기술적 고민 및 트러블슈팅 → [ENGINEERING.md](ENGINEERING.md)
- 변경 이력 → [CHANGELOG.md](CHANGELOG.md)
