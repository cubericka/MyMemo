# MyMemo 3.5

앱 이름과 표시 버전을 **MyMemo / 3.5**로 통일했습니다. 정식 파일명은 **MyMemo-3.5-release.apk**입니다.

- [정식 APK와 소스 ZIP](https://github.com/cubericka/MyMemo/releases/tag/v3.5)
- [설치 호환성·검증 설명](docs/release-3.5.md)
- 패키지: `com.cubericka.mymemo.titledrag`
- versionName: `3.5`, versionCode: `362`
- 제작자: **아카라이브 모르윈**

직전 정식 3.6.1과 같은 서명 키를 사용하고 내부 버전 코드를 높여, 기존 메모와 PIN 설정을 유지하는 업데이트 설치를 지원합니다. 이전의 다른 서명 앱과는 호환되지 않습니다. 기존 앱을 삭제하거나 데이터를 초기화하지 마세요.

제목 드래그 정렬, 다중선택, 메모 작성·수정·저장·검색, 공통 숫자 PIN, 휴지통 선택·전체 영구 삭제, 4×1 다중 메모 위젯과 Room 구조를 유지합니다. 메모 편집 옵션의 앱 잠금 설정은 제거된 상태를 유지하며, 공통 잠금은 메인과 환경설정에서 사용합니다.

## 빌드와 서명

JDK 17, Android SDK 36, build-tools 35.0.0을 사용합니다. `tools/release-policy.json`과 앱 버전이 일치해야 하며, 키와 비밀번호는 저장소에 포함하지 않습니다.

```sh
python3 tools/build_release.py --java-home /path/to/jdk17 --sdk /path/to/android-sdk --keystore /private/path/MyMemo-release-2026.p12 --alias release
```

비밀번호는 대화형으로 입력합니다. Gradle의 미서명 중간 APK를 배포하지 마세요. 서명 도구는 정책의 실제 버전으로 `MyMemo-3.5-release.apk` 파일명을 생성합니다.

기존 main 백업은 `backup/legacy-version`, 이전 배포 기록은 [v3.6.1](https://github.com/cubericka/MyMemo/releases/tag/v3.6.1)에 보존합니다. 기본 브랜치 병합은 별도 승인 전까지 보류합니다.
