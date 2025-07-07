import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import "../../css/member/MemberFindPw.css";
import { axiosAPI } from "../../api/axiosAPI";

export default function MemberFindPw() {
  const navigate = useNavigate();

  // 폼 데이터
  const [formData, setFormData] = useState({
    memberEmail: "",
    authKey: "",
  });

  // 유효성 검사 상태
  const [checkObj, setCheckObj] = useState({
    memberEmail: false,
    authKey: false,
  });

  // 메시지 상태
  const [messages, setMessages] = useState({
    emailMessage: "가입하신 이메일을 입력해주세요.",
    authKeyMessage: "",
  });

  const [messageClasses, setMessageClasses] = useState({});

  // 타이머 관련 상태
  const [authTimer, setAuthTimer] = useState(null);
  const [min, setMin] = useState(5);
  const [sec, setSec] = useState(0);
  const initTime = "05:00";

  // 타이머 정리
  useEffect(() => {
    return () => {
      if (authTimer) {
        clearInterval(authTimer);
      }
    };
  }, [authTimer]);

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

    if (name === "memberEmail") {
      validateEmail(value);
    }
  };

  // 이메일 유효성 검사
  const validateEmail = (inputEmail) => {
    // 이메일 인증 후 이메일이 변경된 경우
    updateCheckObj("authKey", false);
    updateMessage("authKeyMessage", "");
    if (authTimer) {
      clearInterval(authTimer);
      setAuthTimer(null);
    }

    // 입력된 이메일이 없을 경우
    if (inputEmail.trim().length === 0) {
      updateMessage("emailMessage", "가입하신 이메일을 입력해주세요.", "");
      updateCheckObj("memberEmail", false);
      return;
    }

    // 정규식 검사
    const regExp = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

    if (!regExp.test(inputEmail)) {
      updateMessage(
        "emailMessage",
        "알맞은 이메일형식으로 작성해주세요.",
        "error"
      );
      updateCheckObj("memberEmail", false);
      return;
    }

    // 이메일 존재 확인
    axiosAPI
      .get(`/member/checkEmail?memberEmail=${inputEmail}`)
      .then((response) => {
        const count = response.data;
        if (count === 0) {
          updateMessage("emailMessage", "등록되지 않은 이메일입니다.", "error");
          updateCheckObj("memberEmail", false);
          return;
        }

        updateMessage("emailMessage", "확인된 이메일입니다.", "confirm");
        updateCheckObj("memberEmail", true);
      })
      .catch((err) => {
        console.log(err);
        updateMessage(
          "emailMessage",
          "이메일 확인 중 오류가 발생했습니다.",
          "error"
        );
        updateCheckObj("memberEmail", false);
      });
  };

  // 숫자 앞에 0 붙이기
  const addZero = (number) => {
    return number < 10 ? "0" + number : number;
  };

  // 인증번호 받기
  const sendAuthKey = () => {
    updateCheckObj("authKey", false);
    updateMessage("authKeyMessage", "");

    if (!checkObj.memberEmail) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">
            유효한 이메일 작성 후 클릭해주세요.
          </div>
        </div>
      );
      return;
    }

    // 타이머 초기화
    setMin(5);
    setSec(0);

    // 이전 타이머 클리어
    if (authTimer) {
      clearInterval(authTimer);
    }

    // 인증번호 발송 요청
    axiosAPI
      .post("/email/findPwEmail", {
        memberEmail: formData.memberEmail,
      })
      .then((response) => {
        console.log("비밀번호 찾기 인증번호 발송 성공");

        // 타이머 시작
        updateMessage("authKeyMessage", initTime, "");
        toast.success(
          <div>
            <div className="toast-success-title">인증번호 발송 알림!</div>
            <div className="toast-success-body">인증번호가 발송되었습니다.</div>
          </div>
        );

        let currentMin = 5;
        let currentSec = 0;

        const timer = setInterval(() => {
          // 직접 계산
          if (currentSec > 0) {
            currentSec--;
          } else {
            if (currentMin > 0) {
              currentSec = 59;
              currentMin--;
            } else {
              // 시간 종료
              updateCheckObj("authKey", false);
              clearInterval(timer);
              setAuthTimer(null);
              updateMessage(
                "authKeyMessage",
                "시간이 만료되었습니다. 다시 인증번호를 요청해주세요!",
                "error"
              );
              return;
            }
          }

          // 상태 업데이트
          setMin(currentMin);
          setSec(currentSec);

          // UI 업데이트
          const timeDisplay = `${addZero(currentMin)}:${addZero(currentSec)}`;
          updateMessage("authKeyMessage", timeDisplay, "");
        }, 1000);

        setAuthTimer(timer);
      })
      .catch((err) => {
        console.log(err);
        if (err.response?.status === 404) {
          toast.error(
            <div>
              <div className="toast-error-title">오류 알림!</div>
              <div className="toast-error-body">
                등록되지 않은 이메일입니다.
              </div>
            </div>
          );
        } else {
          toast.error(
            <div>
              <div className="toast-error-title">오류 알림!</div>
              <div className="toast-error-body">
                인증번호 발송에 실패했습니다.
              </div>
            </div>
          );
        }
      });
  };

  // 인증번호 확인
  const checkAuthKey = () => {
    if (min === 0 && sec === 0) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">
            인증번호 입력 제한시간을 초과하였습니다. 다시 발급해주세요.
          </div>
        </div>
      );
      return;
    }

    if (formData.authKey.length < 6 || formData.authKey.length >= 7) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">
            인증번호를 정확히 입력해주세요.
          </div>
        </div>
      );
      return;
    }

    const obj = {
      memberEmail: formData.memberEmail,
      authKey: formData.authKey,
    };

    axiosAPI
      .post("/email/checkFindPwAuthKey", obj)
      .then((response) => {
        const result = response.data;
        if (result === 0) {
          toast.error(
            <div>
              <div className="toast-error-title">오류 알림!</div>
              <div className="toast-error-body">
                인증번호가 일치하지 않습니다.
              </div>
            </div>
          );
          updateCheckObj("authKey", false);
          return;
        }

        // 인증 성공
        if (authTimer) {
          clearInterval(authTimer);
          setAuthTimer(null);
        }
        updateMessage("authKeyMessage", "인증되었습니다", "confirm");
        updateCheckObj("authKey", true);
      })
      .catch((err) => {
        console.log(err);
        toast.error(
          <div>
            <div className="toast-error-title">오류 알림!</div>
            <div className="toast-error-body">
              인증번호 확인에 실패했습니다.
            </div>
          </div>
        );
      });
  };

  // 다음 단계로 이동
  const handleNext = () => {
    if (!checkObj.memberEmail) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">유효한 이메일을 입력해주세요.</div>
        </div>
      );
      return;
    }

    if (!checkObj.authKey) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">이메일 인증을 완료해주세요.</div>
        </div>
      );
      return;
    }

    // 새 비밀번호 설정 페이지로 이동
    navigate("/memberSetPw", {
      state: { email: formData.memberEmail },
    });
  };

  return (
    <div className="find-pw-container">
      <h1 className="find-pw-title">비밀번호 찾기</h1>

      <div className="find-pw-form">
        <div className="find-pw-form-group">
          <label className="find-pw-form-label">이메일</label>
          <div className="find-pw-input-wrapper">
            <input
              type="email"
              id="memberEmail"
              name="memberEmail"
              value={formData.memberEmail}
              onChange={handleInputChange}
              placeholder="가입하신 이메일을 입력해주세요"
              className="find-pw-form-input"
              required
            />
            <button
              type="button"
              className="find-pw-verify-button"
              onClick={sendAuthKey}
            >
              인증받기
            </button>
          </div>
          <span
            className={`find-pw-message ${messageClasses.emailMessage || ""}`}
          >
            {messages.emailMessage}
          </span>

          <div className="find-pw-input-wrapper">
            <input
              type="text"
              id="authKey"
              name="authKey"
              value={formData.authKey}
              onChange={handleInputChange}
              placeholder="인증번호를 입력해주세요"
              className="find-pw-form-input"
              required
            />
            <button
              type="button"
              className="find-pw-verify-button"
              onClick={checkAuthKey}
            >
              인증확인
            </button>
          </div>
          <span
            className={`find-pw-message ${messageClasses.authKeyMessage || ""}`}
          >
            {messages.authKeyMessage}
          </span>
        </div>

        <button
          type="button"
          className="find-pw-submit-button"
          onClick={handleNext}
        >
          다음 단계
        </button>

        <div className="find-pw-back-to-login">
          <button
            type="button"
            onClick={() => navigate("/login")}
            className="find-pw-back-btn"
          >
            로그인으로 돌아가기
          </button>
        </div>
      </div>
    </div>
  );
}
