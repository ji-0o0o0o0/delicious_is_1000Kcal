# 🔧 상세 설정 가이드

## 1. Tesseract 설치

1. 아래 링크에서 설치 파일 다운로드
   - https://digi.bib.uni-mannheim.de/tesseract/
   - `tesseract-ocr-w64-setup-5.4.0.20240606.exe` 다운로드
2. 설치 진행
   - `Install for anyone using this computer` 선택
   - `Additional language data (download)` 펼쳐서 **Korean** 체크
3. 설치 완료 후 `kor.traineddata` 파일 복사
   - 기본 설치 경로: `C:\Program Files\Tesseract-OCR\tessdata\kor.traineddata`
   - 복사 대상: `src/main/resources/tessdata/kor.traineddata`

---

## 2. Google Sheets API 설정

### 2-1. Google Cloud 프로젝트 생성
1. [console.cloud.google.com](https://console.cloud.google.com) 접속
2. 상단 프로젝트 선택 → **새 프로젝트** 생성

### 2-2. API 활성화
1. 좌측 메뉴 → **API 및 서비스** → **라이브러리**
2. `Google Sheets API` 검색 → **사용 설정**
3. `Google Drive API` 동일하게 **사용 설정**

### 2-3. 서비스 계정 생성
1. **API 및 서비스** → **사용자 인증 정보**
2. **사용자 인증 정보 만들기** → **서비스 계정**
3. 서비스 계정 이름 입력 (예: `kakao-tracker`) → **만들고 계속하기**
4. 권한, 액세스 설정은 스킵 → **완료**

### 2-4. JSON 키 다운로드
1. 생성된 서비스 계정 클릭
2. **키** 탭 → **키 추가** → **새 키 만들기** → **JSON** 선택
3. JSON 파일 다운로드
4. `src/main/resources/` 폴더에 복사

### 2-5. 구글 시트 생성 및 공유
1. 구글 시트 새로 생성
2. 시트 하단 탭을 아래 3개로 생성
   - `원본기록`
   - `주간통계`
   - `월간통계`
3. 우상단 **공유** 클릭
4. JSON 파일 안의 `client_email` 값을 복사해서 편집자 권한으로 공유

---

## 3. config.properties 설정

`src/main/resources/config.properties` 파일 생성 후 아래 내용 입력:

```properties
spreadsheet.id=구글시트URL에서복사한ID
credentials.file=다운로드받은서비스계정키파일명.json
image.path.prefix=C:/delicious_1000/images/
scheduler.hour=9
```

- `spreadsheet.id`: 구글 시트 URL에서 `/d/` 뒤 `/edit` 앞의 긴 문자열
- `credentials.file`: JSON 파일명 (경로 없이 파일명만)
- `image.path.prefix`: 캡처 이미지 저장 폴더 경로
- `scheduler.hour`: 스케줄러 실행 시간 (24시간 기준, 예: 9 = 오전 9시)

---

## 4. members.txt 설정

`src/main/resources/members.txt` 파일 생성 후 멤버 이름을 한 줄에 한 명씩 입력:

```
마이클
제이슨
홍길동
...
```

가나다순 정렬 권장 (구글 시트에 이 순서대로 입력됩니다)

---

## 5. 이미지 저장 폴더 생성

`config.properties`의 `image.path.prefix`에 설정한 경로로 폴더 생성:

```
C:\delicious_1000\images\
```

---

## 6. Windows 작업 스케줄러 등록

1. `Win + R` → `taskschd.msc` 입력 → 확인
2. 오른쪽 **기본 작업 만들기** 클릭
3. 이름: `KakaoTracker` 입력
4. 트리거: **매일** 선택 → 시작 시간 `오전 9:00`
5. 동작: **프로그램 시작** 선택
   - 프로그램: `C:\경로\jdk\bin\java.exe`
   - 인수: `-jar "C:\프로젝트경로\build\libs\delicious_is_1000Kcal-1.0-SNAPSHOT.jar" scheduler`
   - 시작 위치: `C:\프로젝트경로`
6. 마침 클릭

> **주의**: PC가 켜져 있어야 스케줄러가 실행됩니다.
> 노트북 덮개를 닫고 사용하는 경우 **제어판 → 전원 옵션 → 덮개를 닫을 때 → 아무것도 안 함** 으로 설정하세요.
