# 사용자 대화·장기기억 저장 암호화

## 1차 커밋 검토

기준: `main`의 `835a346b3600297d1fd0c6e84e8f0b3d33306c52`.

- AES-256-GCM, 12바이트 무작위 nonce, 128비트 인증 태그 선택은 유지한다. 동일 평문도 매번 다른 암호문으로 저장한다.
- 기존 구현은 키 누락 시 평문을 저장했고, 대화 본문 3종 외 장기기억·복사된 상태는 평문이었다. 기본값을 키 필수로 바꾸고 적용 범위를 확장했다.
- 기존 v1 암호문을 읽는다. 새 쓰기는 버전 식별자를 AAD에 포함한 v2 형식이다. 잘못된 키·변조·잘린 데이터·알 수 없는 버전은 평문으로 대체하지 않고 실패한다. 빈 문자열도 왕복 가능하다.
- Spring에서 설정을 주입하며 static 환경변수 캐시를 제거했다. 키 교체는 프로세스 재시작과 별도의 재암호화 계획이 필요하다. 이번 마이그레이션은 키 회전 도구가 아니다.

## 적용 범위와 경계

정확한 목록은 `EncryptedColumns.TABLES`와 JPA 변환기 선언이며 테스트로 일치 여부를 확인한다.

- `chat_messages`, `world_room_messages`, `world_room_message_variants`의 대화 본문.
- 장기기억의 요약·인용·구조화된 사실/의미의 문자열, 보호 세부기억, 임베딩 벡터.
- 분기별 플레이어/장면 상태 스냅샷, 사용자 장면 지시, Pocket 부재 활동·서술 및 서술 맥락.
- Pocket 시작 인트로 사본·초기 위치/상태/소지품·사용자 노트·플레이 가이드, 현재 플레이어 위치/상태/소지품.

개발 QA·오류 로그는 요청대로 기존 평문 유지: `evaluation_context_json`, `test_diagnostics_json`, `test_memory_before_json`, `test_memory_after_json`, QA 내보내기와 오류 기록 파일은 암호화하지 않는다. 따라서 QA 자료에 복사한 대화는 평문으로 남는다. 이 변경으로 모든 파일·모든 DB 메타데이터가 암호화되는 것은 아니다.

ID·소유 관계·날짜·역할·숫자 상태·분류 enum·기존 검색/중복 방지 지문은 메타데이터로 유지한다. 보호 세부기억의 기존 평문 unique index는 `(memory_id, detail_hash)`로 바꾼다. 새 지문은 암호화 키에서 별도 용도 키를 도출한 HMAC-SHA-256으로, DB만으로 원문을 사전 대입하는 것을 막는다. 동일성 정보는 남으므로 운영 검색어로 노출하지 않는다. 명시적으로 암호화를 끈 개발 DB에서는 SHA-256을 사용한다. 사용자 검색/회상은 권한 확인 후 서버에서 복호화한 값을 사용한다. 암호문에 SQL LIKE를 적용하지 않는다.

이는 서버 저장 암호화이며 종단간 암호화가 아니다. 서버는 응답/AI 처리 시 복호화한다. 키와 DB를 함께 탈취한 공격, 관계 메타데이터 변경, 행 사이 암호문 교환까지 막는 설계는 아니다. JPA 변환기는 행 ID를 받지 못하므로 AAD는 형식에 결합되며 행별 소유자에 결합되지 않는다.

## 키와 환경 설정

`POCKETMM_DATA_ENCRYPTION_KEY`에는 암호학적으로 무작위인 32바이트를 Base64로 인코딩한 비밀을 넣는다. 키를 코드·명령 인자·Git·QA 파일·로그에 넣지 않는다. 운영 비밀 저장소 또는 권한을 제한한 서버 환경 파일로 주입하고, DB 백업과 분리해 키를 안전하게 백업한다. 같은 키를 유지하지 못하면 기존 데이터는 복호화할 수 없다.

운영 `prod`는 키 필수, `ddl-auto=validate`, SQL 출력 비활성이다. 로컬에서 평문 저장이 필요한 경우에만 `pocketmm.encryption.required=false`를 명시한다. QA/오류 기록을 평문으로 유지하기 위해 사용자 DB 암호화를 끌 필요는 없다. 테스트는 별도 `application-default.properties`로 H2에 한해 기본 암호화를 끄며, 암호화 통합 테스트는 실행 때 만든 무작위 키를 주입한다.

## 소유자 권한

Pocket 대화/기억/변형 조회는 기존 `ownedSession`의 회원 소유 검증과 world/room/session 관계 검증을 유지한다. 공개 세계를 배포한 제작자라도 다른 사용자의 Pocket은 조회할 수 없다.

이전 `/api/chat/**`와 `/api/worlds/{worldId}/characters/{characterId}/**` 경로는 로그인과 캐릭터 소유자 확인을 거친다. 소유자가 없는 예전 캐릭터는 임의 사용자에게 귀속시키지 않고 404로 차단한다. 기존 데이터의 소유자 보정은 별도 검토 후 수행한다. 운영자 역할도 일반 대화 경로의 소유자 확인을 우회하지 않는다.

## 운영자 제한 검색·복호화

기본은 비활성이다. `POCKETMM_PRIVATE_DATA_OPERATORS`에 정확한 로그인 ID를 쉼표로 나열하고, 해당 계정은 별도로 `ADMIN` 또는 `DEVELOPER` 역할이어야 한다. 기존 로그인 절차를 통과한 세션으로 호출한다. 비공개 모드에서도 운영자 API는 이 이중 검증을 거친다.

`POST /api/admin/private-data/search` 예:

```json
{"target":"POCKET","ownerId":123,"scopeId":456,"before":null,"reason":"문의 티켓 2026-001 확인"}
```

사용자와 대화 범위를 모두 지정해야 하며, 한 번에 ID 메타데이터 최대 20개만 반환한다. `before`를 지정하면 더 이전 ID를 검색한다. 본문 전체 검색이나 전역 덤프는 없다.

`POST /api/admin/private-data/decrypt` 예:

```json
{"target":"POCKET","ownerId":123,"scopeId":456,"ids":[789],"reason":"문의 티켓 2026-001 확인"}
```

요청 ID 최대 20개, 중복 불가, 모든 ID가 지정한 소유자/범위에 속해야 한다. 하나라도 맞지 않으면 내용은 반환하지 않는다. 지원 대상: `CHAT`(conversation), `POCKET`(session), `VARIANT`(session), `CHAT_MEMORY`(conversation), `POCKET_MEMORY`(session). 기억 복호화는 요약만 반환한다. 추가 필드나 전체 기록을 자동 반환하지 않는다. 응답은 `Cache-Control: no-store`이다.

사유는 10~500자이며 티켓 번호/목적만 기록한다. 대화 원문이나 키를 사유에 붙여 넣지 않는다. 감사 테이블에는 요청 UUID, 수행자, 대상 종류·소유자·범위·ID, 사유, 시각, 결과·건수만 쓴다. 검색과 복호화의 요청/성공/실패는 각각 별도 트랜잭션으로 기록한다. 인증된 권한 거부도 기록한다. 감사 저장 실패 시 내용 반환은 차단한다. `REQUESTED`만 남은 요청은 프로세스 중단 여부를 조사한다.

운영 DB 계정에는 감사 테이블 INSERT만 부여하고 UPDATE/DELETE는 부여하지 않는다. 조회/보관/외부 보존은 별도 관리 계정과 정책으로 운영한다. 애플리케이션에는 감사 수정·삭제 API가 없다. DB 관리자까지 통제하는 외부 불변 저장소는 별도 운영 구성이 필요하다.

## 운영 적용: 아직 실행하지 않음

SQL 파일과 마이그레이션은 애플리케이션 시작 시 자동 실행되지 않는다. 다음 절차를 검토·완료하기 전 운영 DB를 변경하지 않는다. DDL은 MySQL/MariaDB에서 암묵적으로 커밋되므로 트랜잭션 롤백만으로 복구할 수 없다.

1. 운영 DB 종류/버전/현재 스키마·인덱스·행 수·디스크 여유를 확인한다. 오래된 평문, v1 암호문, QA 필드의 분포를 별도로 집계한다. 기존 키가 있다면 그대로 확보한다. 적용 SQL은 실제 MySQL/MariaDB 버전의 복제 DB에서 먼저 검증한다.
2. 모든 앱·부재 활동 스케줄러·백그라운드 작업의 쓰기를 중단한다. DB 일관 스냅샷/전체 논리 백업(테이블·인덱스·제약조건 포함)을 만든다. 백업 접근을 제한하고 SHA-256을 기록한다. 별도 DB에 복원해 행 수와 표본 내용을 확인하고, v1 데이터가 있으면 같은 키로 인증되는지 검증한다.
3. 복원한 검증 DB에서 `deploy/sql/20261029-encryption-prepare.sql`을 수동 실행한다. 기존 보호 세부기억 인덱스 이름이 다르면 실제 이름을 확인해 SQL을 수정한다. SQL은 재실행용 IF NOT EXISTS 스크립트가 아니므로 이미 적용한 DDL을 반복 실행하지 않는다.
4. 검증 DB에서 아래 CLI의 `--dry-run`, `--apply`, `--verify`를 차례로 수행한다. 모든 암호문 인증, 행 수 불변, 평문 0, 보호 세부기억 해시 존재를 확인한다. `encryption-finalize.sql`로 NOT NULL/unique 제약을 확정한다. 앱을 키 설정과 함께 구동해 로그인·본인 대화·장기기억·재생성·분기 전환·5개 페이지 조회·타인 차단·운영자 감사 기록을 점검한다.
5. 복원 검증 결과와 백업 해시를 승인 기록에 남긴 뒤, 운영에서도 쓰기 중단 상태를 유지한 채 같은 순서를 적용한다. 운영 URL에 대한 별도 백업 증빙을 사용한다. 소유자 보정이나 키 회전을 이 작업에 섞지 않는다.
6. 운영 `--verify`가 평문 0을 보고하고 모든 점검이 통과한 뒤 쓰기를 재개한다. QA/오류 로그는 암호화 대상에서 제외했으므로 별도 평문 점검 대상이다.

CLI는 프로젝트 빌드 결과의 클래스를 직접 실행한다. Spring 앱을 시작하거나 스케줄러를 실행하지 않는다. 런타임 classpath는 `target/classes`와 현재 프로젝트의 MySQL Connector/J JAR이다. Windows는 `;`, Linux는 `:`로 구분한다.

```text
java -cp "<target/classes><구분자><mysql-connector-j JAR>" com.example.projectmm.infrastructure.security.EncryptionMigration --dry-run
java -cp "<동일 classpath>" com.example.projectmm.infrastructure.security.EncryptionMigration --apply
java -cp "<동일 classpath>" com.example.projectmm.infrastructure.security.EncryptionMigration --verify
```

비밀은 환경변수로만 공급한다: `POCKETMM_DATA_ENCRYPTION_KEY`, `POCKETMM_MIGRATION_DB_URL`, `POCKETMM_MIGRATION_DB_USER`, `POCKETMM_MIGRATION_DB_PASSWORD`. DB 비밀번호를 URL에 넣지 않는다. 키는 실제 암호화 대상과 동일해야 한다.

`--apply`에만 추가로 필요한 값:

- `POCKETMM_MIGRATION_MAINTENANCE=WRITERS_STOPPED`
- `POCKETMM_MIGRATION_BACKUP_FILE`: 실제 백업 파일의 로컬 경로
- `POCKETMM_MIGRATION_BACKUP_EVIDENCE`: 다음 형식의 복원 검증 증빙 properties 파일 경로

```properties
backup.sha256=<실제 백업 파일 SHA-256 소문자 64자리>
restore.verified=true
restore.verified.at=<복원 검증 시각>
database.url.sha256=<대상 JDBC URL 문자열 UTF-8의 SHA-256 소문자 64자리>
```

도구는 백업 파일의 실제 해시와 증빙/대상 URL의 일치를 확인한다. 사람이 기록한 복원 검증·쓰기 중단 여부를 자동으로 입증하는 장치는 아니므로 실행 책임자가 실제 상태를 확인해야 한다. 키는 증빙 파일에 넣지 않는다.

`--dry-run`은 모든 대상 값을 읽어 암호문을 인증하고 평문/암호문 개수를 출력하지만 쓰지 않는다. `--apply`는 200행씩 처리하며 각 값의 암복호화 왕복·DB 재조회 검증 후 배치 커밋한다. 원래 값 조건이 맞지 않으면 현재 배치를 롤백한다. 이미 암호화된 값은 다시 암호화하지 않고 인증만 한다. 실패 전 커밋된 배치는 남으므로 문제를 해결한 뒤 재실행한다. 출력에는 테이블명과 개수만 남긴다.

`enc:`로 시작하는 알 수 없는 형식이나 손상된 값은 자동으로 평문 취급하지 않는다. 복제 DB에서 원인과 원본을 확인하고 수동 복구 계획을 마련한다. `--verify`에서 평문이 남으면 종료 코드 1이다. 최종 SQL은 보호 세부기억의 해시 누락도 차단한다.

실패 시 쓰기를 재개하지 않는다. 현재 배치 롤백만으로 이전 배치·DDL까지 되돌아가지 않으므로, 배포 버전/설정과 백업 DB를 함께 복원한다. 전환 중/완료 후 새 데이터가 생겼다면 백업 복원 전에 별도로 보존하고 병합 계획을 세운다. 구버전 앱으로만 되돌리거나 암호문을 평문으로 덮어쓰는 롤백은 하지 않는다. 백업과 기존 키는 확인 기간이 끝날 때까지 별도로 보호한다.

## 조회 방식과 검증

Pocket UI의 첫 조회와 위로 스크롤 추가 조회는 각각 최근 5개이다. `id < before`로 이전 페이지를 가져와 시간순으로 앞에 붙이고, 기존 맨 위 메시지를 기준으로 스크롤 위치를 복원한다. AI에 전달하는 문맥 길이는 별도 정책을 유지한다. 이전 캐릭터 채팅 API의 조회 길이는 기존 동작을 유지한다.

테스트는 무작위 키의 Unicode/빈 문자열/큰 본문 왕복·nonce 차이, 잘못된 키/변조/버전/잘림, v1 호환, JPA 원시 DB 암호문, 전체 변환 필드와 마이그레이션 목록 일치, 무변경 점검·재실행·배치 롤백, 백업 증빙 검증, 타인/무소유자 접근 차단, 운영자 범위·권한·최대 건수·감사 실패 차단, 감사 트랜잭션 독립성, 5개 페이지 경계를 확인한다. 운영 DB 검증을 대체하지는 않는다.
