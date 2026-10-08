# 기존 EC2를 활용한 PocketMM 배포 준비

2026-10-08 PocketMM을 기존 EC2에 설치하고 health UP 및 사이트 화면 연결을 확인했다. 블러드옵스 두 서비스는 중지하고 자동 시작을 해제했다. 아래는 최초 준비 절차이며, 실제 서버 구성과 집에서 이어서 재배포하는 절차는 [EC2 재배포 안내](ec2-redeployment.md)를 우선 참고한다.

## 준비한 파일

- deploy/pocketmm.env.example: 서버 환경변수 양식. 발송 계정과 Gmail 앱 비밀번호는 블러드옵스의 기존 설정을 재사용한다. 수신자는 wlwns13@gmail.com이다.
- deploy/pocketmm.service: Linux systemd 실행 예시. Java 25와 pocketmm 운영 계정 및 경로를 확인한 뒤 사용한다.
- deploy/nginx-pocketmm.locations.conf: 기존 HTTPS 서버의 정적 화면/API 연결 예시. 기존 인증서 설정을 보존하고 기존 location과 교체해야 한다.

prod 프로필은 기본적으로 비공개 테스트 로그인을 켜고, 백엔드를 127.0.0.1:8080에 바인딩한다. 로그인은 HTTPS nginx 주소로 확인한다. HTTP localhost에서 prod의 secure 세션 쿠키로 로그인 테스트하지 않는다. 로컬 테스트는 local 프로필을 유지한다.

## 배포 파일 만들기

Java 25와 Node 22.12 이상으로 새 체크아웃에서 실행한다. Vite가 허용하는 Node 20.19 계열도 사용할 수 있다.

```powershell
cd C:\SpringWork\projectmm
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
cd frontend
npm.cmd ci
npm.cmd run build -- --configLoader native
```

백엔드는 target/projectmm-0.0.1-SNAPSHOT.jar, 화면은 frontend/dist다. 서버에서는 각각 /opt/pocketmm/pocketmm.jar와 /opt/pocketmm/frontend에 배치한다. 기존 블러드옵스 파일·서비스·nginx 설정을 먼저 별도로 보존하여 되돌릴 수 있게 한다.

## 서버 연결 후 확인할 항목

1. EC2의 운영체제, Java 경로/버전, 기존 앱 실행 방식(systemd·Docker·직접 실행), nginx 설정 및 앱 포트를 읽기 전용으로 확인한다.
2. PocketMM 전용 MySQL DB/계정을 준비하고 PROD_DB_*를 설정한다. 블러드옵스 DB를 덮어쓰지 않는다. Redis 연결과 AI 키도 설정한다.
3. 새 앱을 별도 포트에서 띄우고 서버 내부 /actuator/health로 기동을 확인한다. 기존 앱과 포트가 겹치면 SERVER_PORT와 nginx upstream을 함께 맞춘다.
4. nginx 설정 검사 후 연결을 전환한다. 그 뒤 HTTPS에서 devseed 로그인·이메일 인증과 채팅, 시스템 저장, 문제 기록 저장/다운로드를 확인한다.
5. 확인이 끝난 뒤 기존 블러드옵스 앱을 중단한다. EC2 인스턴스나 블러드옵스 DB를 삭제하는 단계는 없다.

운영 DB는 MariaDB RDS이므로 재배포 안내의 MariaDB 설정을 유지한다. SSH 개인키·DB 비밀번호·앱 비밀번호는 채팅에 붙이지 않고 서버의 환경 파일에 설정한다.
