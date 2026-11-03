# MyMemo 3.6.1

잉크 & 앰버 메모 앱. 제목 영역을 길게 눌러 드래그 정렬하고, 여러 메모를 선택하여 휴지통으로 옮길 수 있습니다. 설정의 어플 정보에 앱 이름·현재 버전·제작자 **아카라이브 모르윈**을 표시합니다.

3.6.1에서는 앱 공통 숫자 PIN과 휴지통 선택/전체 영구 삭제를 추가했습니다. 복원 UI는 제거했으며 영구 삭제 전 확인합니다. 기존 암호화 메모와 Room 스키마 v4를 유지합니다.

- 패키지: `com.cubericka.mymemo.titledrag`
- 버전: `3.6.1` (361)
- APK 파일명: `MyMemo-3.4.2-release.apk` (기존 이름 유지; 실제 버전은 APK 내부 정보 기준)
- [기능·호환성·검증 상태](docs/release-3.6.1.md)
- 기존 main 백업: `backup/legacy-version`

## 빌드

JDK 17, Android SDK platform 36, build-tools 35.0.0이 필요합니다.

```sh
export MYMEMO_RELEASE_CERT_SHA256=c79f39f8c3de98981ea85d8a4e6c40e5b084fcc3807d97b815603991dce0d5d4
./gradlew :app:assembleRelease :app:testDebugUnitTest
```

릴리스 Gradle 출력은 미서명 APK입니다. 기존 보호·서명 절차 `tools/build_release.py`를 사용하고 서명 키는 프로젝트 바깥에 보관하세요. 비밀번호는 대화형으로 입력합니다. 서명 키를 저장소에 커밋하지 마세요.

```sh
python3 tools/build_release.py --java-home /path/to/jdk17 --sdk /path/to/android-sdk --keystore /private/path/mymemo-title-drag.p12 --alias release
```

기존 GitHub 3.4.2와 첨부된 3.6.0은 패키지 및 서명이 다릅니다. 3.6.1은 첨부된 3.6.0 계열의 업데이트이며, GitHub 구버전의 메모를 자동 이전하지 않습니다. 기기에서 기존 앱을 삭제하거나 데이터를 초기화하지 마세요.
