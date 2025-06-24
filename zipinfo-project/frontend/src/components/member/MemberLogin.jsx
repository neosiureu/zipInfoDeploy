import React, { memo, useContext, useEffect, useState } from "react";
import { axiosAPI } from "../../api/axiosAPI";
import "../../css/member/MemberLogin.css";
import { MemberContext } from "../member/MemberContext";
import NaverCallback from "../auth/NaverCallback";
import { data, useNavigate } from "react-router-dom";

export default function MemberLogin() {
  useEffect(() => {
    localStorage.removeItem("loginMember");
  }, []);
  const { VITE_KAKAO_REST_API_KEY, VITE_KAKAO_REDIRECT_URI } = import.meta.env;
  const navigate = useNavigate();

  const { setMember } = useContext(MemberContext);

  const [formData, setFormData] = useState({
    email: "", // 초기화용
    password: "", // 초기화용
    saveId: false,
  });

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
    e.preventDefault();

    try {
      const resp = await axiosAPI.post("http://localhost:8080/member/login", {
        memberEmail: formData.email, //  DTO 필드명과 동일
        memberPw: formData.password,
      });

      // 200 OK
      const loginMember = resp.data; // 백엔드가 돌려준 Member

      // 아이디 저장 check 후 localStorage를 뒤져보는 경우
      if (formData.saveId) {
        localStorage.setItem("saveId", formData.email);
      } else {
        localStorage.removeItem("saveId");
      }

      // 로그인 정보 저장
      localStorage.setItem("loginMember", JSON.stringify(loginMember));
      setMember(loginMember);

      alert(`${loginMember.memberNickname}님 반갑습니다!`);
      if (loginMember.memberAuth == 2) {
        alert(`당신은 중개사 자격이 없는 중개자입니다. `);
      }

      navigate("/"); //router 사용하여 메인페이지로 이동
    } catch (err) {
      if (err.response?.status === 401) {
        alert("이메일 또는 비밀번호가 다릅니다.");
      } else {
        console.error(err);
        alert("로그인 중 오류가 발생했습니다!!");
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
    console.log("회원가입 페이지 진입!!");
    navigate("/signUp"); //router 사용
  };

  // 카카오 로그인
  const handleKakaoLogin = () => {
    localStorage.removeItem("loginMember");
    setMember(null);

    window.Kakao.Auth.loginForm({
      scope: "profile_nickname,account_email",
      success: async (authObj) => {
        try {
          const { data: member } = await axiosAPI.post("/oauth/kakao", {
            code: authObj.access_token,
          });
          alert(`${member.memberNickname}님, 환영합니다!`);

          localStorage.setItem("loginMember", JSON.stringify(member));
          setMember(member);
          navigate("/");
        } catch (err) {
          console.error("카카오 로그인 처리 중 에러", err);
          alert("로그인 중 오류가 발생했습니다.");
        }
      },
      fail: (err) => {
        console.error("카카오 로그인 실패", err);
        alert("로그인에 실패했습니다.");
      },
    });
  };

  // 네이버 로그인
  const handleNaverLogin = () => {
    localStorage.removeItem("loginMember");
    setMember(null);

    // 당장 토큰을 받아서 백엔드로 수신하는 post 메서드
    const listener = (e) => {
      if (e.data?.type !== "NAVER_TOKEN") return;

      const { accessToken, code } = e.data;
      axiosAPI
        .post("/oauth/naver", { accessToken, code })
        .then(({ data: member }) => {
          localStorage.setItem("loginMember", JSON.stringify(member));
          setMember(member);
          navigate("/");
          alert(`member.memberNickname님, 환영합니다!`);
        })
        .catch((err) => {
          console.error("네이버 로그인 중 오류", err);
          alert("네이버 로그인 중 오류가 발생했습니다.");
        })
        .finally(() => window.removeEventListener("message", listener));
    };

    window.addEventListener("message", listener, { once: true });
    const btn = document.getElementById("naverIdLogin_loginButton");
    if (btn) {
      btn.click();
    } else {
      alert("네이버 SDK가 아직 초기화되지 않음");
      window.removeEventListener("message", listener);
    }
  };

  // 기타 앞으로 할 일

  const handleFindPassword = () => console.log("비밀번호 찾기 진입");

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

        <form onSubmit={handleSubmit}>
          {/* 이메일 */}
          <div className="form-group">
            <label htmlFor="email">이메일</label>
            <input
              id="email"
              name="email"
              type="email"
              placeholder="이메일을 입력해주세요"
              className="form-input"
              value={formData.email}
              onChange={handleChange}
              required
            />
          </div>

          {/* 비밀번호 */}
          <div className="form-group">
            <label htmlFor="password">비밀번호</label>
            <input
              id="password"
              name="password"
              type="password"
              placeholder="비밀번호를 입력해주세요"
              className="form-input"
              value={formData.password}
              onChange={handleChange}
              required
            />
          </div>

          {/* 옵션/버튼 */}
          <div className="form-options">
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
          <svg
            className="kakao-detailed-icon"
            width="20"
            height="18"
            viewBox="0 0 20 18"
            fill="none"
          >
            <path
              d="M10 0C4.48 0 0 3.28 0 7.32c0 2.6 1.74 4.89 4.38 6.17l-.79 2.91c-.08.29.2.52.45.38l3.29-2.17c.55.08.95.08 1.67.08 5.52 0 10-3.28 10-7.32S15.52 0 10 0z"
              fill="currentColor"
            />
          </svg>
          카카오로 간편 로그인
        </button>

        {/* 네이버 간편 로그인 */}
        <button
          onClick={handleNaverLogin}
          className="naver-login-btn brand-color"
        >
          <span className="naver-simple-n">N</span>네이버로 간편 로그인
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
