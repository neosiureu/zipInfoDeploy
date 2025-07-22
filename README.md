#  ZipInfo - 부동산 정보 통합 플랫폼

<div align="center">
  <img src="https://img.shields.io/badge/Platform-Real%20Estate-blue?style=for-the-badge" alt="Platform">
  <img src="https://img.shields.io/badge/Status-Active-brightgreen?style=for-the-badge" alt="Status">
  <img src="https://img.shields.io/badge/Auth-Kakao%20%7C%20Naver-yellow?style=for-the-badge" alt="Authentication">
</div>

##  프로젝트 소개

**부동산은 언제나 대한민국의 핵심 이슈였으며, 현재진행형이고 미래입니다.**

ZipInfo는 복잡한 부동산 시장에서 투명하고 신뢰할 수 있는 정보를 제공하는 통합 플랫폼입니다. 다양한 시공사의 분양정보부터 중개자의 실거래 정보까지, 부동산과 관련된 모든 정보를 한 곳에서 만날 수 있습니다.

---

##  주요 기능

###  **스마트 인증 시스템**
- **OAuth 2.0 소셜 로그인**: 카카오, 네이버 간편 로그인 지원
- **Kakao SDK**: JavaScript SDK를 활용한 매끄러운 로그인 UX
- **전문가 인증**: 중개자 등록번호 검증을 통한 신뢰성 보장
- **이메일 인증**: 중개자 회원가입 시 필수 본인확인

###  **고급 에디터 & 콘텐츠 관리**
- **Summernote**: WYSIWYG 에디터로 풍부한 게시글 작성
- **Toast UI**: 직관적인 사용자 인터페이스 컴포넌트
- 이미지 업로드 및 멀티미디어 콘텐츠 지원
- 실시간 게시글 미리보기

###  **우리동네 게시판**
- 전국 시·군·구별 지역 정보 공유
- **스마트 알림**: 관심 지역 설정 시 새 게시글 자동 알림
- 지역 커뮤니티 기반의 생생한 정보 교환

###  **분양 정보 센터**
- 전국 분양 정보 통합 제공
- 시공사별 분양 일정 및 조건 안내
- 실시간 분양 현황 업데이트

###  **실거래가 정보**
- **일반 사용자**: 실거래 가능 매물 정보 열람
- **중개자**: 보유 매물 등록·수정·삭제 관리
- 투명한 거래 정보 제공으로 시장 신뢰도 향상

###  **지도 기반 서비스 & 공공데이터 연동**
- **카카오맵 API** 연동으로 직관적인 위치 정보 제공
- **V-World API**: 공간정보 오픈플랫폼 연동으로 정확한 지역 정보
- **공공데이터포털**: 정부 공식 부동산 데이터 실시간 연동
- **시군구 조회 시스템**: 전국 행정구역별 세밀한 정보 제공
- 분양 및 실거래 정보의 지도상 시각화
- 주변 시설 및 교통 접근성 정보

###  **관리자 대시보드**
- 회원 관리 및 게시글 모니터링
- 회원 및 거래 매물 통계 분석
- **중개자 검증 시스템**: 실제 중개 등록번호 확인을 통한 사이트 무결성 보장

---

##  신뢰성과 보안

ZipInfo는 사용자의 안전한 부동산 거래를 위해 다음과 같은 검증 시스템을 운영합니다:

- **2단계 중개자 인증**: 이메일 인증 + 관리자 승인
- **실명 기반 거래**: 허위 정보 방지를 위한 본인 인증
- **지속적인 모니터링**: 관리자의 실시간 콘텐츠 관리

---

##  기술 스택

### Frontend
<div align="center">
  <img src="https://img.shields.io/badge/Vite-646CFF?style=flat-square&logo=vite&logoColor=white" alt="Vite">
  <img src="https://img.shields.io/badge/React-61DAFB?style=flat-square&logo=react&logoColor=white" alt="React">
  <img src="https://img.shields.io/badge/Toast%20UI-515ce6?style=flat-square&logo=nhn&logoColor=white" alt="Toast UI">
  <img src="https://img.shields.io/badge/Summernote-FF8C00?style=flat-square&logoColor=white" alt="Summernote">
</div>

### Backend
<div align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Oracle-F80000?style=flat-square&logo=oracle&logoColor=white" alt="Oracle">
  <img src="https://img.shields.io/badge/OAuth%202.0-4285F4?style=flat-square&logo=google&logoColor=white" alt="OAuth">
</div>

### External APIs
<div align="center">
  <img src="https://img.shields.io/badge/Kakao%20Map-FFCD00?style=flat-square&logo=kakao&logoColor=black" alt="Kakao Map">
  <img src="https://img.shields.io/badge/Kakao%20OAuth-FFCD00?style=flat-square&logo=kakao&logoColor=black" alt="Kakao OAuth">
  <img src="https://img.shields.io/badge/Naver%20OAuth-03C75A?style=flat-square&logo=naver&logoColor=white" alt="Naver OAuth">
  <img src="https://img.shields.io/badge/V--World%20API-0066CC?style=flat-square&logoColor=white" alt="V-World API">
  <img src="https://img.shields.io/badge/공공데이터포털-336699?style=flat-square&logoColor=white" alt="Public Data Portal">
</div>

### Infrastructure & Deployment
<div align="center">
  <img src="https://img.shields.io/badge/AWS-FF9900?style=flat-square&logo=amazonaws&logoColor=white" alt="AWS">
  <img src="https://img.shields.io/badge/Domain-zipinfo.site-blue?style=flat-square&logoColor=white" alt="Domain">
</div>

---

##  서비스 화면

| 메인 페이지 | 분양 정보 | 실거래 정보 | 지역 게시판 |
|:---:|:---:|:---:|:---:|
| ![메인](placeholder) | ![분양](placeholder) | ![실거래](placeholder) | ![게시판](placeholder) |

---

##  향후 계획

- [ ] 모바일 앱 버전 개발
- [ ] AI 기반 시세 예측 서비스
- [ ] 부동산 투자 분석 도구 추가
- [ ] 실시간 채팅 상담 서비스

---

##  문의 및 지원

부동산 정보의 새로운 패러다임, **ZipInfo**를 많이 이용해 주세요!

 **서비스 바로가기**: [zipinfo.site](https://www.zipinfo.site)

<div align="center">
  <a href="https://www.zipinfo.site"><img src="https://img.shields.io/badge/Website-zipinfo.site-blue?style=for-the-badge" alt="Website"></a>
  <a href="mailto:contact@zipinfo.site"><img src="https://img.shields.io/badge/Email-contact@zipinfo.site-red?style=for-the-badge" alt="Email"></a>
  <a href="#"><img src="https://img.shields.io/badge/KakaoTalk-@zipinfo-yellow?style=for-the-badge" alt="KakaoTalk"></a>
</div>

---

<div align="center">
  <sub>Built with ❤️ for Korean Real Estate Market</sub><br>
  <sub>© 2024 ZipInfo. All rights reserved.</sub>
</div>
