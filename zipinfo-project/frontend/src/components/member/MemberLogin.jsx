import React, { memo, useContext, useEffect, useState } from "react";
import { axiosAPI } from "../../api/axiosApi";
import "../../css/member/MemberLogin.css";
import { MemberContext } from "../member/MemberContext";
import NaverCallback from "../auth/NaverCallback";
import { useNavigate } from "react-router-dom";
import { toast } from "react-toastify";

import Kakao from "../../assets/kakao-talk-icon.svg";
import Naver from "../../assets/naver-icon.svg";

function openNaverPopup() {
  const url = new URL("https://nid.naver.com/oauth2.0/authorize");
  url.searchParams.set("response_type", "token");
  url.searchParams.set("client_id", import.meta.env.VITE_NAVER_CLIENT_ID);
  url.searchParams.set("redirect_uri", import.meta.env.VITE_NAVER_CALLBACK_URI);
  url.searchParams.set("state", crypto.randomUUID());
  url.searchParams.set("auth_type", "reauthenticate");
  
  console.log("🔵 네이버 URL:", url.toString());
  
  // 팝업 옵션 개선
  const popupOptions = [
    "width=500",
    "height=640", 
    "scrollbars=yes",
    "resizable=yes",
    "status=yes",
    "location=yes",
    "toolbar=no",
    "menubar=no",
    "left=" + (screen.width / 2 - 250),
    "top=" + (screen.height / 2 - 320)
  ].join(",");
  
  return window.open(url.toString(), "naverLogin", popupOptions);
}

export default function MemberLogin() {
  useEffect(() => {
    localStorage.removeItem("loginMember");
    localStorage.removeItem("com.naver.nid.access_token");
    localStorage.removeItem("com.naver.nid.oauth.state_token");
  }, []);
  const { VITE_KAKAO_REST_API_KEY, VITE_KAKAO_REDIRECT_URI } = import.meta.env;
  const navigate = useNavigate();

  const { setMember } = useContext(MemberContext);

  const [formData, setFormData] = useState({
    email: "", // 초기화용
    password: "", // 초기화용
    saveId: false,
  });

  const handleFindPassword = () => {
    navigate("/findPassword");
  };

  // 입력값 제어
  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));
  };

  // 그냥 로그인
  const handleSubmit = async (e) => {
    console.log("🔍 axiosAPI.defaults:", axiosAPI.defaults);
    console.log("🔍 axiosAPI.defaults.baseURL:", axiosAPI.defaults.baseURL);
    // 클라이언트 측 검증 추가
    if (!formData.email.trim()) {
      toast.error("이메일을 입력해주세요.");
      return;
    }

    if (formData.email.length > 50) {
      toast.error("이메일은 50자 이내로 입력해주세요.");
      return;
    }

    if (!formData.password.trim()) {
      toast.error("비밀번호를 입력해주세요.");
      return;
    }

    if (formData.password.length < 6 || formData.password.length > 20) {
      toast.error("비밀번호는 6~20자 사이로 입력해주세요.");
      return;
    }
    console.log("현재 BASE URL:", import.meta.env.VITE_API_BASE_URL);
    console.log("현재 도메인:", window.location.origin);
    try {
      const resp = await axiosAPI.post("/member/login", {
        memberEmail: formData.email, //  DTO 필드명과 동일
        memberPw: formData.password,
      });

      // 200 OK
      const { loginMember, accessToken } = resp.data; // 백엔드가 돌려준 Member

      // 아이디 저장 check 후 localStorage를 뒤져보는 경우
      if (formData.saveId) {
        localStorage.setItem("saveId", formData.email);
      } else {
        localStorage.removeItem("saveId");
      }

      // 로그인 정보 저장
      localStorage.setItem("accessToken", accessToken);
      localStorage.setItem("loginMember", JSON.stringify(loginMember));
      setMember(loginMember);

      if (loginMember.memberAuth == 2) {
        toast.error(`당신은 중개사 자격이 없는 중개자입니다. `);
      }

      navigate("/"); //router 사용하여 메인페이지로 이동
    } catch (err) {
      if (err.response?.status === 401) {
        toast.error("이메일 또는 비밀번호가 다릅니다.");
      } else {
        console.error(err);
        toast.error("로그인 중 오류가 발생했습니다.");
      }
    }
  };

  const handleSubmitEnter = async (e) => {
    if (e.key === "Enter") {
      // 클라이언트 측 검증 추가
      if (!formData.email.trim()) {
        toast.error("이메일을 입력해주세요.");
        return;
      }

      if (formData.email.length > 50) {
        toast.error("이메일은 50자 이내로 입력해주세요.");
        return;
      }

      if (!formData.password.trim()) {
        toast.error("비밀번호를 입력해주세요.");
        return;
      }

      if (formData.password.length < 6 || formData.password.length > 20) {
        toast.error("비밀번호는 6~20자 사이로 입력해주세요.");
        return;
      }
      console.log("현재 BASE URL:", import.meta.env.VITE_API_BASE_URL);
      console.log("현재 도메인:", window.location.origin);
      try {
        const resp = await axiosAPI.post("/member/login", {
          memberEmail: formData.email, //  DTO 필드명과 동일
          memberPw: formData.password,
        });

        // 200 OK
        const { loginMember, accessToken } = resp.data; // 백엔드가 돌려준 Member

        // 아이디 저장 check 후 localStorage를 뒤져보는 경우
        if (formData.saveId) {
          localStorage.setItem("saveId", formData.email);
        } else {
          localStorage.removeItem("saveId");
        }

        // 로그인 정보 저장
        localStorage.setItem("accessToken", accessToken);
        localStorage.setItem("loginMember", JSON.stringify(loginMember));
        setMember(loginMember);

        if (loginMember.memberAuth == 2) {
          toast.error(`당신은 중개사 자격이 없는 중개자입니다. `);
        }

        navigate("/"); //router 사용하여 메인페이지로 이동
      } catch (err) {
        if (
          err.response?.status === 401 &&
          err.response?.data?.msg === "WITHDRAW_14D"
        ) {
          toast.error("탈퇴 후 14일 동안은 재가입할 수 없습니다.");
          return; // 더 이상 처리 안함
        }

        if (err.response?.status === 401) {
          toast.error("이메일 또는 비밀번호가 다릅니다.");
        } else {
          console.error(err);
          toast.error("로그인 중 오류가 발생했습니다.");
        }
      }
    }
  };

  const KAKAO_AUTH_URL =
    `https://kauth.kakao.com/oauth/authorize` +
    `?response_type=code` +
    `&client_id=${VITE_KAKAO_REST_API_KEY}` +
    `&redirect_uri=${encodeURIComponent(VITE_KAKAO_REDIRECT_URI)}`;

  // 회원가입
  const handleSignUp = () => {
    navigate("/signUp"); //router 사용
  };

  // 카카오 로그인
  const handleKakaoLogin = () => {
    localStorage.removeItem("loginMember");
    localStorage.removeItem("accessToken");
    setMember(null);

    window.Kakao.Auth.loginForm({
      scope: "profile_nickname,account_email",
      success: async (authObj) => {
        try {
          const { data } = await axiosAPI.post("/oauth/kakao", {
            code: authObj.access_token,
          });
          const { loginMember, accessToken } = data; // 백엔드 응답 키와 동일
          localStorage.setItem("accessToken", accessToken);
          localStorage.setItem("loginMember", JSON.stringify(loginMember));
          setMember(loginMember);
          navigate("/");
        } catch (err) {
          if (
            err.response?.status === 403 &&
            err.response?.data?.msg === "MEMBER_WITHDRAWN"
          ) {
            alert("탈퇴한 회원은 로그인할 수 없습니다.");
            return;
          }
          console.error("카카오 로그인 처리 중 에러", err);
          toast.error("로그인 중 오류가 발생했습니다.");
        }
      },
      fail: (err) => {
        console.error("카카오 로그인 실패", err);
        toast.error("로그인에 실패했습니다.");
      },
    });
  };

  // 네이버 로그인
// 네이버 로그인
const handleNaverLogin = () => {
  console.log("네이버 로그인 시작");
  console.log("환경변수 체크:");
  console.log("- CLIENT_ID:", import.meta.env.VITE_NAVER_CLIENT_ID?.substring(0, 5) + "...");
  console.log("- CALLBACK_URI:", import.meta.env.VITE_NAVER_CALLBACK_URI);
  console.log("- 현재 도메인:", window.location.origin);
  
  // 브라우저 정보
  console.log("브라우저 정보:");
  console.log("- User Agent:", navigator.userAgent.substring(0, 50) + "...");
  console.log("- 팝업 차단 확인 전 상태");
  
  // 기존 상태 체크 (변수 정의)
  const existingToken = localStorage.getItem("accessToken");
  const existingMember = localStorage.getItem("loginMember");
  console.log("기존 상태:");
  console.log("- 기존 토큰 존재:", !!existingToken);
  console.log("- 기존 멤버 존재:", !!existingMember);
  
  // (1) 초기화
  localStorage.removeItem("accessToken");
  localStorage.removeItem("loginMember");
  setMember(null);

  // 팝업 로그 리스너 추가
  const logListener = (e) => {
    if (e.origin !== window.location.origin) return;
    if (e.data?.type === "NAVER_LOG") {
      const { level, message, data, timestamp } = e.data;
      console[level](`[PopupLog ${timestamp}] ${message}`, data || '');
    }
  };
  window.addEventListener("message", logListener);

  // (2) 팝업 차단 체크
  const testPopup = window.open('', '_blank', 'width=1,height=1');
  console.log("팝업 테스트 결과:", {
    popup: !!testPopup,
    closed: testPopup?.closed,
    location: testPopup?.location?.href
  });
  
  if (!testPopup || testPopup.closed) {
    console.log("팝업 차단됨 - 상세 정보:");
    console.log("- testPopup null:", testPopup === null);
    console.log("- testPopup undefined:", testPopup === undefined);
    console.log("- testPopup.closed:", testPopup?.closed);
    window.removeEventListener("message", logListener);
    toast.error("팝업이 차단되었습니다. 팝업 차단을 해제해주세요.");
    return;
  }
  testPopup.close();
  console.log("팝업 테스트 통과");

  // pollCount 변수 정의
  let pollCount = 0;

  // (3) 타임아웃 설정 (30초)
  const timeout = setTimeout(() => {
    console.log("네이버 로그인 타임아웃 발생:");
    console.log("- 경과 시간: 30초");
    console.log("- 팝업 상태:", popup?.closed ? "닫힘" : "열림");
    console.log("- 리스너 등록 상태: 제거 예정");
    console.log("- 토큰 수신 여부:", !!localStorage.getItem("accessToken"));    
    toast.error("로그인 시간이 초과되었습니다. 다시 시도해주세요.");
    window.removeEventListener("message", listener);
    window.removeEventListener("message", logListener);
    if (popup && !popup.closed) {
      popup.close();
      console.log("- 팝업 강제 종료됨");
    }
  }, 30000);

  // (4) 메시지 리스너 개선
  const listener = (e) => {
    console.log("===================");
    console.log("메시지 수신 상세:");
    console.log("- 시간:", new Date().toLocaleTimeString());
    console.log("- Origin:", e.origin);
    console.log("- 예상 Origin:", window.location.origin);
    console.log("- Data 전체:", e.data);
    console.log("- Data type:", e.data?.type);
    console.log("- Data keys:", Object.keys(e.data || {}));
    console.log("===================");

    // Origin 체크 개선
    if (e.origin !== window.location.origin) {
      console.log("Origin 불일치, 무시");
      console.log("- 받은 Origin:", e.origin);
      console.log("- 기대 Origin:", window.location.origin);
      return;
    }
    
    // 에러 메시지 처리 추가
    if (e.data?.type === "NAVER_ERROR") {
      console.log("네이버 콜백에서 에러 수신:", e.data);
      clearTimeout(timeout);
      clearInterval(poll);
      window.removeEventListener("message", listener);
      window.removeEventListener("message", logListener);
      
      const errorMsg = e.data.description || e.data.error || "알 수 없는 오류";
      toast.error(`네이버 로그인 중 오류 발생: ${errorMsg}`);
      
      if (popup && !popup.closed) {
        popup.close();
      }
      return;
    }
    
    if (e.data?.type !== "NAVER_TOKEN") {
      console.log("잘못된 메시지 타입:", e.data?.type);
      console.log("- 전체 데이터:", JSON.stringify(e.data, null, 2));      
      return;
    }

    console.log("네이버 토큰 수신:", e.data.accessToken?.substring(0, 10) + "...");
    console.log("토큰 길이:", e.data.accessToken?.length);
    clearTimeout(timeout);
    clearInterval(poll);
    window.removeEventListener("message", logListener);

    const { accessToken: naverToken } = e.data;
    
    // 토큰 유효성 체크
    if (!naverToken || naverToken.length < 10) {
      console.log("유효하지 않은 토큰");
      toast.error("로그인 정보가 올바르지 않습니다.");
      return;
    }

    axiosAPI
      .post("/oauth/naver", { accessToken: naverToken })
      .then((response) => {
        console.log("백엔드 응답 성공:");
        console.log("- 상태 코드:", response.status);
        console.log("- 응답 헤더:", response.headers);
        console.log("- 응답 데이터:", response.data);
        console.log("- loginMember 존재:", !!response.data.loginMember);
        console.log("- accessToken 존재:", !!response.data.accessToken);
        console.log("- accessToken 길이:", response.data.accessToken?.length);
        
        const { loginMember, accessToken } = response.data;
        
        if (!loginMember || !accessToken) {
          throw new Error("응답 데이터가 올바르지 않습니다.");
        }
        
        localStorage.setItem("accessToken", accessToken);
        localStorage.setItem("loginMember", JSON.stringify(loginMember));
        setMember(loginMember);
        
        toast.success("로그인이 완료되었습니다!");
        navigate("/");
      })
      .catch((err) => {
        console.error("백엔드 요청 실패 상세:");
        console.error("- 에러 객체:", err);
        console.error("- 응답 상태:", err.response?.status);
        console.error("- 응답 데이터:", err.response?.data);
        console.error("- 응답 헤더:", err.response?.headers);
        console.error("- 요청 URL:", err.config?.url);
        console.error("- 요청 메서드:", err.config?.method);
        console.error("- 요청 데이터:", err.config?.data);
        
        if (err.response?.status === 403 && err.response?.data?.msg === "MEMBER_WITHDRAWN") {
          toast.error("탈퇴한 회원은 로그인할 수 없습니다.");
          return;
        }
        
        const errorMsg = err.response?.data?.message || err.message || "알 수 없는 오류";
        toast.error(`네이버 로그인에 실패했습니다: ${errorMsg}`);
      });
  };

  // (5) 이벤트 리스너 등록 (once 제거 - 수동 관리)
  window.addEventListener("message", listener);

  // (6) 팝업 열기
  console.log("네이버 팝업 열기 시도");
  const popup = openNaverPopup();
  
  if (!popup) {
    console.log("팝업 열기 실패");
    clearTimeout(timeout);
    window.removeEventListener("message", listener);
    window.removeEventListener("message", logListener);
    toast.error("팝업을 열 수 없습니다. 브라우저 설정을 확인해주세요.");
    return;
  }

  console.log("네이버 팝업 열기 성공");

  // (7) 팝업 상태 모니터링 개선
  const poll = setInterval(() => {
    pollCount++;
    
    // 10초마다 상태 로그
    if (pollCount % 20 === 0) {
      console.log(`팝업 상태 체크 중... (${pollCount * 0.5}초 경과)`);
    }
    
    if (popup.closed) {
      console.log(`네이버 팝업 닫힌 것 감지 (${pollCount}번째 체크)`);
      console.log("- 최종 토큰 상태:", !!localStorage.getItem("accessToken"));
      console.log("- 최종 멤버 상태:", !!localStorage.getItem("loginMember"));
      
      clearInterval(poll);
      clearTimeout(timeout);
      window.removeEventListener("message", listener);
      window.removeEventListener("message", logListener);
      
      // 팝업이 닫혔는데 로그인이 안 된 경우
      if (!localStorage.getItem("accessToken")) {
        console.log("팝업은 닫혔지만 로그인 실패");
        console.log("- 체크 횟수:", pollCount);
        console.log("- 소요 시간:", (pollCount * 500) + "ms");
      } else {
        console.log("팝업 닫히고 로그인 성공");
      }
    }
  }, 500);
};


  // 랜더링 될떄마다 저장된 ID 불러오기. 화면을 새로고침했을 때마다 새로운게 나오면 안되잖아.
  useEffect(() => {
    const saved = localStorage.getItem("saveId");
    if (saved) setFormData((p) => ({ ...p, email: saved, saveId: true }));
  }, []);

  // 이 아래부터는 html 문법을 따른다.
  return (
    <div className="login-container">
      <div className="login-form">
        <h1 className="login-title">로그인</h1>

        {/* 이메일 */}
        <div className="login-form-group">
          <label htmlFor="email">이메일</label>
          <input
            id="email"
            name="email"
            type="email"
            placeholder="이메일을 입력해주세요"
            className="login-form-input"
            value={formData.email}
            onChange={handleChange}
            maxLength={50}
            required
          />
        </div>

        {/* 비밀번호 */}
        <div className="login-form-group">
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            name="password"
            type="password"
            placeholder="비밀번호를 입력해주세요"
            className="login-form-input"
            value={formData.password}
            onChange={handleChange}
            onKeyDown={handleSubmitEnter}
            maxLength={20}
            required
          />
        </div>

        {/* 옵션/버튼 */}
        <div className="login-form-options">
          <label className="checkbox-label">
            <input
              type="checkbox"
              name="saveId"
              checked={formData.saveId}
              onChange={handleChange}
              className="checkbox-input"
            />
            <span className="checkbox-text">아이디 저장</span>
          </label>

          <button
            type="button"
            onClick={handleFindPassword}
            className="find-password-btn"
          >
            비밀번호 찾기
          </button>
        </div>

        <button onClick={handleSubmit} className="login-btn">
          로그인하기
        </button>

        {/* 카카오 간편 로그인 */}
        <button onClick={handleKakaoLogin} className="kakao-login-btn option5">
          <img src={Kakao} alt="카카오톡 아이콘" />
          <span>카카오로 간편하게 로그인하기</span>
        </button>

        {/* 네이버 간편 로그인 */}
        <button
          onClick={handleNaverLogin}
          className="naver-login-btn brand-color"
        >
          <img src={Naver} alt="네이버 아이콘" />
          <span>네이버로 간편하게 로그인하기</span>
        </button>

        {/* 회원가입 */}
        <div className="signup-link">
          아직 회원이 아니신가요?
          <button onClick={handleSignUp} className="signup-btn">
            회원 가입하기
          </button>
        </div>
      </div>
    </div>
  );
}
