# Hospital-Appointment

환자 등록부터 예약, 외래 접수, 진료기록 작성, 검사, 처치, 수납까지 관리하는 병원 업무 관리 프로그램입니다.
WPF 데스크톱 클라이언트와 Spring Boot 서버를 분리했습니다.

# 프로젝트 정보

### 제작 기간

> 2026.9.8 ~ 2024.10.5

# 사용 기술

- C#, .NET 10, WPF
- CommunityToolkit.Mvvm (MVVM, 데이터 바인딩, 커맨드)
- Java 21, Spring Boot
- Spring Security (세션 인증 및 역할별 권한)
- Spring Data JPA, Hibernate
- PostgreSQL
- Jakarta Validation, Lombok
- Apache POI (상병마스터 Excel 읽기)
- Docker

# ERD

<details>
  <summary>ERD</summary>

<img width="1078" height="806" alt="image" src="https://github.com/user-attachments/assets/629ee93e-913a-4c17-8967-a14db8e3ec0c" />

</details>

| 테이블 | 역할 |
|---|---|
| `members` | 의사, 간호사, 관리자 계정 |
| `patients` | 환자 기본 정보 |
| `appointments` | 예약 및 방문 접수 연결 |
| `encounters` | 방문별 진료 상태와 SOAP 기록 |
| `diagnosis_codes`, `diagnosis_terms` | 상병 코드 및 검색용 명칭 |
| `encounter_diagnoses` | 진료별 진단과 주진단 구분 |
| `medications` | 약품 기준 정보 |
| `encounter_prescriptions` | 진료별 처방 내역 |
| `order_items` | 검사, 처치 항목 |
| `clinical_orders` | 검사, 처치 요청, 수행 결과, 의사 확인 |
| `payments` | 진료별 수납 기록 |

# 기능

### 로그인 & 권한 관리

- 이메일, 비밀번호 로그인 및 로그아웃
- 세션 기반 인증
- 의사, 간호사, 관리자 역할별 업무 권한 구분
- BCrypt를 이용한 비밀번호 해시 저장

> 화면에서 버튼을 제한하는 것과 별도로 서버에서도 권한을 검사합니다. SOAP, 처방은 담당 의사만 저장할 수 있으며, 수납은 간호사, 관리자 계정에서 처리합니다.

### 대시보드

- 오늘 예약, 진료 대기, 진료 중, 진료 완료 건수 조회
- 대기 환자와 대기시간 표시
- 방문 예정 예약 및 예약시간 경과 표시
- 담당 의사별 대기, 진료 현황 조회
- 환자 관리, 진료 관리, 예약 화면으로 이동

### 환자 관리 & 외래 접수

- 환자 검색, 등록 및 정보 수정
- 생년월일, 성별, 연락처, 주소 관리
- 알레르기, 병력, 메모 기록
- 담당 의사와 방문 사유를 지정해 외래 접수
- 예약을 방문 접수로 전환
- 이미 대기 또는 진료 중인 환자의 중복 접수 방지

### 예약 관리

- 날짜별 예약 조회
- 환자, 담당 의사, 예약시간, 방문 사유 등록
- 예약 수정 및 취소
- 예약 당일 방문 접수로 전환
- 예약과 실제 방문 진료 연결

### 진료기록 & 진단

- 날짜, 진료 상태, 담당 여부별 진료 목록 조회
- 담당 의사의 진료 시작 및 완료 처리
- SOAP 형식의 진료기록 작성 및 저장
- 상병 코드, 한글명, 영문명, 추가 명칭 검색
- 진단 추가, 삭제 및 주진단 지정
- 완료한 진료기록 조회

> SOAP는 S(환자 호소), O(진찰, 검사 소견), A(평가), P(계획)로 나누어 저장합니다.

### 약품 검색 & 처방

- 약품명, 대표코드, 제품코드 검색
- 투여량, 단위, 횟수, 일수, 용법 입력
- 처방 목록 추가, 수정, 삭제 및 저장
- 방문별 처방 내역 조회
- CSV 기반 약품 기준 정보 초기 적재

> 처방은 SOAP와 별도로 저장하되 진료의 버전을 함께 사용합니다.

### 검사, 처치 요청 & 결과

- 의사의 검사, 처치 요청 등록
- 유형, 상태, 요청일, 담당 여부별 목록 조회
- 의사, 간호사의 수행 시작 및 결과 저장
- 요청 의사의 수행 전 취소 및 완료 결과 확인
- 수행자, 수행 시각, 취소 사유, 결과 확인 이력 기록
- 환자의 이전 검사, 처치 기록 조회 및 해당 진료 열기

> 검사, 처치 요청은 SOAP와 독립적으로 저장합니다.

### 환자별 진료 이력

- 환자의 전체 방문 이력을 최신순으로 조회
- 방문별 담당 의사, 진료 상태, 방문 사유 확인
- 이전 방문의 SOAP, 진단, 처방 조회
- 페이지 단위 진료 이력 조회

### 수납 관리

- 진료 완료일 기준 환자 목록 조회
- 수납 금액 직접 입력
- 현금, 카드 결제수단 기록
- 미수납, 수납완료 표시
- 수납 담당자 및 처리 시각 저장
- 진료 건당 중복 수납 방지

> 진료 상태와 수납 여부를 분리했습니다. `payments` 기록이 없으면 미수납, 있으면 수납완료로 판단합니다.

# 스크린샷(기능 설명)

<details>
  <summary>로그인</summary>

  역할별 계정 로그인 화면

  <img width="417" height="543" alt="image" src="https://github.com/user-attachments/assets/f08f207d-a860-4248-9bd0-167e12cd8f0e" />


</details>

<details>
  <summary>대시보드</summary>

  오늘 예약 및 진료 현황, 대기 환자, 담당 의사별 현황

  <img width="1167" height="723" alt="image" src="https://github.com/user-attachments/assets/f12eba17-f034-45e3-b72b-4a606372e74f" />


</details>

<details>
  <summary>환자 관리 & 외래 접수</summary>

  환자 검색, 등록, 수정 및 외래 접수 화면

  <img width="1169" height="727" alt="image" src="https://github.com/user-attachments/assets/8aca6f6f-2938-4359-8939-a2c1e5bbfb86" />


</details>

<details>
  <summary>예약 관리</summary>

  예약 조회, 등록, 수정, 취소 및 방문 접수 전환 화면

  <img width="1176" height="730" alt="image" src="https://github.com/user-attachments/assets/d34e033e-7769-4f17-936b-e8f613f66002" />


</details>

<details>
  <summary>진료기록 & 진단 & 처방</summary>

  SOAP 작성, 진단 검색과 주진단 지정, 약품 검색 및 처방 입력 화면

  <img width="1175" height="726" alt="image" src="https://github.com/user-attachments/assets/663f4435-f519-41e2-aaa1-43d58660ee81" />


</details>

<details>
  <summary>검사, 처치</summary>

  요청 등록, 수행 시작, 결과 저장 및 의사 결과 확인 화면

  <img width="1176" height="726" alt="image" src="https://github.com/user-attachments/assets/dd0e1330-495a-4149-851c-996de03b8691" />


</details>

<details>
  <summary>수납 관리</summary>

  진료 완료 환자 조회, 금액, 결제수단 입력 및 수납 완료 화면

  <img width="1174" height="730" alt="image" src="https://github.com/user-attachments/assets/6fa72266-94a6-4976-821c-788d6fc071fc" />


</details>

# 느낀 점

- 예약을 접수로 전환할 때 두 데이터를 연결하고 중복 접수를 검증하도록 구성했다.
- SOAP, 처방, 검사, 처치 결과를 구현하면서 각 기록의 작성자와 완료 시점이 다르다는 점을 고려했다.
- 상병마스터와 약품 데이터를 가져오면서 파일 형식과 인코딩, 중복 코드, 검색용 명칭을 함께 고려했다.
- WPF의 MVVM 구조를 사용하면서 화면 표시와 업무 처리를 분리했다. 데이터 바인딩으로 상태를 갱신하고, 비동기 요청 중 입력을 제한하는 것들을 고민했다.
