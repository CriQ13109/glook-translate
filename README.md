# Glook Translate

순정 Material 3 Expressive 감성의 안드로이드 네이티브 번역기 — Glook 시리즈 첫 번째 앱.

> 이 문서는 **무엇을 구현했고, 무엇을 검증했으며, 요구사항과 어디가 다른지**를 정직하게 기록합니다.

---

## 1. 빌드 · 검증 상태 (2026-11-23)

| 항목 | 결과 |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ 성공 (클라우드 컨테이너, JDK 21, Gradle 9.9.2, AGP 9.5.2, compileSdk 37.1) |
| `./gradlew :app:testDebugUnitTest` | ✅ 8/8 통과 (`RegisterDetectorTest` — 화법 판별기) |
| `./gradlew :app:assembleRelease` (R8 축소) | ✅ 성공, arm64-v8a + armeabi-v7a 포함 미서명 APK 약 61 MB |
| `./gradlew :app:lintDebug` | ✅ 오류 0, 경고 7 (UnusedAttribute·ObsoleteSdkInt 등 경미) |
| **실기기 / 에뮬레이터 실행** | ❌ **하지 않음** (컨테이너에 KVM·기기 없음). UI 동작, Keystore/StrongBox, SQLCipher 실제 오픈, ML Kit 모델 다운로드, TTS/STT, PROCESS_TEXT 메뉴 노출, 위젯·타일은 **컴파일만 확인**했고 런타임은 검증하지 않았습니다. |
| **BYOK 클라우드 호출** | ❌ API 키가 없어 실제 요청은 보내지 않음. SDK 호출 코드는 컴파일 확인만. |

APK 용량의 대부분은 ML Kit 네이티브 라이브러리(번역 ~16 MB/ABI, OCR ~11 MB/ABI)입니다. Play 배포 시 AAB 로 올리면 ABI 별로 분할됩니다.

## 2. 버전 검증 (Google Maven / Maven Central 메타데이터와 소스 JAR 로 직접 확인)

| 의존성 | 사용 버전 | 비고 |
|---|---|---|
| Compose Material 3 | **1.5.0 (stable)** | BOM `2026.11.01` 도 1.5.0 을 가리킴. 1.6.0-alpha01 존재 |
| Compose BOM | 2026.11.01 | compose-ui/foundation 1.13.0 → **compileSdk 37.1 이상 요구**(AAR 메타데이터) |
| AGP / Gradle / Kotlin | 9.5.2 / 9.9.2 / 2.4.21 | AGP 9 내장 Kotlin 사용 (`kotlin-android` 플러그인 없음) |
| sqlcipher-android | 4.20.0 | |
| androidx.sqlite(-ktx) | 2.7.1 | |
| datastore-preferences | 1.3.0 | |
| ML Kit translate / language-id / text-recognition(-korean/-japanese/-chinese) | 17.0.3 / 17.0.6 / 16.0.1 | |
| Glance appwidget / material3 | 1.2.0 | |
| anthropic-java | 3.12.0 | 자체 R8 consumer rules 포함 |
| okhttp / coroutines | 5.5.1 / 1.11.0 | |

**Material 3 1.5.0 소스에서 확인한 API 상태**

| API | 1.5.0 상태 |
|---|---|
| `MaterialExpressiveTheme` | ✅ 안정 (실험 어노테이션 없음) |
| `MotionScheme.expressive()` | ✅ 안정 |
| `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | ✅ 안정 |
| `MaterialShapes`, `Morph.toPath`, `RoundedPolygon.toShape` | ⚠️ 여전히 `@ExperimentalMaterial3ExpressiveApi` |
| `expressiveLightColorScheme()` | ⚠️ 실험적 |
| `ModalBottomSheet` | ⚠️ `@ExperimentalMaterial3Api` |
| `rememberModalBottomSheetState` | 1.5.0 에서 deprecated → `rememberBottomSheetState(initialValue = Hidden)` 사용 |

## 3. 아키텍처

```
app/src/main/kotlin/app/glook/translate/
├── GlookApp.kt                  # Application + 수동 DI 컨테이너(AppContainer)
├── core/security/
│   ├── DatabaseKeyManager.kt    # SecureRandom DEK ↔ Keystore(StrongBox→TEE) AES-GCM KEK, crypto-shredding
│   └── SecureWindow.kt          # FLAG_SECURE + setRecentsScreenshotEnabled
├── domain/
│   ├── model/Models.kt          # Language, Register, TranslationUpdate, Bookmark, AppSettings …
│   └── repository/              # TranslationRepository, LibraryRepository, SettingsRepository, ApiKeyRepository
├── data/
│   ├── db/GlookDatabase.kt      # SQLCipher + SupportSQLite (Room 없음), 반응형 Flow 쿼리
│   ├── db/Vault.kt              # 지연 오픈 / 완전 파기
│   ├── repository/              # 위 인터페이스 구현 (설정·API 키까지 전부 암호화 DB)
│   ├── engine/
│   │   ├── TranslationEngine.kt         # ★ 플러그인 인터페이스
│   │   ├── HybridTranslationRouter.kt   # 감지 → 화법 판별 → 클라우드 스트리밍 vs 온디바이스 경주
│   │   ├── MlKitOnDeviceEngine.kt       # 기본 엔진 (오프라인)
│   │   ├── AnthropicEngine.kt           # BYOK (공식 anthropic-java SDK, SSE)
│   │   ├── OpenAiCompatibleEngine.kt    # BYOK (/chat/completions SSE 호환 제공자)
│   │   ├── RegisterDetector.kt          # 반말/해요체/하십시오체/비즈니스/전문용어 규칙 판별
│   │   └── PromptBuilder.kt             # Zero-Click 화법 매칭 프롬프트 (번역문만 출력)
│   ├── speech/                  # TtsController(onRangeStart 진행률), SpeechInputController(온디바이스 우선)
│   ├── ocr/OcrEngine.kt         # ML Kit Text Recognition v2 (스크립트별)
│   └── phonetic/                # ICU 로마자/병음 (오프라인)
├── ui/
│   ├── theme/Theme.kt           # MaterialExpressiveTheme + MotionScheme.expressive() + Dynamic Color
│   ├── components/              # MorphSwapButton(Shape Morph+햅틱), TtsWaveControl(Wavy), LanguagePill …
│   ├── common/TranslatorViewModel.kt  # MVI: 단일 UiState, 250ms 디바운스, collectLatest 취소
│   ├── main/                    # MainActivity + Bento MainScreen
│   ├── processtext/             # ACTION_PROCESS_TEXT 투명 액티비티 + ModalBottomSheet
│   ├── fullscreen/              # 고대비 풀스크린 카드 (Display Large, 후리가나/병음, 로마자, 밝기)
│   ├── sheet/                   # 암호화 보관함(북마크/기록 검색), 설정
│   └── icons/                   # 필요한 Rounded 아이콘만 소스 포함 (Apache-2.0)
├── tile/TranslateTileService.kt
└── widget/GlookWidget.kt        # Glance + GlanceTheme(Dynamic Color)
```

**레이어**: `ui (Compose, ViewModel)` → `domain (모델·Repository 인터페이스)` ← `data (구현)`. ViewModel 은 MVI 형태(단일 `StateFlow<UiState>` + intent 메서드). Hilt 대신 수동 DI 를 써서 KSP/코드젠 없이 빌드됩니다.

### 번역 라우터 흐름

```
입력 ─(250ms 디바운스, 새 입력 시 진행 중 스트림 즉시 취소)─▶ ML Kit 언어 감지 ─▶ RegisterDetector
  ├─ BYOK 미설정 · 오프라인 ─────────────────────────────▶ ML Kit 온디바이스 → 결과
  └─ BYOK 스트리밍 시작
       ├─ 첫 토큰 도착 → 결과 카드에 토큰 단위 실시간 렌더
       ├─ 600ms 내 첫 토큰 없음 → ML Kit 결과를 먼저 임시 표시(클라우드가 오면 교체)
       ├─ 4s 내 첫 토큰 없음 · 오류 · 거절 → 클라우드 취소, ML Kit 결과 확정
       └─ 스트리밍 중 오류 → ML Kit 결과 확정
```

외부 유입(공유·PROCESS_TEXT·음성·OCR)은 디바운스 없이 즉시 번역합니다. 감지 언어가 대상 언어와 같으면 대상이 한국어↔영어로 자동 전환됩니다.

## 4. 기본 엔진 선택과 그 이유

- **기본(고속) 엔진 = Google ML Kit 온디바이스 번역.** 요구사항의 "개발자 서버 없음·로그인 없음" 조건에서 키 없이 쓸 수 있는 번역 백엔드는 온디바이스뿐입니다. 응답이 즉시이고, 오프라인이며, 원문이 기기를 떠나지 않습니다.
- **BYOK = 화법 매칭·스트리밍 담당.** 기본 제공자는 Anthropic(공식 SDK, 모델 `claude-opus-5-5`, 지연 단축을 위해 `effort: low`). 설정에서 모델 ID 를 바꿀 수 있으며, 더 빠른 응답이 필요하면 `claude-haiku-5-5` 를 넣으면 됩니다. 거절(refusal) 대비로 서버 측 `fallbacks: "default"`(beta `server-side-fallback-2026-07-01`)를 켰고, 그래도 거절되면 라우터가 ML Kit 로 폴백합니다. OpenAI 호환 `/chat/completions` 제공자도 연결할 수 있습니다.
- 새 백엔드(DeepL, Google Cloud Translation 등)는 `TranslationEngine` 구현 + `CloudProvider` enum 항목 추가만으로 붙습니다.

## 5. 요구사항과의 편차 (정직한 고지)

1. **`androidx.security:security-crypto` 미사용.** 1.1.0 에서 `MasterKey` 등 전체 API 가 `@Deprecated`("Use KeyGenerator with AndroidKeyStore instead") — 소스 JAR 로 확인. 공식 권장대로 `javax.crypto` + `AndroidKeyStore` 를 직접 사용합니다.
2. **"EncryptedDataStore" 는 공식 AndroidX 클래스가 아닙니다.** 대신 일반 Preferences DataStore 에 **Keystore 로 이미 암호화된 DEK 암호문 + IV 만** 저장합니다(평문 키는 디스크에 기록하지 않음). 목적은 같습니다.
3. **"루팅·개발자 접근으로도 복호화 불가"는 과장입니다.** Keystore 는 키 *추출*을 막을 뿐입니다. 루팅된 기기에서 앱 프로세스 권한을 얻은 공격자는 앱 실행 중 Keystore 에 복호화를 *요청*하거나 메모리에서 평문을 읽을 수 있습니다. 보장되는 것은: (a) 파일 복사·ADB 추출만으로는 다른 기기에서 DB 를 열 수 없음, (b) KEK 에 `setUnlockedDeviceRequired(true)` 를 걸어 기기가 잠긴 동안에는 키 사용 불가, (c) 개발자 서버가 없으므로 개발자가 데이터를 받을 경로 자체가 없음.
4. **오프라인(ML Kit) 결과에는 화법 매칭이 적용되지 않습니다.** ML Kit 번역에는 톤 제어 기능이 없습니다. Zero-Click 화법 매칭은 BYOK LLM 이 있을 때만 동작합니다.
5. **일본어 한자 후리가나는 오프라인 불가.** 형태소 분석 사전이 필요합니다. BYOK 엔진이 있으면 LLM 으로 읽기를 받아 루비로 표시하고, 오프라인에서는 가나→로마자만 제공합니다. 중국어 병음은 ICU `Han-Latin` 으로 오프라인 제공(다음자는 문맥 없이 대표음이 나올 수 있음). 한국어 로마자는 ICU 규칙이라 국립국어원 표기법과 일부 다를 수 있습니다.
6. **ModalBottomSheet 의 스프링 모션.** 시트 등장 애니메이션은 테마의 MotionScheme 을 따르도록 MaterialExpressiveTheme 안에 두었지만, 시트 내부 구현이 MotionScheme 을 어디까지 사용하는지는 실기기에서 확인하지 않았습니다.
7. **음성 인식.** Android 12+ 에서 온디바이스 인식기가 있으면 그것을 쓰고, 없으면 시스템 인식기 + `EXTRA_PREFER_OFFLINE` 을 씁니다. 후자는 기기·인식 서비스에 따라 서버 인식이 될 수 있습니다.
8. **Room 대신 SupportSQLite 직접 사용**, **Hilt 대신 수동 DI** — 요구사항엔 없었지만 빌드 단순화를 위한 선택입니다.
9. **설정값도 암호화 DB 에 저장**합니다(요구사항보다 강화). 그래서 첫 화면에서 FLAG_SECURE 는 설정 로드 전까지 "켜짐"이 기본입니다.
10. **ML Kit 의 네트워크 통신.** 온디바이스 API 도 모델 다운로드를 위해 Google 서버와 통신합니다. Google 이 ML Kit 사용 지표를 수집한다는 공지가 있는 것으로 알고 있지만, 이 환경에서 developers.google.com 에 접근할 수 없어 **원문은 확인하지 못했습니다** — 출시 전 개인정보처리방침 작성 시 확인이 필요합니다.

명시적 제외 사항은 지켰습니다: 결과 카드에 `[직역|구어체|격식체]` 같은 수동 톤 버튼 없음, 문법 해설·단어 풀이 카드 없음(프롬프트에서도 "번역문만 출력"을 강제).

## 6. 보안 요약

- SQLCipher(AES-256) 단일 DB: 기록, 북마크, BYOK API 키, 설정.
- DEK 256-bit(SecureRandom) → raw-key 형식(`x'HEX'`)으로 전달(이미 무작위 키라 PBKDF2 생략) → 전달 후 바이트 배열 0 으로 덮어씀.
- KEK: AndroidKeyStore AES-256-GCM, StrongBox 우선 → 실패 시 TEE, 설정 화면에 실제 보관 위치 표시(`KeyInfo.securityLevel`).
- 완전 초기화: DB 파일 삭제 + KEK 삭제 + wrapped DEK 삭제 (crypto-shredding).
- `allowBackup=false`, `fullBackupContent=false`, `dataExtractionRules` 에서 cloud-backup·device-transfer 전 도메인 제외.
- 평문 HTTP 금지, 사용자 추가 CA 불신(`network_security_config`).
- 릴리스 빌드에서 `android.util.Log` 호출 제거, 분석 SDK 없음.
- OCR 촬영본은 인식 직후 캐시에서 삭제. CAMERA 권한은 선언하지 않고 시스템 카메라 앱에 위임.
- 위젯에는 번역 내용을 표시하지 않음(잠금 해제 없이 홈 화면 노출 방지).

## 7. 빌드 방법

```bash
# Android SDK platform "android-37.1", build-tools 36+ 필요
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## 8. 다음 단계 (권장)

1. **실기기 검증**: PROCESS_TEXT 메뉴 노출·대치, Keystore 보관 위치, 잠금 상태 동작, TTS 진행률 콜백, Shape Morph/스프링 모션 체감.
2. 온디바이스 LLM(ML Kit GenAI Prompt API / Gemini Nano, 현재 beta)을 지원 기기용 무료 화법 매칭 엔진으로 추가 검토.
3. 일본어 오프라인 후리가나(사전 기반 형태소 분석기) 도입 여부 결정 — APK 용량과 트레이드오프.
4. 계측 테스트(Compose UI 테스트, DB 암·복호화 instrumented test) 추가.
5. 앱 아이콘·스크린샷 등 스토어 자산, 개인정보처리방침(ML Kit 데이터 공지 원문 확인 포함).

---

## 2차 개선 (2026-11-24)

**디자인**: 번들 Google Sans Flex(OFL 1.1, 출처는 참고 리포 동봉본이며 업스트림 원본은 미확인 — THIRD_PARTY_NOTICES.md), `MaterialExpressiveTheme`+`MotionScheme.expressive()`, `LargeFlexibleTopAppBar`, MaterialShapes 아이콘 타일, 형태 모핑 마이크/스왑 버튼, `HorizontalFloatingToolbar`, `FloatingActionButtonMenu`, 벤토 타일, 바운시 클릭+햅틱.
**기능**: 대화 모드(상하 분할·상대 쪽 180° 회전), 이미지 번역(OCR 블록 위에 번역문 오버레이, 원문/번역 토글), 최근 언어 칩·언어 검색, 클립보드 제안(MIME/시각만 확인, 내용은 탭 후에만 읽음), 자동 읽기/복사, 오프라인 언어팩 관리, 햅틱 설정.
**텐키 입력 버그**: 추정 원인은 IME 조합 중 ViewModel 값으로 TextField 를 덮어쓰는 구조. `TextFieldState` 를 단일 진실원으로 바꿨습니다. 실제 증상은 사용자 확인 전이라 해결 여부 **미검증**.
**위젯**: Glance 1.2 Material You, 3단계 반응형(작게/가로/크게). 이전 위젯이 왜 동작하지 않았는지 원인은 **특정하지 못했습니다**(기기 실행 불가). 재작성본도 실기기 동작은 미검증.
**검증**: assembleDebug/Release, 단위 테스트 13개(Robolectric 스크린샷 5 포함), lint 통과. 에뮬레이터/실기기 실행 없음. 스크린샷은 Dynamic Color 미적용 기본 팔레트 기준.

## 3차 개선 (2026-11-24)
- **음성 오류 12**(`ERROR_LANGUAGE_NOT_SUPPORTED`): 언어 코드를 `ko-KR` 같은 전체 로케일 태그로 변환하고, 온디바이스 인식기가 12/13 으로 실패하면 시스템 기본 인식기로 재시도합니다. 이 폴백 경로는 인식기 구현에 따라 오디오가 서버로 전송될 수 있습니다. 오류 12가 이 원인이라는 것은 Android 오류 코드 정의에 근거한 추정이며 실기기 확인 전입니다.
- UI: 홈 타일 2×2, 설명문·부제 삭제, 입력창 최소 높이 확대, 배경화면이 무채색이면 동적 색 대신 기본 Expressive 팔레트 사용.
- 폴더블/대화면(Google Play adaptive quality, 2026-11-18 블로그·foldables 페이지 기준): 너비 600dp 이상 2열, `androidx.window` 로 북·테이블탑 자세 감지(대화 모드 힌지 정렬), MainActivity `standard` + 멀티 인스턴스 속성, 방향 고정 없음. 카메라 연속성·PiP 는 해당 기능이 없어 미적용. 폴더블 실기기/에뮬레이터 검증 없음.

## 4차 개선 (2026-11-24)
- **고정 팔레트**: Seed #3B6939. 사용자가 이미지로 준 토큰 값(Primary, Primary Container, Secondary, Tertiary, Surface, Surface Container High / 라이트·다크)은 그대로, 나머지 토큰은 같은 톤 계열에서 **추정**해 채웠습니다(HCT 계산 아님). 동적 색은 제거했고 위젯도 같은 팔레트를 씁니다. `ui/theme/Palette.kt`.
- **문서 번역**(홈 '문서' 타일, FAB 메뉴, 공유 시트): TXT(UTF-8/UTF-16/CP949), DOCX(word/document.xml), HWPX(Contents/section*.xml), PDF(PDFBox-Android 2.0.27.0, 텍스트 레이어 없으면 PdfRenderer+ML Kit OCR 앞 30쪽). 25MB·12만 자 상한, 문서는 메모리/캐시에서만 처리.
- 검증: 파서 단위 테스트(합성 DOCX/HWPX/TXT/PDF) 통과. **실제 한글(HWPX)·Word 파일로는 시험하지 못했습니다.** HWPX 는 공개된 파서들의 구조(hp:p/hp:t)를 참고해 작성. PDFBox-Android 는 마지막 릴리스가 2023-01 입니다. 릴리스(R8) 빌드는 컴파일만 확인, 실행 미검증.
- 스캔 PDF 는 OCR 이라 정확도가 낮고, 표·다단·이미지 속 글자는 순서가 어긋나거나 빠질 수 있습니다. 번역 결과는 txt 로만 저장(원본 서식 유지 안 함).

## 5차 개선 (2026-11-24)
- 홈: FAB 메뉴 제거, 스크롤 없는 고정 레이아웃(긴 글은 카드 안에서만 스크롤), 2×2 타일 아래 작고 긴 '오프라인 언어팩' 줄, 상단바는 작은 TopAppBar. 이미지 타일은 이미지 화면에서 카메라/갤러리를 고르게 함(위젯의 카메라 버튼은 기존대로 바로 카메라).
- 문서 화면: 홈과 같은 공용 `LanguageBar`(원문·스왑·대상) 사용, 텍스트 버튼은 공용 `ActionButton` 한 종류로 통일(언어 선택·원문 함께·txt 저장).
- 한 화면 맞춤은 Robolectric 스크린샷(w411dp-h891dp)에서만 확인. 키보드가 올라오면 타일은 숨깁니다. 작은 화면(예: 높이 700dp 이하)에서의 모습은 미확인.

## 6차 개선 — 용량 (2026-11-24)
- 이전에 드린 APK 는 **디버그 빌드(148MB)** 였습니다. 릴리스(R8·리소스 축소) 빌드는 50MB 였고, 아래 조치 후 **29MB**(arm64).
- 조치: (OCR 비번들 전환은 사용자 요청으로 취소 — OCR 모델은 앱에 번들) 쓰지 않는 BouncyCastle PQC 리소스 제외, 폰트를 wght 300–700 축만 남긴 사본으로 축소(4.1MB→0.28MB, 변경본 고지 THIRD_PARTY_NOTICES.md).
- 남은 큰 항목: ML Kit 번역 네이티브(libtranslate_jni.so 16MB, 번들 전용), classes.dex 7MB, SQLCipher 2MB. 번역 언어 모델은 앱 외부에서 따로 받음(언어당 수십 MB, 기기 저장공간에 별도 차지).
- 릴리스 APK 는 기기의 디버그 키로 서명(사이드로드용, Play 배포용 아님). 키 파일은 /mnt/project-files/glook-signing/ 에 보관해 다음 빌드도 같은 서명으로 덮어 설치되게 함.
- R8 릴리스 빌드의 실행은 **미검증**(컴파일·테스트·lint 만 통과).

## 7~8차 — 릴리스 실행 실패 대응 (2026-11-24)
- 사용자 보고: 릴리스 APK(R8 난독화·최적화·리소스 축소)가 실행되지 않음. 기기가 없어 **원인을 재현하지 못했습니다**(정적 분석으로는 ViewModel 생성자·ML Kit 레지스트라·DataStore protobuf 규칙이 유지되는 것까지만 확인).
- 임시 조치: 릴리스에서 난독화·최적화·리소스 축소를 끄고 코드 축소만 수행(`proguard-rules.pro`, `android.enableResourceOptimizations=false`). 54.5MB. 이 빌드의 실행 여부도 **미검증**.
- 계속 실패하면 `adb logcat -d | grep -E "AndroidRuntime|glook"` 또는 크래시 화면이 필요합니다.

## 9차 (2026-11-24)
- **크래시 보고 화면**: 앱이 죽으면 별도 프로세스(:crash)의 단순 View 화면에 스택 트레이스를 표시(복사·공유 버튼). 릴리스가 시작하자마자 종료되는 원인을 USB 디버깅 없이 확인하기 위한 임시 장치. 외부 전송 없음.
- **이미지 번역 카메라**: 시스템 카메라 대신 앱 내 카메라(CameraX 1.7.0) + 화면 아래 최근 사진 줄(탭하면 바로 번역) + 갤러리(시스템 사진 선택기). CAMERA·사진 읽기 권한을 요청하며, 거부해도 선택기로는 동작. 실기기 미검증(Robolectric 에서 카메라 시험 불가).
- APK: 릴리스 56.5MB(난독화·최적화·리소스 축소 해제 상태), 디버그 150.8MB.

## 10차 — 릴리스 시작 크래시 원인 (2026-11-24)
- 크래시 보고 화면으로 확보한 스택: `InitializationProvider → WorkManagerInitializer → Room.getGeneratedImplementation: Failed to create an instance of androidx.work.impl.WorkDatabase` (Android 16, API 36).
- 원인(추정이지만 R8 산출물로 확인): R8 full mode 에서 room-runtime 2.2.5 의 `-keep class * extends androidx.room.RoomDatabase` 가 기본 생성자를 유지하지 않아 `WorkDatabase_Impl.<init>()` 이 제거됨(usage.txt 에 제거 기록, 수정 후 seeds.txt 에 `WorkDatabase_Impl()` 유지 확인). WorkManager 는 Glance 위젯 의존성으로 포함.
- 조치: `proguard-rules.pro` 에 RoomDatabase `<init>()`, InputMerger `<init>()`, ListenableWorker `<init>(Context, WorkerParameters)` keep 추가. 난독화·최적화·리소스 축소는 아직 꺼 둔 채(56.5MB). 실기기에서 뜨는 것을 확인한 뒤 다시 켜서 용량을 줄일 예정.
- 같은 부류(생성자만 지워진 리플렉션 대상)를 찾기 위해 usage.txt/seeds.txt 를 대조했고 위 3종 외 앱에 영향 있는 항목은 찾지 못했으나, 실행 검증은 아님.

## 11차 — 릴리스 크래시 2 (2026-11-24)
- 스택: `GlookApp.onCreate → MlKitOnDeviceEngine.<init> → LanguageIdentification.getClient → NPE (LanguageIdentifierImpl$Factory null)`. ML Kit 컴포넌트 등록이 R8 축소로 깨짐.
- 조치: ML Kit·Firebase components·androidx.startup·WorkManager·androidx.window·CameraX 패키지를 통째로 keep. 무축소(`-PnoMinify`) 릴리스는 125MB 라서 채택하지 않음. 현재 56.7MB.
- 한계: 이런 부류(리플렉션 대상이 R8 에 지워짐)가 더 있을 수 있고, 기기 실행으로 확인하지 못했습니다. 크래시 보고 화면은 유지합니다.

## 12차 (2026-11-24)
- 색: Android 12+ 는 기기(배경화면) 테마 색, 그 외·설정("기기 테마 색 사용") off 는 Glook 기본 팔레트. 기본 팔레트는 Seed #3B6939 로 Material Color Utilities(TonalSpot 2021, 파이썬 포트로 계산)에서 전 토큰을 산출, tertiary 만 사용자 지정 살몬 유지. 홈 타일은 primary/secondary/tertiary 컨테이너 역할 색만 사용(회색 서피스 제거).
- 카메라: 최근 사진 줄 제거. 핀치 줌, 줌 프리셋(기기가 CameraX에 노출한 배율 범위 안에서), 탭 초점, 셔터 왼쪽 최근 사진 미리보기(누르면 시스템 사진 선택기). 실기기 미검증.
- `-PnoSecure` 빌드: BuildConfig.ALLOW_SECURE=false → FLAG_SECURE 미적용 (apk/…-nosecure.apk). 일반 빌드는 기존대로 설정("화면 캡처 차단") 기반.

## 14차 (2026-11-24)
- 와이드 홈: 같은 높이의 헤더 줄(왼쪽 언어 선택 | 오른쪽 기능 타일 4개) 아래에 같은 높이의 입력·결과 카드. 최근 언어는 제목 줄로, 오프라인 언어팩은 결과가 없을 때 오른쪽 카드 하단에. 하드웨어 키보드가 있으면 입력창에 자동 포커스.
- 테마: "기기 테마 색 사용" 토글 제거. Android 12+ 는 항상 기기 테마 색, 12 미만은 Glook 기본 팔레트.

## 15차 (2026-11-24)
- 와이드 홈 재설계: 맨 위 가로 스크롤 한 줄(대화·이미지·음성·문서·오프라인 언어팩), 그 아래 언어 선택(원문 | 교체 | 번역)이 각 카드 바로 위, 그 아래 같은 높이의 입력·결과 카드. 최근 언어는 제목 줄.
- 번역 언어 24→59개: ML Kit translate 17.0.3 의 TranslateLanguage 상수 전체. 언어 이름은 앱 언어(Locale)로 표시.
- 앱 UI 10개 언어(ko/en/ja/zh-CN/es/fr/de/pt/ru/vi): 기본(values)=영어, values-ko=한국어. 문자열은 `core/Str.kt` 의 `str(R.string.x)` 로 접근. Android 13+ 앱별 언어 설정(locales_config) 지원, 12 이하는 시스템 언어. `localeFilters` 로 그 외 언어 리소스 제외. **번역문은 모델(Claude)이 작성, 원어민 검수 없음.** 키·포맷 자리표시자 일치는 LocalizationTest 로 검증.
- OCR(번들 인식기: 라틴·한·일·중)은 키릴·아랍·데바나가리 문자 등은 인식하지 못함(번역 언어를 늘려도 이미지 번역 지원 범위는 그대로).
