# 집에서 PocketMM 개발 및 EC2 재배포 이어가기

## 2026-10-08 운영 상태

- Amazon Linux 2023, Java 25, EC2 t3.micro.
- 사이트: https://bloodops.duckdns.org/ (기존 HTTPS 인증서 유지).
- SSH: `ec2-user@3.37.101.171`, 키 페어 이름 AWSKEY.
- 프로그램: `/opt/pocketmm/pocketmm.jar`, 화면: `/opt/pocketmm/frontend`.
- 서비스: `pocketmm.service`, 자동 시작 활성화. 블러드옵스 두 서비스는 중지하고 자동 시작 해제. 기존 파일과 DB는 보관.
- nginx: `/etc/nginx/conf.d/bloodops.conf`. 이전 설정 백업: `/etc/nginx/bloodops.conf.before-pocketmm`.
- nginx 화면은 `/opt/pocketmm/frontend`, API는 `127.0.0.1:8080`, 응답 대기 180초.
- Redis: 기존 `redis6.service`, localhost:6379.
- RDS MariaDB에 별도 `pocketmm` DB와 `pocketmm_app` 계정 생성. 기존 `bloodops_j` DB와 분리.
- 자바 메모리: `-Xms128m -Xmx384m`. 현재 서비스 설정은 deploy/pocketmm.service에 반영.
- health UP과 사이트 화면 연결 확인. 실제 배포 환경의 이메일 로그인·채팅·시스템 저장·문제 기록 검사는 별도 진행.

## 깃에 없는 것과 집에서 필요한 것

실제 설정은 `/etc/pocketmm/pocketmm.env`에 root 소유 600 권한으로 보관돼 있다. DB 비밀번호, Gmail 앱 비밀번호, AI 키는 이 파일에 있고 깃에 넣지 않는다. 같은 EC2 재배포 시 이 파일을 유지하면 다시 입력할 필요가 없다. 로컬 설정과 캐릭터·월드·대화·문제 기록 데이터도 깃으로 이동하지 않는다.

집에서는 AWSKEY.pem 개인키 파일이 필요하다. 신뢰할 수 있는 비공개 방식으로 별도 보관·이동하고 깃이나 채팅에 올리지 않는다. 집 네트워크에서 SSH가 시간 초과되면 EC2 보안 그룹의 22번 포트 허용 IP를 집의 현재 공인 IP로 확인한다.

운영 DB는 새 DB라 devseed 계정부터 시작한다. 로컬 문제 기록의 서버 이관은 아직 수행하지 않았다. 재배포할 때 기존 서버 DB를 덮어쓰거나 계정을 다시 생성하지 않는다.

## MariaDB 연결 설정 유지

현재 프로그램은 MySQL Connector/J를 포함한다. MariaDB 접속은 성공했으나 자동 메타데이터 조회에서 `Unknown column 'RESERVED' in 'WHERE'`가 발생했다. 아래 서버 설정을 추가한 뒤 기동·테이블 생성·health UP을 확인했다.

```properties
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.MariaDBDialect
SPRING_JPA_PROPERTIES_HIBERNATE_BOOT_ALLOW_JDBC_METADATA_ACCESS=false
```

DB URL은 `jdbc:mysql://HOST:3306/pocketmm?sslMode=REQUIRED&characterEncoding=UTF-8` 형식이다. 실제 주소·계정·비밀번호는 서버 설정에 보관한다. deploy/pocketmm-mariadb.env.example은 참고 양식이며 운영 설정을 통째로 덮어쓰는 파일이 아니다. MariaDB 전용 드라이버 적용은 아직 하지 않았고 별도 검증이 필요하다.

## 집에서 코드 받기와 빌드

처음이면 GitHub에서 저장소를 clone한다. 기존 폴더면 변경사항을 먼저 확인한 뒤 최신 main을 받는다. 로컬 변경으로 pull이 거부되면 강제로 지우지 않고 변경을 먼저 보관한다.

```powershell
cd C:\SpringWork\projectmm
git status --short
git pull --ff-only origin main
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
cd frontend
npm.cmd ci
npm.cmd run build -- --configLoader native
```

Java 25와 Node 22.12 이상을 사용한다. Vite가 허용하는 Node 20.19 계열도 가능하다. JAR은 target/projectmm-0.0.1-SNAPSHOT.jar, 화면은 frontend/dist다. 빌드는 EC2 메모리를 쓰지 않도록 PC에서 한다.

## 같은 EC2에 재배포

다음 명령은 Windows PowerShell에서 실행한다. 키와 저장소 경로는 실제 위치로 바꾼다. 첫 ZIP 배포 이후에는 JAR과 화면만 전송하면 된다.

```powershell
$pocketmmKey = "C:\실제키폴더\AWSKEY.pem"
$pocketmmRepo = "C:\SpringWork\projectmm"
ssh -i $pocketmmKey ec2-user@3.37.101.171 "mkdir -p /home/ec2-user/pocketmm-deploy/next"
scp -i $pocketmmKey "$pocketmmRepo\target\projectmm-0.0.1-SNAPSHOT.jar" ec2-user@3.37.101.171:/home/ec2-user/pocketmm-deploy/next/pocketmm.jar
scp -i $pocketmmKey -r "$pocketmmRepo\frontend\dist" ec2-user@3.37.101.171:/home/ec2-user/pocketmm-deploy/next/
ssh -i $pocketmmKey ec2-user@3.37.101.171
```

이후는 SSH 서버 터미널에서 한 줄씩 실행한다. 백업 생성 후 서비스를 중지한다. 오류가 발생하면 다음 단계로 넘어가지 않는다.

```bash
ls -lh /home/ec2-user/pocketmm-deploy/next/pocketmm.jar /home/ec2-user/pocketmm-deploy/next/dist/index.html
sudo cp -a /opt/pocketmm /opt/pocketmm-backup-$(date +%Y%m%d-%H%M%S)
sudo systemctl stop pocketmm
sudo install -m 644 /home/ec2-user/pocketmm-deploy/next/pocketmm.jar /opt/pocketmm/pocketmm.jar
sudo cp -a /home/ec2-user/pocketmm-deploy/next/dist/. /opt/pocketmm/frontend/
sudo systemctl start pocketmm
```

이전 화면 asset은 잠시 유지해서 열린 브라우저의 이전 파일 요청을 지원한다. 약 30초 뒤 아래로 기동을 확인한다.

```bash
sudo systemctl status pocketmm --no-pager -l
curl --max-time 10 -sS http://127.0.0.1:8080/actuator/health
```

UP 확인 후 사이트에서 강력 새로고침하고 로그인·이메일 인증, 채팅, 시스템 저장 후 새로고침, 문제 기록을 검사한다. 같은 경로·포트로 파일만 교체하면 nginx를 다시 바꿀 필요는 없다. 운영 환경 파일과 DB는 유지한다. 향후 스키마 변경이 있는 릴리스는 재배포 전에 별도 DB 백업도 필요하다.

실패하면 서비스를 중지하고 최근 로그를 확인한다. 백업 폴더의 JAR과 화면을 원래 경로로 복원해 다시 시작할 수 있다. 파일 백업은 DB 변경을 되돌리지 않는다. 실제 비밀번호나 키가 출력된 로그는 공유하지 않는다.
