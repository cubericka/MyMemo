# My Memo 3.4.2 — Windows 공식 서명 빌드 안내

## 현재 상태

사용자가 테스트 APK의 기능 검수를 완료한 v3.4.2 소스를 바탕으로 배포 스크립트만 보완했습니다. 앱 기능·데이터베이스·패키지 설정은 변경하지 않았습니다. 공식 Release APK는 아직 생성하거나 실기기에서 검수하지 않았습니다.

공식 인증서 SHA-256 (사용자가 제공한 공개 지문):
`9a07d3fb061cd33f62cae6b6214df51acc0ad6fb40175da24a2a9a511adc8aef`

설정은 `tools/release-policy.json`에 있습니다. 개인키·비밀번호는 프로젝트에 포함되지 않습니다. 키 파일 기본 위치는 `%USERPROFILE%\MyMemoSigning\mymemo-release.p12`, 별칭은 `release`입니다.

## 1. 새 소스 열기

ZIP을 새 폴더에 압축 해제하고 Android Studio에서 그 안의 `MyMemo` 폴더를 엽니다. Gradle Sync가 완료될 때까지 기다립니다. 기존 휴대폰 테스트 앱은 삭제하지 마세요.

Android Studio 하단 Terminal을 열고 PowerShell에서 다음을 실행합니다. 현재 위치는 `gradlew.bat`과 `tools` 폴더가 있는 MyMemo 폴더여야 합니다.

```powershell
Get-Location
Test-Path .\tools\build_release.py
py -3 --version
```

`Test-Path`는 True, Python 버전은 3.10 이상이어야 합니다. `py`가 없으면 Windows용 Python 3를 설치한 뒤 터미널을 다시 열어주세요. `python --version`이 3.10 이상으로 작동한다면 아래 명령의 `py -3`를 `python`으로 바꿔도 됩니다.

## 2. 사전 점검

```powershell
py -3 .\tools\build_release.py --check
```

필요한 항목:
- Android Studio 기본 설치 위치의 `jbr` 또는 JAVA_HOME으로 지정한 JDK 17 이상
- Android SDK Platform 36
- Android SDK Build-Tools **35.0.0**
- 프로젝트 밖에 보관한 PKCS12 키 파일

Build-Tools가 없다는 오류가 나오면 Android Studio의 Tools → SDK Manager → SDK Tools → Show Package Details에서 Android SDK Build-Tools 35.0.0을 선택해 설치합니다. SDK Platforms에서 API 36도 확인하세요.

기본 경로와 다르면 정확한 실제 경로를 지정합니다. 아래는 형식 예시이며 자신의 경로로 바꿔야 합니다.

```powershell
py -3 .\tools\build_release.py --check --java-home "D:\Android Studio\jbr" --sdk "D:\Android\Sdk" --keystore "D:\PrivateKeys\mymemo-release.p12"
```

통과 문구: `Prerequisite check passed.` 이 단계는 경로·버전 확인만 하며 키 비밀번호나 실제 인증서는 아직 검증하지 않습니다.

## 3. 빌드 및 서명

사전 점검이 통과하면 실행하세요. 경로 옵션을 사용했다면 여기에도 똑같이 넣습니다.

```powershell
py -3 .\tools\build_release.py
```

스크립트가 수행하는 순서:
1. 승인 지문과 버전 342를 확인하고 Release 빌드를 실행합니다.
2. `Keystore password (hidden):`가 나오면 본인 키 비밀번호를 입력합니다. 화면에는 나타나지 않습니다.
3. 키의 실제 인증서 지문과 승인 지문이 같은지 검사합니다.
4. DEX·네이티브 라이브러리·리소스 등 APK 엔트리의 해시 목록을 생성하고 서명합니다.
5. zipalign을 실행한 뒤 APK v2/v3 서명을 적용합니다.
6. apksigner 서명 검증, 실제 서명자 지문 비교, 정렬 및 엔트리 내용 검증을 합니다.
7. 모든 검증이 통과한 뒤에만 최종 이름으로 APK를 내보냅니다.

비밀번호는 파일·명령행 인자로 저장하지 않으며 Gradle 빌드에도 전달하지 않습니다. 서명 작업 동안만 자식 프로세스 환경에 전달됩니다. 로컬 관리자 등에게까지 비밀번호 노출을 완전히 막는 방식은 아닙니다.

성공 문구: `BUILD AND SIGNATURE VERIFICATION SUCCEEDED`

결과 위치:

```text
MyMemo\release-output\실행날짜-시간-식별자\MyMemo-3.4.2-release.apk
```

`FAILED`가 표시되면 그 실행은 실패입니다. unsigned/debug APK를 대신 배포하지 마세요. 원인 메시지를 확인하고 재실행하세요. 실행마다 별도 출력 폴더를 사용하므로 예전 성공 파일을 새 결과로 오인하지 마세요. 실패한 작업은 새 최종 APK를 내보내지 않습니다. 기존 최종 파일을 지정해 덮어쓰는 작업도 거부합니다.

빌드 중 다운로드를 위해 인터넷이 필요할 수 있지만, 완성된 앱의 실행에는 인터넷을 요구하지 않습니다.

## 4. 최종 APK 휴대폰 확인

- 정식 앱 이름: My Memo
- 정식 패키지: com.personal.memostarter
- 기존 테스트 패키지: com.personal.memostarter.preview34

정식 앱은 현재 테스트 앱과 별도로 설치됩니다. 테스트 앱의 메모가 자동 이전되지 않습니다. 테스트 앱 삭제·데이터 초기화를 하지 마세요. 백업/복원 기능은 사용자의 요청대로 제거된 상태입니다.

Release APK에서 오프라인 시작, 작성·저장·재실행, 잠금, 체크리스트, 드래그 정렬, 위젯, 재부팅을 다시 확인합니다. 보호 오류 화면이 나오면 배포하지 말고 오류 문구를 확인하세요. 테스트 APK 기능 검수 통과가 Release 보호 기능 검증을 대신하지 않습니다.

고객 배포 전 남은 검증: 실제 Release APK 정상 실행, 코드/리소스가 변경된 APK와 다른 키로 재서명한 APK의 차단, 동일 공식 키를 사용하는 후속 버전으로 업데이트 시 데이터 보존. 검증용 변조 APK는 실제 메모가 없는 별도 테스트 환경에서만 다룹니다. 기존 tools/test_apks.py는 이전 플랫폼용 테스트 도구이며 이번 Windows 실행 지원 대상으로 수정하지 않았습니다.

## 5. 배포 및 보관

실기기 검수까지 통과한 최종 APK와 설치 안내만 고객에게 전달합니다. .p12·비밀번호·소스·R8 mapping 파일은 전달하지 않습니다. 공식 키와 비밀번호는 별도 안전한 장소에 보관합니다. 후속 업데이트는 공식 패키지와 키를 유지하고 versionCode를 증가시켜야 합니다. 버전별 APK와 mapping.txt도 개발자가 보관하세요.

## 변경 파일 및 검증 결과

- tools/build_release.py: Windows 기본 경로 탐색, 사전 점검, 승인 지문 주입, Gradle→서명 실행, 숨김 비밀번호 입력, 실행별 출력 폴더.
- tools/protect_release.py: .exe 지원, apksigner.jar 직접 실행, 공백 경로 처리, 임시 파일에서 서명/검증 후 최종 APK 생성, 지문 및 내용 비교, 기존 출력 덮어쓰기 방지.
- tools/release-policy.json: 승인 공개 지문·앱 ID·버전 342·Build-Tools 설정.
- tools/test_release_pipeline.py: 외부 도구를 모의 처리한 파이프라인 단위 테스트.
- docs/WINDOWS_RELEASE.md, README.md, START_HERE.md, docs/보호-빌드-배포.md: 현행 절차 및 버전 안내.

실행한 검증: Python 파이프라인 단위 테스트 8개 통과. 서명→검증 순서, 검증 실패 시 최종 APK 미생성, 기존 파일 보존, 잘못된 지문/변경된 내용 거부, Windows 도구 경로 및 해시 대상 확인. Python 소스 문법과 ZIP의 앱 소스/스키마 불변도 확인.

실행하지 못한 검증: 실제 Windows 실행, Gradle Release 빌드, 실제 Java 서명, APK 설치, 변조 APK 실행, 실기기 검수. 현재 작업 환경은 정상 작동하는 JDK 및 Android SDK를 갖추지 못했습니다. 따라서 이 ZIP은 배포 도구 소스이며 완성된 배포 APK가 아닙니다. 단위 테스트는 실제 암호학적 서명 검증을 대신하지 않습니다.

오프라인 자체 검사는 공격자가 검사 코드까지 패치하면 우회할 수 있습니다. 난독화와 서명/내용 검증은 변조 난이도를 높이지만 완전한 방지를 보장하지 않습니다. 공식 개인키를 가진 공격자는 신뢰 기준을 훼손할 수 있으므로 키를 별도로 보호해야 합니다.
