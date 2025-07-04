import React, { useState, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import "../../css/member/MemberSetPw.css";
import { axiosAPI } from "../../api/axiosAPI";

export default function MemberSetPw() {
  const location = useLocation();
  const navigate = useNavigate();
  const email = location.state?.email;

  // 폼 데이터
  const [formData, setFormData] = useState({
    memberPw: "",
    memberPwConfirm: "",
  });

  // 유효성 검사 상태
  const [checkObj, setCheckObj] = useState({
    memberPw: false,
    memberPwConfirm: false,
  });

  // 메시지 상태
  const [messages, setMessages] = useState({
    pwMessage: "영어,숫자,특수문자(!,@,#,-,_) 6~20글자 사이로 입력해주세요.",
    pwMessageConfirm: "비밀번호를 다시 한번 입력해주세요.",
  });

  const [messageClasses, setMessageClasses] = useState({});

  // 이메일 정보가 없으면 비밀번호 찾기 화면으로 리다이렉트
  useEffect(() => {
    if (!email) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">잘못된 접근입니다.</div>
        </div>
      );
      navigate("/findPassword");
    }
  }, [email, navigate]);

  // 메시지 업데이트 헬퍼 함수
  const updateMessage = (field, message, className = "") => {
    setMessages((prev) => ({ ...prev, [field]: message }));
    setMessageClasses((prev) => ({ ...prev, [field]: className }));
  };

  // 체크 상태 업데이트 헬퍼 함수
  const updateCheckObj = (field, isValid) => {
    setCheckObj((prev) => ({ ...prev, [field]: isValid }));
  };

  // 입력값 변경 핸들러
  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));

    // 각 필드별 유효성 검사 실행
    if (name === "memberPw") {
      validatePassword(value);
    } else if (name === "memberPwConfirm") {
      validatePasswordConfirm(value);
    }
  };

  // 비밀번호 유효성 검사
  const validatePassword = (inputPw) => {
    if (inputPw.trim().length === 0) {
      updateMessage(
        "pwMessage",
        "영어,숫자,특수문자(!,@,#,-,_) 6~20글자 사이로 입력해주세요.",
        ""
      );
      updateCheckObj("memberPw", false);
      return;
    }

    const regExp = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!@#_-])[A-Za-z\d!@#_-]{6,20}$/;

    if (!regExp.test(inputPw)) {
      updateMessage("pwMessage", "비밀번호가 유효하지 않습니다.", "error");
      updateCheckObj("memberPw", false);
      return;
    }

    updateMessage("pwMessage", "유효한 비밀번호 형식입니다.", "confirm");
    updateCheckObj("memberPw", true);

    // 비밀번호 확인란에 값이 있으면 비교
    if (formData.memberPwConfirm.length > 0) {
      checkPw(inputPw, formData.memberPwConfirm);
    }
  };

  // 비밀번호 확인 유효성 검사
  const validatePasswordConfirm = (inputPwConfirm) => {
    if (checkObj.memberPw) {
      checkPw(formData.memberPw, inputPwConfirm);
    } else {
      updateCheckObj("memberPwConfirm", false);
    }
  };

  // 비밀번호 일치 검사
  const checkPw = (pw, pwConfirm) => {
    if (pw === pwConfirm) {
      updateMessage("pwMessageConfirm", "비밀번호가 일치합니다.", "confirm");
      updateCheckObj("memberPwConfirm", true);
    } else {
      updateMessage(
        "pwMessageConfirm",
        "비밀번호가 일치하지 않습니다.",
        "error"
      );
      updateCheckObj("memberPwConfirm", false);
    }
  };

  // 비밀번호 변경 제출
  const handleSubmit = (e) => {
    e.preventDefault();

    // 유효성 검사
    if (!checkObj.memberPw) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">비밀번호가 유효하지 않습니다.</div>
        </div>
      );
      document.querySelector('[name="memberPw"]')?.focus();
      return;
    }

    if (!checkObj.memberPwConfirm) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">비밀번호가 일치하지 않습니다.</div>
        </div>
      );
      document.querySelector('[name="memberPwConfirm"]')?.focus();
      return;
    }

    // 비밀번호 변경 API 호출 (백엔드에서 구현될 예정)
    const submitData = {
      memberEmail: email,
      memberPw: formData.memberPw,
    };

    // axios를 사용해서 비밀번호 변경 API 호출
    axiosAPI
      .post("/member/setPw", submitData)
      .then((response) => {
        const result = response.data;
        if (result === "1" || result === "success") {
          toast.success(
            <div>
              <div className="toast-success-title">성공 알림!</div>
              <div className="toast-success-body">
                새 비밀번호가 설정되었습니다. 로그인해주세요.
              </div>
            </div>
          );
          navigate("/login");
        } else {
          toast.error(
            <div>
              <div className="toast-error-title">오류 알림!</div>
              <div className="toast-error-body">
                비밀번호 변경에 실패했습니다. 다시 시도해주세요.
              </div>
            </div>
          );
        }
      })
      .catch((err) => {
        console.error(err);
        toast.error(
          <div>
            <div className="toast-error-title">오류 알림!</div>
            <div className="toast-error-body">
              비밀번호 변경 중 오류가 발생했습니다.
            </div>
          </div>
        );
      });
  };

  if (!email) {
    return null; // 이메일 정보가 없으면 화면을 보여주지 않음
  }

  return (
    <div className="set-pw-container">
      <h1 className="set-pw-title">새 비밀번호 설정</h1>

      <div className="set-pw-email-info-box">
        <p className="set-pw-email-info">
          <strong>{email}</strong>님의 비밀번호를 새로 설정합니다.
        </p>
      </div>

      <form className="set-pw-form" onSubmit={handleSubmit}>
        <div className="set-pw-form-group">
          <label className="set-pw-form-label">새 비밀번호</label>
          <input
            type="password"
            id="memberPw"
            name="memberPw"
            value={formData.memberPw}
            onChange={handleInputChange}
            placeholder="새 비밀번호를 입력해주세요"
            className="set-pw-form-input"
            required
          />
          <span className={`set-pw-message ${messageClasses.pwMessage || ""}`}>
            {messages.pwMessage}
          </span>
        </div>

        <div className="set-pw-form-group">
          <label className="set-pw-form-label">새 비밀번호 확인</label>
          <input
            type="password"
            id="memberPwConfirm"
            name="memberPwConfirm"
            value={formData.memberPwConfirm}
            onChange={handleInputChange}
            placeholder="새 비밀번호를 다시 입력해주세요"
            className="set-pw-form-input"
            required
          />
          <span
            className={`set-pw-message ${
              messageClasses.pwMessageConfirm || ""
            }`}
          >
            {messages.pwMessageConfirm}
          </span>
        </div>

        <button type="submit" className="set-pw-submit-button">
          비밀번호 변경하기
        </button>

        <div className="set-pw-back-to-login">
          <button
            type="button"
            onClick={() => navigate("/login")}
            className="set-pw-back-btn"
          >
            로그인으로 돌아가기
          </button>
        </div>
      </form>
    </div>
  );
}
