# Contributing to Order-Payment Microservices

먼저, 이 프로젝트에 기여하는 것을 고려해 주셔서 감사합니다! 🎉

## 개발 환경 설정

1. **저장소 Fork**
   ```bash
   # GitHub에서 Fork 후
   git clone https://github.com/YOUR_USERNAME/order-payment-msa.git
   cd order-payment-msa
   ```

2. **로컬 개발 환경 구축**
   ```bash
   ./start.sh
   ```

3. **브랜치 생성**
   ```bash
   git checkout -b feature/your-feature-name
   ```

## 코드 스타일

### Java
- Google Java Style Guide 준수
- Checkstyle 설정 적용
- Lombok 사용 권장

```bash
# Checkstyle 검사
mvn checkstyle:check
```

### Commit 메시지
Conventional Commits 형식을 따릅니다:

```
feat: Add new feature
fix: Fix bug
docs: Update documentation
test: Add tests
refactor: Refactor code
chore: Update dependencies
```

예시:
```
feat: Add idempotency check in PaymentService

- Add ProcessedEvent entity
- Implement duplicate payment prevention
- Add integration test for idempotency
```

## Pull Request 프로세스

1. **테스트 작성**
   - 새로운 기능에는 반드시 테스트 추가
   - 통합 테스트 작성 권장

2. **코드 리뷰 요청**
   - PR 설명에 변경사항 상세히 기재
   - 관련 Issue 링크 추가

3. **CI 통과 확인**
   - 모든 테스트 통과
   - Checkstyle 통과
   - 빌드 성공

## 버그 리포트

GitHub Issues를 사용하여 버그를 보고해 주세요.

**포함해야 할 정보**:
- 버그 재현 단계
- 예상 동작
- 실제 동작
- 환경 정보 (OS, Java 버전 등)
- 로그 또는 스크린샷

## 기능 제안

새로운 기능 제안도 환영합니다!

**포함해야 할 정보**:
- 기능 설명
- 사용 사례
- 가능하다면 구현 아이디어

## 질문하기

- GitHub Discussions 사용
- 또는 Issue에 `question` 라벨 추가

## 라이선스

이 프로젝트에 기여함으로써, 귀하의 기여가 MIT 라이선스 하에 있음에 동의합니다.
