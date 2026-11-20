# 순서 1 의존성 묶음 기록 (2026-11-20)

만들기 설계안 4판 4.3절의 기록이다. 공용 모듈(`zipinfo-project/ai-core`)과 실험 실행기(`experiment/harness`)가 쓰는 외부 의존성을 사용자 PC에서 모았다.

- 묶음 zip은 저장소에 넣지 않는다.
- 판은 두 빌드의 `gradle.lockfile`이 고정한다.
- 파일 바이트는 두 빌드의 `gradle/verification-metadata.xml`(103개)과 이 폴더의 `SHA256SUMS.txt`(129개 모두)가 고정한다. 표지 POM 26개는 `SHA256SUMS.txt`에만 있다.

## 모은 것

- Anthropic Java SDK 3.11.0(API 판 2026-10-21)과 JUnit 5.14.4(BOM, launcher 1.14.4), 그리고 둘이 끌어오는 것이다. 잠금 파일 기준 외부 모듈은 44개다.
- 묶음의 `m2/`에는 구성요소 61개, 파일 129개가 있다.
    - POM 61개. 그 가운데 26개는 `.module`로 넘기는 표지 POM이다(11/20 고친 것, 아래).
    - jar 40개, `.module` 28개.

## 모은 방법

- 이 폴더의 `build.gradle`·`settings.gradle`을 저장소 밖 `C:\workspace\ai_deps_1120`에 풀고, 백엔드 `gradlew.bat`으로 돌렸다.
    - Gradle 8.14.2, Eclipse Temurin 21.0.13, Windows 11
    - `--write-locks --write-verification-metadata sha256 resolveAll`
    - `zipRepo`
- 모은 때는 2026-11-20 09:19:16(한국 시각)이다.
- 결과 zip
    - 이름: `ai-deps_anthropic-java-3.11.0.zip`
    - 크기: 52,526,066바이트
    - SHA-256: `11564478584D9020022DAE2308FA8640D40E9A43C42E369726AADEDCDA1CDD40`
- 묶음 안 파일마다의 SHA-256은 이 폴더의 `SHA256SUMS.txt`(129줄)에 있다.
- 11/20 고친 것: Gradle 모듈 메타데이터로 낸 라이브러리의 표지 POM은 `verification-metadata.xml`에 적히지 않는다. 그래서 `zipRepo`가 목록에 없는 자기 POM을 캐시에서 함께 옮기게 했다(`build.gradle` 100~113줄).

## 묶음과 저장소 파일

- `zipinfo-project/ai-core/gradle.lockfile`과 `experiment/harness/gradle.lockfile`은 묶음의 `gradle.lockfile`과 같다. 줄 끝만 LF로 바꿨다.
    - 두 빌드는 이 작업 공간에서 묶음만으로 오프라인으로 풀어 잠금을 새로 썼는데, 묶음 것과 같게 나왔다.
- 두 빌드의 `gradle/verification-metadata.xml`도 묶음의 것과 같다. 이것도 줄 끝만 LF로 바꿨다.

## 짓는 법

- **Maven Central에 닿는 곳(사용자 PC):** 그냥 짓는다. 잠금 파일과 해시로 같은 판, 같은 바이트를 쓴다.

```powershell
C:\workspace\zipInfoDeploy\zipinfo-project\backend\gradlew.bat -p experiment\harness build
```

- **닿지 않는 곳:** 묶음을 풀고 먼저 `SHA256SUMS.txt`로 129개를 모두 대 본다.
    - 그다음 `m2` 폴더의 **절대 경로**를 `-PaiDepsRepo`로 넘기고 `--offline`을 붙인다.
    - 상대 경로는 Gradle 데몬의 작업 폴더를 기준으로 읽혀 묶음을 찾지 못한다.

```sh
# 묶음을 푼 폴더에서
sha256sum -c SHA256SUMS.txt
# 저장소 뿌리에서
gradle --offline -PaiDepsRepo=/absolute/path/to/m2 -p experiment/harness build
```

- 목록(`verification-metadata.xml`)에 든 103개는 한 바이트라도 다르면 Gradle이 "Dependency verification failed"로 멈춘다.
- 표지 POM 26개는 Gradle이 대 보지 않는다. Gradle은 그 POM에서 표지만 읽고, 의존성은 대 본 `.module`에서 읽는다. 그래서 위 첫 줄로 먼저 대 본다.
