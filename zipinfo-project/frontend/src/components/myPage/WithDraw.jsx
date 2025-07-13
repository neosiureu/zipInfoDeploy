import React, { useContext, useState } from "react";
import "../../css/myPage/withDraw.css";
import "../../css/myPage/menu.css";
import Menu from "./Menu";
import { useNavigate } from "react-router-dom";
import { axiosAPI } from "../../api/axiosAPI";
import { MemberContext } from "../member/MemberContext";
import { toast } from "react-toastify";

export default function PasswordChange() {
  const nav = useNavigate();
  const { setMember } = useContext(MemberContext);

  /* 카카오 로그인 여부 */
  const kakaoKey = Object.keys(localStorage).find((k) =>
    k.startsWith("kakao_")
  );
  const isKakaoLogin = Boolean(kakaoKey);

  /* form & check */
  const [password, setPassword] = useState("");
  const [agreed, setAgreed] = useState(false);

  const handleCheck = (e) => setAgreed(e.target.checked);
  const handlePass = (e) => setPassword(e.target.value);

  /* 탈퇴 */
  const handleWithdrawal = async () => {
    try {
      /* 약관 미동의 */
      if (!agreed) {
        toast.error("약관에 동의하세요.");
        return;
      }

      /* 일반 로그인일 때 비밀번호 검증 */
      if (!isKakaoLogin) {
        if (!password.trim()) {
          toast.error("비밀번호를 입력하세요.");
          return;
        }

        const { data: pwOK } = await axiosAPI.post("/myPage/checkPassword", {
          memberPw: password,
        });
        if (pwOK !== 1) {
          toast.error("비밀번호가 일치하지 않습니다.");
          setPassword("");
          return;
        }
      }

      /* 실제 탈퇴 – 엔드포인트 분기 */
      const url = isKakaoLogin ? "/oauth/kakaoWithdraw" : "/myPage/withDraw";
      const res = await axiosAPI.post(url); // POST로 통일(바디 불필요)

      if (res.status === 200 && res.data === 1) {
        toast.success("회원 탈퇴가 완료되었습니다.");
        /* 클라이언트 상태 정리 */
        setMember(null);
        localStorage.removeItem("loginMember");
        if (kakaoKey) localStorage.removeItem(kakaoKey);
        nav("/");
        return;
      }

      /* 14 일 내 재가입 제한(카카오 전용) */
      if (res.status === 401 && res.data?.msg === "WITHDRAW_14D") {
        toast.error("탈퇴 후 14일 동안 재가입이 제한됩니다.");
        return;
      }

      /* 기타 오류 */
      toast.error("탈퇴 처리 중 오류가 발생했습니다.");
    } catch (err) {
      console.error(err);
      toast.error("서버 통신 오류가 발생했습니다.");
    }
  };

  /* ----------------------- JSX ----------------------- */
  return (
    <div className="my-page">
      <div className="my-page-container">
        <Menu />

        <div className="with-draw-container">
          {/* 비밀번호 입력(일반 로그인만) */}
          {!isKakaoLogin && (
            <div className="with-draw-password-section">
              <div className="with-draw-section-title">비밀번호</div>
              <input
                type="password"
                value={password}
                onChange={handlePass}
                className="with-draw-password-input"
                placeholder="비밀번호를 입력해주세요"
              />
            </div>
          )}

          {/* 카카오 안내(카카오 로그인만) */}
          {isKakaoLogin && (
            <div className="kakao-withdraw-info">
              카카오 로그인 계정은 비밀번호가 설정되어 있지 않습니다.
              <br />
              <strong>
                탈퇴 후 14일 동안 동일 카카오 계정으로 재가입이 제한됩니다.
              </strong>
            </div>
          )}

          {/* 약관 */}
          <div className="with-draw-work-section">
            <div className="with-draw-section-title">회원 탈퇴 약관</div>
            {/* … 기존 약관 문구 그대로 … */}
          </div>

          {/* 동의 체크 */}
          <div className="with-draw-agreement-section">
            <label className="with-draw-agreement-item">
              <input
                type="checkbox"
                id="agree"
                checked={agreed}
                onChange={handleCheck}
              />
              <span className="withdraw-span">위 약관에 동의합니다</span>
            </label>
          </div>

          <button className="withdrawal-btn" onClick={handleWithdrawal}>
            탈퇴하기
          </button>
        </div>
      </div>
    </div>
  );
}
