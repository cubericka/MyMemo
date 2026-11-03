# MyMemo 3.6.1

잉크 & 앰버 메모 앱. 제목 영역 드래그 정렬, 다중선택, 공통 숫자 PIN, 휴지통 선택·전체 영구 삭제, 4×1 다중 메모 위젯을 제공합니다. 제작자: **아카라이브 모르윈**.

메모별 편집 옵션의 앱 잠금 설정은 제거했습니다. 공통 잠금 설정은 메인 화면과 환경설정에서 사용합니다. 데이터베이스 구조와 기존 메모 기능을 유지합니다.

- 버전: **3.6.1 (361)**
- 패키지: `com.cubericka.mymemo.titledrag`
- 정식 APK 파일명: `MyMemo-3.4.2-release.apk` — 요청에 따라 이름을 유지했으며 파일 내부 버전은 3.6.1입니다.
- [정식 APK와 소스 ZIP](https://github.com/cubericka/MyMemo/releases/tag/v3.6.1)
- [서명·검증·설치 호환성](docs/release-3.6.1.md)
- 기존 main 백업: `backup/legacy-version`

이번 정식 배포부터 사용자 승인으로 **새 서명 키**를 사용합니다. 같은 패키지의 이전 서명 앱에 덮어 업데이트할 수 없고, 기존 메모는 자동 이전되지 않습니다. 기존 앱을 삭제하거나 초기화하지 마세요. `.debug` 테스트 앱과는 별도 설치됩니다. 이후 업데이트는 이번 새 키를 계속 사용해야 합니다.

## 빌드와 서명

JDK 17, Android SDK 36, build-tools 35.0.0을 사용합니다. 키와 비밀번호는 저장소에 포함하지 않습니다. `tools/release-policy.json`의 공개 인증서 지문으로 빌드·서명을 검증합니다.

```sh
python3 tools/build_release.py --java-home /path/to/jdk17 --sdk /path/to/android-sdk --keystore /private/path/MyMemo-release-2026.p12 --alias release
```

비밀번호는 대화형으로 입력합니다. Gradle의 릴리스 출력은 서명 전 중간 파일이므로 위 보호·서명 절차를 완료한 APK만 배포하세요. Android/Robolectric 테스트 60개와 배포 도구 테스트 8개가 통과했습니다. 별도 fixture가 없는 10개 테스트는 제외했고 실제 휴대폰 검증은 하지 않았습니다. 서명 검증과 Android 14 에뮬레이터의 설치·실행 검사 8개도 통과했습니다.
