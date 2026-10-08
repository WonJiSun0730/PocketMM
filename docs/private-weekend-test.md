# 주말 비공개 테스트 로그인

배포 서버에 `PRIVATE_TEST_MODE=true`를 설정한다. 기본값 false인 일반 개발 환경은 기존 로그인을 유지한다.

- devseed 계정의 기존 비밀번호 확인 후 wlwns13@gmail.com에 6자리 번호를 보낸다. 다른 계정·틀린 비밀번호는 발송하지 않는다.
- 인증 전에는 API 접근 권한이 생기지 않는다. 이전 로그인 세션도 인증 완료 표시가 없어 차단된다.
- 회원가입과 아이디·이메일 중복 확인 API는 차단하고 가입 버튼을 숨긴다.
- 번호는 5분 만료, 5회 오입력 시 무효, 동일 세션에서 1회만 사용할 수 있다. 인증 완료 시 세션 ID를 바꾼다.
- 발송 간격은 모든 세션을 합쳐 2분이다. 현재 제한 저장소는 단일 서버 메모리다. 서버 재시작 시 발송 대기는 초기화되므로 이 설정은 단일 서버 테스트 배포에 사용한다.
- 새 DB에는 devseed 계정만 생성한다. 기존 devseed 계정의 비밀번호와 권한은 덮어쓰지 않는다. 자동 생성 비밀번호는 요청한 devseed-password다.

## 메일 설정

블러드옵스 EmailVerificationService의 JavaMailSender/SimpleMailMessage 방식과 Gmail SMTP 설정을 재사용했다.

서버 환경변수 `MAIL_USERNAME`, `MAIL_PASSWORD`에 블러드옵스에서 사용하던 발송 계정과 Gmail 앱 비밀번호를 설정한다. 수신자는 발송 계정과 무관하게 위의 고정 주소다. 비밀번호를 코드나 Git에 넣지 않는다.

SMTP는 smtp.gmail.com:587, TLS 필수, 연결·읽기·쓰기 제한 5초다. 발송 실패 시 로그인하지 않고 503을 반환한다. 가입용 임의 이메일 발송 API는 추가하지 않는다.

## 배포 전 실제 확인

1. PRIVATE_TEST_MODE=true로 기동하고 다른 계정, 잘못된 비밀번호, 가입 요청이 거부되는지 확인한다.
2. devseed 로그인 후 받은 편지함에서 번호를 확인한다. 아직 /api/auth/me는 거부되어야 한다.
3. 번호를 입력한 뒤 로그인하고, 로그아웃한 다음 재접근이 막히는지 확인한다.
4. 인증 전 브라우저와 별도 브라우저에서 번호 재사용·세션 혼용이 거부되는지 확인한다.

자동 테스트는 메일 발송을 모의 처리한다. 실제 Gmail 발송 성공과 AWS 운영 상태는 자동 테스트 결과에 포함되지 않는다.

## AWS 확인 범위

BloodOps의 feature/aws-deployment 브랜치는 application.properties의 외부 환경변수 설정을 추가한 커밋이다. 저장소에 서버 구성이나 배포 자동화 파일이 없어 Git만으로 실제 AWS 리소스를 확인할 수 없다.

기존 서비스 중단 및 서버 재사용 판단에는 AWS 서비스 종류와 인스턴스/환경 정보가 필요하다. 기존 DB를 PocketMM DB로 덮어쓰거나 서버를 삭제하는 작업은 수행하지 않았다.

2026-10-08 확인: https://bloodops.duckdns.org/ 는 HTTP 200으로 응답하며 Server 헤더는 nginx/1.30.4다. DNS A 레코드는 3.37.101.171이다. 이는 공개 웹 응답 확인 결과이며 EC2 콘솔에서 인스턴스를 확인한 결과는 아니다.

같은 EC2의 기존 앱을 중지하고 PocketMM으로 교체하는 방식은 가능하다. 구체적인 교체에는 SSH/SSM 또는 AWS 콘솔 접근, 기존 프로세스 실행 방식, nginx upstream, PocketMM 전용 DB와 환경변수 확인이 필요하다. 인스턴스를 삭제하는 방식으로 진행하지 않는다.

인증 세션 저장과 세션 ID 변경은 [Spring Security 공식 문서](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)를 참고했다.
