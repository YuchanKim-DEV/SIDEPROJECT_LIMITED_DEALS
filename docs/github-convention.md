# GitHub Convention

LIMITED_DEALS 프로젝트의 Git / GitHub 작업 규칙이다. 개발 용어와 타입은 영어, 설명은 한글로 쓴다.

## 1. Branch (브랜치)

GitHub Flow를 단순하게 쓴다.

- `main`: 항상 빌드가 되는 상태로 유지한다. `main`에 직접 커밋하지 않는다.
- 작업 브랜치: `main`에서 만들고 PR(Pull Request)로 합친다.

### 이름 규칙

```
타입/설명/시작날짜
```

- 타입: 아래 커밋 타입과 같다.
- 설명: 영어 kebab-case (소문자와 하이픈)
- 시작날짜: `YYYY-MM-DD`

```
feat/product-crud/2026-10-12
fix/deal-price-calc/2026-10-15
docs/github-convention/2026-10-12
```

## 2. Commit (커밋)

Conventional Commits 형식을 따른다.

```
타입(범위): 제목

본문 (선택)
```

- 제목은 한글로 쓰고, 50자 이내로 쓰며, 마침표를 붙이지 않는다.
- 본문에는 무엇을 바꿨는지보다 **왜** 바꿨는지를 쓴다.
- 범위(scope)는 `product`, `deal`, `auth`처럼 바뀐 도메인이나 모듈 이름이다. 생략할 수 있다.

### 타입

| 타입 | 용도 |
|---|---|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `hotfix` | `main`에 반영된 긴급 버그 수정 |
| `changes` | 기능 변경 (동작이 바뀌는 수정) |
| `refactor` | 동작은 그대로, 코드 구조 개선 |
| `test` | 테스트 추가 / 수정 |
| `docs` | 문서 수정 |
| `chore` | 설정, 잡일 (기능과 무관) |
| `build` | 빌드 설정, 의존성 변경 |

예시: `feat(product): 상품 등록 API 추가`

## 3. Pull Request (PR)

- 작업 브랜치 하나당 PR 하나를 만든다.
- 제목은 커밋 형식과 같다: `타입(범위): 제목`
- 본문은 `.github/pull_request_template.md` 템플릿을 따른다.
- 머지 방식은 **Create a merge commit**을 쓴다. 작업 브랜치의 커밋이 그대로 `main`에 남고, 합쳤다는 머지 커밋이 하나 추가된다.
- 작업 브랜치의 커밋도 `main`에 그대로 남으므로, 커밋 메시지는 위 커밋 규칙을 지켜서 쓴다.
- 머지 후에는 작업 브랜치를 삭제한다.

### 리뷰 코멘트

혼자 작업하므로 스스로 코멘트를 달고, 머지 전에 Claude에게 리뷰를 맡긴다.

- 코드의 특정 줄에 "왜 이렇게 했는지"를 코멘트로 남긴다.
- 코멘트 앞에 접두사를 붙인다.

| 접두사 | 뜻 |
|---|---|
| `[Must]` | 머지 전에 반드시 고쳐야 함 |
| `[Suggestion]` | 더 나은 방법 제안 |
| `[Question]` | 궁금한 점 |
| `[Note]` | 참고용 메모 |

## 4. Issue (이슈, 선택)

- 하나의 작은 작업 덩어리(예: 상품 CRUD 구현)를 이슈로 만들 수 있다. 필수는 아니다.
- 이슈가 있으면 PR 본문에 `Closes #번호`를 써서 연결한다. 머지되면 이슈가 자동으로 닫힌다.

## 5. Claude 전용 파일

Claude가 참고하는 `md`, `txt` 파일(`CLAUDE.md`, `cluade.md` 등)은 반드시 `.gitignore`에 추가한다.
