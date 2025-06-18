import React, { useState, useEffect } from "react";
import { axiosAPI } from "../../api/axiosAPI";
import "./MemberSignUp.css";

export default function MemberSignUp() {
  // 초기 폼 데이터
  const INITIAL_FORM = {
    // Member테이블
    memberEmail: "",
    authKey: "",
    memberPw: "",
    memberPwConfirm: "",
    memberName: "",
    memberNickname: "",
    memberTel: "", // 전화번호 추가
    postcode: "", // 우편번호
    address: "", // 주소
    detailAddress: "", // 상세주소
    // 중개사 전용 테이블 자료
    companyName: "", // 중개사의 이름
    brokerNo: "", // 중개사 고유 번호
    // ^([가-힣]\d{3,5}-\d{2,4}-\d{1,5}|\d{5}-\d{4}-\d{3,5})$ 와 같은 정규식으로 regExp를 표현하면 된다.

    representativeNumber: "",
  };

  // 유효성 검사 상태
  const INITIAL_CHECK_OBJ = {
    memberEmail: false,
    authKey: false,
    memberPw: false,
    memberPwConfirm: false,
    memberNickname: false,
    brokerNo: false,
  };

  // 메시지 상태
  const INITIAL_MESSAGES = {
    emailMessage: "메일을 받을 수 있는 이메일을 입력해주세요.",
    authKeyMessage: "",
    pwMessage: "영어,숫자,특수문자(!,@,#,-,_) 6~20글자 사이로 입력해주세요.",
    pwMessageConfirm: "비밀번호를 다시 한번 입력해주세요.",
    nickMessage: "한글,영어,숫자로만 2~10글자",
    brokerNoMessage: "등록번호는 최소 9자리에서 최대 20자리 내로 입력해주세요",
  };

  const [activeTab, setActiveTab] = useState("general"); // 일반 vs 중개자
  const [formData, setFormData] = useState(INITIAL_FORM);
  const [checkObj, setCheckObj] = useState(INITIAL_CHECK_OBJ);
  const [messages, setMessages] = useState(INITIAL_MESSAGES);
  const [messageClasses, setMessageClasses] = useState({});

  // 타이머 관련 상태
  const [authTimer, setAuthTimer] = useState(null);
  const [min, setMin] = useState(2);
  const [sec, setSec] = useState(59);
  const initTime = "03:00";

  // 타이머 정리
  useEffect(() => {
    return () => {
      if (authTimer) {
        clearInterval(authTimer);
      }
    };
  }, [authTimer]);

  // 입력값 변경 핸들러
  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));

    // 각 필드별 유효성 검사 실행
    switch (name) {
      case "memberEmail":
        validateEmail(value);
        break;
      case "memberPw":
        validatePassword(value);
        break;
      case "memberPwConfirm":
        validatePasswordConfirm(value);
        break;
      case "memberNickname":
        validateNickname(value);
        break;
      case "brokerNo":
        validateBrokerNo(value);
        break;
      default:
        break;
    }
  };

  // 탭 전환 시 폼 초기화
  const handleTabChange = (tab) => {
    setActiveTab(tab);
    setFormData(INITIAL_FORM);
    setCheckObj(INITIAL_CHECK_OBJ);
    setMessages(INITIAL_MESSAGES);
    setMessageClasses({});

    // 타이머 정리
    if (authTimer) {
      clearInterval(authTimer);
      setAuthTimer(null);
    }
  };

  // 메시지 업데이트 헬퍼 함수
  const updateMessage = (field, message, className = "") => {
    setMessages((prev) => ({ ...prev, [field]: message }));
    setMessageClasses((prev) => ({ ...prev, [field]: className }));
  };

  // 체크 상태 업데이트 헬퍼 함수
  const updateCheckObj = (field, isValid) => {
    setCheckObj((prev) => ({ ...prev, [field]: isValid }));
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
      updateMessage(
        "emailMessage",
        "메일을 받을 수 있는 이메일을 입력해주세요.",
        ""
      );
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

    // 중복 검사
    axiosAPI
      .get(`/member/checkEmail?memberEmail=${inputEmail}`)
      .then((response) => {
        const count = response.data;
        if (count == 1) {
          updateMessage("emailMessage", "이미 사용중인 이메일 입니다", "error");
          updateCheckObj("memberEmail", false);
          return;
        }

        updateMessage("emailMessage", "사용가능한 이메일 입니다.", "confirm");
        updateCheckObj("memberEmail", true);
      })
      .catch((err) => console.log(err));
  };

  // 인증번호 받기
  const sendAuthKey = () => {
    updateCheckObj("authKey", false);
    updateMessage("authKeyMessage", "");

    if (!checkObj.memberEmail) {
      alert("유효한 이메일 작성 후 클릭해주세요");
      return;
    }

    // 타이머 초기화
    setMin(2);
    setSec(59);

    // 이전 타이머 클리어
    if (authTimer) {
      clearInterval(authTimer);
    }

    // 인증번호 발송 요청
    axiosAPI
      .post("/email/signup", formData.memberEmail, {
        headers: { "Content-Type": "application/json" },
      })
      .then((response) => {
        const result = response.data;
        if (result == 1) {
          console.log("인증 번호 발송 성공");
        } else {
          console.log("인증 번호 발송 실패");
        }
      })
      .catch((err) => console.log(err));

    // 타이머 시작
    updateMessage("authKeyMessage", initTime, "");
    alert("인증번호가 발송되었습니다.");

    const timer = setInterval(() => {
      setSec((prevSec) => {
        setMin((prevMin) => {
          const newSec = prevSec === 0 ? 59 : prevSec - 1;
          const newMin = prevSec === 0 ? prevMin - 1 : prevMin;

          const timeDisplay = `${addZero(newMin)}:${addZero(newSec)}`;
          updateMessage("authKeyMessage", timeDisplay, "");

          // 시간 종료
          if (newMin === 0 && newSec === 0) {
            updateCheckObj("authKey", false);
            clearInterval(timer);
            setAuthTimer(null);
            updateMessage("authKeyMessage", "시간이 만료되었습니다.", "error");
          }

          return newMin;
        });
        return prevSec === 0 ? 59 : prevSec - 1;
      });
    }, 1000);

    setAuthTimer(timer);
  };

  // 숫자 앞에 0 붙이기
  const addZero = (number) => {
    return number < 10 ? "0" + number : number;
  };

  // 인증번호 확인
  const checkAuthKey = () => {
    if (min === 0 && sec === 0) {
      alert("인증번호 입력 제한시간을 초과하였습니다. 다시 발급해주세요");
      return;
    }

    if (formData.authKey.length < 6 || formData.authKey.length >= 7) {
      alert("인증번호를 정확히 입력해주세요");
      return;
    }

    const obj = {
      email: formData.memberEmail,
      authKey: formData.authKey,
    };

    axiosAPI
      .post("/email/checkAuthKey", obj)
      .then((response) => {
        const result = response.data;
        if (result == 0) {
          alert("인증번호가 일치하지않습니다");
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
      .catch((err) => console.log(err));
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

  // 중개사 번호 유효성 검사
  const validateBrokerNo = (inputBrokerNo) => {
    if (inputBrokerNo.trim().length === 0) {
      updateMessage(
        "brokerNoMessage",
        "등록번호는 최소 9자리에서 최대 20자리 내로 입력해주세요",
        ""
      );
      updateCheckObj("brokerNo", false);
      return;
    }

    const regExp =
      /^([가-힣]\d{3,5}-\d{2,4}-\d{1,5}|[가-힣]\d{4}-\d{4}|\d{5}-\d{4}-\d{3,5})$/;

    if (!regExp.test(inputBrokerNo)) {
      updateMessage(
        "brokerNoMessage",
        "중개사 번호가 유효하지 않습니다.",
        "error"
      );
      updateCheckObj("brokerNo", false);
      return;
    }

    updateMessage("brokerNoMessage", "유효한 중개사 번호입니다..", "confirm");
    updateCheckObj("brokerNo", true);
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
  // 비밀번호 일치 검사 함수 수정
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

  // 닉네임 유효성 검사
  const validateNickname = (inputNickname) => {
    if (inputNickname.trim().length === 0) {
      updateMessage("nickMessage", "한글,영어,숫자로만 2~10글자", "");
      updateCheckObj("memberNickname", false);
      return;
    }

    const regExp = /^[가-힣\w\d]{2,10}$/;

    if (!regExp.test(inputNickname)) {
      updateMessage("nickMessage", "유효하지 않은 닉네임 형식입니다", "error");
      updateCheckObj("memberNickname", false);
      return;
    }

    // 중복 검사
    axiosAPI
      .get(`/member/checkNickname?memberNickname=${inputNickname}`)
      .then((response) => {
        const count = response.data;
        if (count == 1) {
          updateMessage("nickMessage", "중복된 닉네임이 있습니다.", "error");
          updateCheckObj("memberNickname", false);
          return;
        }

        updateMessage("nickMessage", "허용가능한 닉네임입니다.", "confirm");
        updateCheckObj("memberNickname", true);
      })
      .catch((err) => console.log(err));
  };

  // 다음 주소 API 호출
  const execDaumPostcode = () => {
    new window.daum.Postcode({
      oncomplete: (data) => {
        const addr =
          data.userSelectedType === "R" ? data.roadAddress : data.jibunAddress;
        setFormData((prev) => ({
          ...prev,
          postcode: data.zonecode,
          address: addr,
        }));
        document.getElementsByName("detailAddress")[0].focus();
      },
    }).open();
  };

  // 폼 제출
  const handleSubmit = (e) => {
    e.preventDefault();

    // 전체 유효성 검사
    for (let key in checkObj) {
      if (!checkObj[key]) {
        let str;
        switch (key) {
          case "memberEmail":
            str = "이메일이 유효하지 않습니다.";
            break;
          case "authKey":
            str = "이메일이 인증되지 않았습니다.";
            break;
          case "memberPw":
            str = "비밀번호가 유효하지 않습니다.";
            break;
          case "memberPwConfirm":
            str = "비밀번호가 일치하지 않습니다.";
            break;
          case "memberNickname":
            str = "닉네임이 유효하지 않습니다.";
            break;
          case "brokerNo":
            str = "중개사 번호가 유효하지 않습니다.";
            break;
          default:
            str = "입력값을 확인해주세요.";
        }
        alert(str);
        document.querySelector(`[name="${key}"]`)?.focus();
        return;
      }
    }

    // 제출 데이터 준비
    const submitData = { ...formData };

    // 필요없는 필드 제거
    if (activeTab === "general") {
      delete submitData.companyName;
      delete submitData.brokerNo;
      delete submitData.representativeNumber;
      delete submitData.postcode;
      delete submitData.address;
      delete submitData.detailAddress;
    }

    // 서버로 전송
    const endpoint =
      activeTab === "general" ? "/member/signup" : "/agent/signup";

    fetch(`http://localhost:8080${endpoint}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(submitData),
    })
      .then((resp) => resp.text())
      .then((result) => {
        if (result === "1" || result === "success") {
          alert("회원가입이 완료되었습니다!");
          // 성공 시 리다이렉트 또는 다른 처리
        } else {
          alert("회원가입에 실패했습니다. 다시 시도해주세요.");
        }
      })
      .catch((err) => {
        console.error("회원가입 오류:", err);
        alert("서버 오류가 발생했습니다.");
      });
  };

  return (
    <div className="signup-container">
      <h1 className="signup-title">회원가입</h1>

      {/* Tab Navigation */}
      <div className="signup-tab-navigation">
        <button
          className={`signup-tab-button ${
            activeTab === "general" ? "active" : ""
          }`}
          onClick={() => handleTabChange("general")}
        >
          일반 회원가입
        </button>
        <button
          className={`signup-tab-button ${
            activeTab === "agent" ? "active" : ""
          }`}
          onClick={() => handleTabChange("agent")}
        >
          중개사 회원가입
        </button>
      </div>

      <form className="signup-form" onSubmit={handleSubmit}>
        {/* 이메일 + 인증번호 */}
        <div className="signup-form-group">
          <label className="signup-form-label">이메일</label>
          <div className="input-wrapper">
            <input
              type="email"
              id="memberEmail"
              name="memberEmail"
              value={formData.memberEmail}
              onChange={handleInputChange}
              placeholder="이메일을 입력해 주세요"
              className="signup-form-input"
            />
            <button
              type="button"
              id="sendAuthKeyBtn"
              className="signup-verify-button"
              onClick={sendAuthKey}
            >
              인증받기
            </button>
          </div>
          <span className={`message ${messageClasses.emailMessage || ""}`}>
            {messages.emailMessage}
          </span>

          {/* 인증번호 입력 */}
          <div className="input-wrapper">
            <input
              type="text"
              id="authKey"
              name="authKey"
              value={formData.authKey}
              onChange={handleInputChange}
              placeholder="인증 번호를 입력해 주세요"
              className="signup-form-input"
            />
            <button
              type="button"
              id="checkAuthKeyBtn"
              className="signup-verify-button"
              onClick={checkAuthKey}
            >
              인증확인
            </button>
          </div>
          <span className={`message ${messageClasses.authKeyMessage || ""}`}>
            {messages.authKeyMessage}
          </span>
        </div>

        {/* 비밀번호 */}
        <div className="signup-form-group">
          <label className="signup-form-label">비밀번호</label>
          <input
            type="password"
            id="memberPw"
            name="memberPw"
            value={formData.memberPw}
            onChange={handleInputChange}
            placeholder="영어+숫자+특수문자를 포함한 6자리 이상"
            className="signup-form-input"
          />
          <span className={`message ${messageClasses.pwMessage || ""}`}>
            {messages.pwMessage}
          </span>
        </div>

        {/* 비밀번호 확인 */}
        <div className="signup-form-group">
          <label className="signup-form-label">비밀번호 확인</label>
          <input
            type="password"
            id="memberPwConfirm"
            name="memberPwConfirm"
            value={formData.memberPwConfirm}
            onChange={handleInputChange}
            placeholder="비밀번호를 재입력해 주세요"
            className="signup-form-input"
          />
          <span className={`message ${messageClasses.pwMessageConfirm || ""}`}>
            {messages.pwMessageConfirm}
          </span>
        </div>

        {activeTab === "agent" && (
          <>
            {/* 중개사만: 중개사 회사 이름 */}
            <div className="signup-form-group">
              <label className="signup-form-label">중개사명</label>
              <input
                type="text"
                id="companyName"
                name="companyName"
                value={formData.companyName}
                onChange={handleInputChange}
                placeholder="중개사명을 입력해 주세요"
                className="signup-form-input"
              />
            </div>

            {/* 중개사만: 중개등록번호 */}
            <div className="signup-form-group">
              <label className="signup-form-label">중개등록번호</label>
              <input
                type="text"
                id="brokerNo"
                name="brokerNo"
                value={formData.brokerNo}
                onChange={handleInputChange}
                placeholder="중개등록번호를 입력해 주세요"
                className="signup-form-input"
              />
              <span
                className={`message ${messageClasses.brokerNoMessage || ""}`}
              >
                {messages.brokerNoMessage}
              </span>
            </div>

            {/* 중개사만: 대표번호 */}
            <div className="signup-form-group">
              <label className="signup-form-label">대표번호</label>
              <input
                type="tel"
                id="representativeNumber"
                name="representativeNumber"
                value={formData.representativeNumber}
                onChange={handleInputChange}
                placeholder="대표의 전화번호를 입력해 주세요(-없이 숫자만 입력)"
                className="signup-form-input"
              />
            </div>

            {/* 중개사만: 주소*/}
            <div className="signup-form-group">
              <label className="signup-form-label">중개사 주소</label>
              <div className="input-wrapper">
                <input
                  type="text"
                  id="postcode"
                  name="postcode"
                  value={formData.postcode}
                  readOnly
                  className="signup-form-input"
                  placeholder="우편 번호"
                />
                <button
                  type="button"
                  id="searchAddress"
                  className="signup-address-button"
                  onClick={execDaumPostcode}
                >
                  주소검색
                </button>
              </div>

              <input
                type="text"
                id="address"
                name="address"
                value={formData.address}
                readOnly
                className="signup-form-input signup-address-detail"
                placeholder="주소를 검색해 주세요"
              />
              <input
                type="text"
                id="detailAddress"
                name="detailAddress"
                value={formData.detailAddress}
                onChange={handleInputChange}
                className="signup-form-input signup-address-detail"
                placeholder="상세 주소를 입력해 주세요"
              />
            </div>
          </>
        )}

        {/* 공통: 이름 */}
        <div className="signup-form-group">
          <label className="signup-form-label">이름</label>
          <input
            type="text"
            id="memberName"
            name="memberName"
            value={formData.memberName}
            onChange={handleInputChange}
            placeholder="이름을 입력해 주세요"
            className="signup-form-input"
          />
        </div>

        {/* 공통: 닉네임 */}
        <div className="signup-form-group">
          <label className="signup-form-label">닉네임</label>
          <input
            type="text"
            id="memberNickname"
            name="memberNickname"
            value={formData.memberNickname}
            onChange={handleInputChange}
            placeholder="닉네임을 입력해 주세요"
            className="signup-form-input"
          />
          <span className={`message ${messageClasses.nickMessage || ""}`}>
            {messages.nickMessage}
          </span>
        </div>

        <button type="submit" className="submit-button">
          가입하기
        </button>
      </form>
    </div>
  );
}
