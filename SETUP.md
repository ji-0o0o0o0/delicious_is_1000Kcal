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

> **tessdata 경로 결정 과정**
> tessdata를 jar 내부에 포함할 경우 압축된 상태라 Tesseract가 직접 읽지 못하는 문제가 있었습니다.
> jar 빌드 시 `build/libs/tessdata/` 에 자동 복사하고, IntelliJ 실행 시에는 `src/main/resources/tessdata` 를 사용하도록 환경별로 자동 감지하는 방식을 선택했습니다.

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
2. 시트 하단 탭을 아래 4개로 생성
    - `원본기록`
    - `이번주현황`
    - `주간통계`
    - `월간통계`
3. 우상단 **공유** 클릭
4. JSON 파일 안의 `client_email` 값을 복사해서 편집자 권한으로 공유

---

## 3. config.properties 설정

> **외부 파일로 분리한 이유**
> config.properties를 jar 내부에만 두면 스케줄러 간격 등 설정을 바꿀 때마다 jar를 다시 빌드해야 했습니다.
> jar와 같은 위치에 외부 config.properties를 두면 재빌드 없이 설정을 변경할 수 있습니다.
> jar 빌드 시 `build/libs/` 에 자동 복사됩니다.

`src/main/resources/config.properties` 파일 생성 후 아래 내용 입력:

```properties
spreadsheet.id=구글시트URL에서복사한ID
credentials.file=다운로드받은서비스계정키파일명.json
image.path.prefix=C:/Users/사용자명/Google Drive/delicious_1000/images/
scheduler.interval.hours=1
```

- `spreadsheet.id`: 구글 시트 URL에서 `/d/` 뒤 `/edit` 앞의 긴 문자열
- `credentials.file`: JSON 파일명 (경로 없이 파일명만)
- `image.path.prefix`: 캡처 이미지 저장 폴더 경로 (경로 구분자는 `/` 사용)
- `scheduler.interval.hours`: 스케줄러 실행 간격 (시간 단위, 매 정시 기준으로 실행)

> **주의**: Windows 경로는 `\` 대신 `/` 를 사용해야 합니다. `\t` 등이 이스케이프 문자로 처리될 수 있습니다.

---

## 4. members.txt 설정

> **외부 파일로 분리한 이유**
> 멤버가 추가되거나 변경될 때 코드 수정 없이 파일만 수정하면 되도록 분리했습니다.
> jar 빌드 시 `build/libs/` 에 자동 복사됩니다.

`src/main/resources/members.txt` 파일 생성 후 멤버 이름을 한 줄에 한 명씩 입력:

```
마이클
제이슨
홍길동
...
```

가나다순 정렬 권장 (구글 시트에 이 순서대로 입력됩니다)

---

## 5. 이미지 동기화 설정 (구글 드라이브)

> **이미지 동기화 방식 결정 과정**
> 핸드폰으로 캡처하면 더 선명하고 편리하지만 PC로 옮기는 과정이 번거로웠습니다.
> 구글 드라이브를 활용하면 핸드폰에서 업로드 후 PC에 자동 동기화되어 별도 전송 과정이 필요 없습니다.

1. [drive.google.com/drive/downloads](https://drive.google.com/drive/downloads) 에서 구글 드라이브 PC 앱 설치
2. 구글 드라이브에 `delicious_1000/images/` 폴더 생성
3. `config.properties` 의 `image.path.prefix` 를 구글 드라이브 동기화 폴더 경로로 설정
4. 핸드폰에서 캡처 후 구글 드라이브 앱으로 `260417.jpg` 형식으로 저장
5. 처리 완료된 이미지는 자동으로 `images/done/` 폴더로 이동됩니다

---

## 6. Windows 작업 스케줄러 등록

> **스케줄러 방식 결정 과정**
> Java 내부 스케줄러만 사용하면 PC 재시작 시 매번 수동으로 jar를 실행해야 했습니다.
> Windows 작업 스케줄러로 PC 시작 시 자동 실행하고, Java 내부 스케줄러가 매 정시마다 동작하는 방식을 선택했습니다.

1. `Win + R` → `taskschd.msc` 입력 → 확인
2. 오른쪽 **기본 작업 만들기** 클릭
3. 이름: `KakaoTracker` 입력
4. 트리거: **시작할 때** 선택 (PC 켜질 때 자동 실행)
5. 동작: **프로그램 시작** 선택
    - 프로그램: `C:\경로\jdk\bin\javaw.exe`
    - 인수: `-jar "C:\프로젝트경로\build\libs\delicious_is_1000Kcal-1.0-SNAPSHOT.jar" scheduler`
    - 시작 위치: `C:\프로젝트경로\build\libs`
6. 마침 클릭

> **주의**: 노트북 덮개를 닫고 사용하는 경우 **제어판 → 전원 옵션 → 덮개를 닫을 때 → 아무것도 안 함** 으로 설정하세요.
> PC가 켜져 있으면 Java 내부 스케줄러가 `config.properties` 에 설정된 간격으로 자동으로 실행합니다.