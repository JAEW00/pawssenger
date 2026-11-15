# PAWSSENGER 개발 환경 설정 (Windows)

## 1. 자동 설치
관리자 PowerShell에서:
```
Set-ExecutionPolicy -Scope Process Bypass
.\setup-windows.ps1
```
→ 가상화 확인, Windows Hypervisor Platform 활성화, Git / Android Studio / GitHub CLI 설치. **끝나면 재부팅.**

## 2. 수동 단계
1. **BIOS 가상화**: 스크립트가 경고하면 BIOS에서 VT-x(Intel) / SVM(AMD) 켜기.
2. **Android Studio 첫 실행**: Standard 설치 → SDK 다운로드.
3. **Pleos Connect 에뮬레이터 이미지**
   - Tools > SDK Manager > SDK Update Sites > Add
     - Name: `Pleos Connect System Image`
     - URL(x86_64): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - SDK Platforms 탭 > Show Package Details > API 34의 Pleos Connect system image 체크 > OK
4. **AVD 생성**: Device Manager > Create device > Automotive > Pleos Connect v2.0
   - Show Advanced Settings: **Graphics = Hardware**, **Boot option = Cold boot** (quick boot면 VHAL 안 됨)
5. **CRN 발급/주입**: Pleos Playground 가입 → My Project 생성 → Project Info > Test Info 에서 CRN 복사 → 문서의 방법대로 에뮬레이터에 주입
6. **Pleos Connect SDK 의존성**: Playground 문서의 Gradle 저장소/좌표를 `app/build.gradle.kts` 의 TODO 위치에 추가
7. **프로젝트 열기**: Android Studio > Open > 이 폴더 → Gradle Sync → 에뮬레이터에서 Run

## 3. 제출용 APK
```
./gradlew :app:assembleRelease
```
빌드 결과(`app/build/outputs/apk/release/app-release.apk`)를 `apk/bori_Pawssenger_release.apk`로 복사해 저장소에 올린다.

## 참고
- https://pleos.ai/playground/resources/en/docs/guide/getting-started/application-development/setup-connect-sdk-emulator
- https://pleos.ai/playground/resources/en/docs/guide/getting-started/application-development/setup-application-development-environment
