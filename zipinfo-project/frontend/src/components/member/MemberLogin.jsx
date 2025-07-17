import React, { memo, useContext, useEffect, useState } from "react";
import { axiosAPI } from "../../api/axiosAPI";
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
  return window.open(url.toString(), "naverLogin", "width=500,height=640");
}

export default function MemberLogin() {
  // 임시 디버깅 코드
  console.log("🔍 모든 환경변수:", import.meta.env);
  console.log("🔍 VITE_API_BASE_URL:", import.meta.env.VITE_API_BASE_URL);
  console.log(
    "🔍 VITE_KAKAO_REST_API_KEY:",
    import.meta.env.VITE_KAKAO_REST_API_KEY
  );
  console.log("🔍 VITE_NAVER_CLIENT_ID:", import.meta.env.VITE_NAVER_CLIENT_ID);
  useEffect(() => {
    localStorage.removeItem("loginMember");
    localStorage.removeItem("com.naver.nid.access_token");
    localStorage.removeItem("com.naver.nid.oauth.state_token");
  }, []);

  const { VITE_KAKAO_REST_API_KEY, VITE_KAKAO_REDIRECT_URI } = import.meta.env;
  const navigate = useNavigate();
  const { setMember } = useContext(MemberContext);

  const [formData, setFormData] = useState({
    email: "",
    password: "",
    saveId: false,
  });

  const handleFindPassword = () => {
    navigate("/findPassword");
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));
  };

  // 로그인 로직 통합
  const performLogin = async () => {
    // 클라이언트 측 검증
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

    try {
      const resp = await axiosAPI.post("/member/login", {
        memberEmail: formData.email,
        memberPw: formData.password,
      });

      const { loginMember, accessToken } = resp.data;

      // 아이디 저장 처리
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
        toast.error("당신은 중개사 자격이 없는 중개자입니다.");
      }

      navigate("/");
    } catch (err) {
      if (
        err.response?.status === 401 &&
        err.response?.data?.msg === "WITHDRAW_14D"
      ) {
        toast.error("탈퇴 후 14일 동안은 재가입할 수 없습니다.");
        return;
      }

      if (err.response?.status === 401) {
        toast.error("이메일 또는 비밀번호가 다릅니다.");
      } else {
        console.error(err);
        toast.error("로그인 중 오류가 발생했습니다.");
      }
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    await performLogin();
  };

  const handleSubmitEnter = async (e) => {
    if (e.key === "Enter") {
      await performLogin();
    }
  };

  const KAKAO_AUTH_URL =
    `https://kauth.kakao.com/oauth/authorize` +
    `?response_type=code` +
    `&client_id=${VITE_KAKAO_REST_API_KEY}` +
    `&redirect_uri=${encodeURIComponent(VITE_KAKAO_REDIRECT_URI)}`;

  const handleSignUp = () => {
    navigate("/signUp");
  };

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
          const { loginMember, accessToken } = data;
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

  const handleNaverLogin = () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("loginMember");
    setMember(null);

    const listener = (e) => {
      console.log("[NAVER LOGIN] message event:", e);
      if (e.origin !== window.location.origin) return;
      if (e.data?.type !== "NAVER_TOKEN") return;
      console.log("[MemberLogin] received NAVER_TOKEN:", e.data.accessToken);

      const { accessToken: naverToken } = e.data;
      axiosAPI
        .post("/oauth/naver", { accessToken: naverToken })
        .then((response) => {
          console.log("[NAVER LOGIN] response.data =", response.data);
          const { loginMember, accessToken } = response.data;
          localStorage.setItem("accessToken", accessToken);
          localStorage.setItem("loginMember", JSON.stringify(loginMember));
          setMember(loginMember);
          navigate("/");
        })
        .catch((err) => {
          console.error("네이버 로그인 중 오류", err);
          toast.error("네이버 로그인에 실패했습니다.");
        });
    };
    window.addEventListener("message", listener, { once: true });

    const popup = openNaverPopup();
    if (!popup) {
      window.removeEventListener("message", listener);
      return;
    }

    const poll = setInterval(() => {
      if (popup.closed) {
        clearInterval(poll);
        window.removeEventListener("message", listener);
      }
    }, 500);
  };

  useEffect(() => {
    const saved = localStorage.getItem("saveId");
    if (saved) setFormData((p) => ({ ...p, email: saved, saveId: true }));
  }, []);

  return (
    <div className="login-container">
      <div className="login-form">
        <h1 className="login-title">로그인</h1>

        <form onSubmit={handleSubmit}>
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

          <button type="submit" className="login-btn">
            로그인하기
          </button>
        </form>

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
