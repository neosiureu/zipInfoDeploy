// src/components/member/MemberSignUp.jsx
import React, { useState } from "react";
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
    memberNickname: "", // 
    phone: "", // 전화 번호
    postcode: "", // 우편번호
    address: "", // 주소
    detailAddress: "", // 상세주소
    // 중개사 전용 테이블 자료
    agencyName: "",
    agencyRegNo: "",
    representativeNumber: "",
  };

  const checkObj = {
    memberEmail: false,
    authKey: false,
    memberPw: false,
    memberPwConfirm: false,
    memberNickname: false,
    memberTel: false,
  };

  const [activeTab, setActiveTab] = useState("general"); // 일반 vs 중개자
  const [formData, setFormData] = useState(INITIAL_FORM);

  // 입력값 변경
  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  // 탭 전환 시 폼 초기화
  const handleTabChange = (tab) => {
    setActiveTab(tab);
    setFormData(INITIAL_FORM);
  };

  // 다음 주소 API 호출 (공통)
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

  const handleSubmit = (e) => {
    e.preventDefault();
    console.log("submitted:", activeTab, formData);
  };

  return (
    <div className="signup-container">
      <h1 className="signup-title">회원가입</h1>

      {/* Tab Navigation */}
      <div className="tab-navigation">
        <button
          className={`tab-button ${activeTab === "general" ? "active" : ""}`}
          onClick={() => handleTabChange("general")}
        >
          일반 회원가입
        </button>
        <button
          className={`tab-button ${activeTab === "agent" ? "active" : ""}`}
          onClick={() => handleTabChange("agent")}
        >
          중개사 회원가입
        </button>
      </div>

      <form className="signup-form" onSubmit={handleSubmit}>
        {/* 이메일 + 인증번호 */}
        <div className="form-group">
          <label className="form-label">이메일</label>
          <div className="input-wrapper">
            <input
              type="email"
              id="email"
              name="memberEmail"
              value={formData.memberEmail}
              onChange={handleInputChange}
              placeholder="이메일을 입력해 주세요"
              className="form-input"
            />
            <button
              type="button"
              id="sendCode"
              className="verify-button"
              onClick={() => console.log("인증번호 요청")}
            >
              인증받기
            </button>
          </div>
          <input
            type="text"
            id="authKey"
            name="authKey"
            value={formData.authKey}
            onChange={handleInputChange}
            placeholder="인증 번호를 입력해 주세요"
            className="form-input"
          />
        </div>

        {/* 비밀번호 */}
        <div className="form-group">
          <label className="form-label">비밀번호</label>
          <input
            type="password"
            id="password"
            name="memberPw"
            value={formData.memberPw}
            onChange={handleInputChange}
            placeholder="영어+숫자+특수문자를 포함한 8자리 이상"
            className="form-input"
          />
        </div>

        {/* 비밀번호 확인 */}
        <div className="form-group">
          <label className="form-label">비밀번호 확인</label>
          <input
            type="password"
            id="confirmPassword"
            name="memberPwConfirm"
            value={formData.memberPwConfirm}
            onChange={handleInputChange}
            placeholder="비밀번호를 재입력해 주세요"
            className="form-input"
          />
        </div>

        {activeTab === "agent" && (
          <>
            {/* 중개사명 */}
            <div className="form-group">
              <label className="form-label">중개사명</label>
              <input
                type="text"
                id="agencyName"
                name="agencyName"
                value={formData.agencyName}
                onChange={handleInputChange}
                placeholder="중개사명을 입력해 주세요"
                className="form-input"
              />
            </div>

            {/* 중개등록번호 */}
            <div className="form-group">
              <label className="form-label">중개등록번호</label>
              <input
                type="text"
                id="agencyRegNo"
                name="agencyRegNo"
                value={formData.agencyRegNo}
                onChange={handleInputChange}
                placeholder="중개등록번호를 입력해 주세요"
                className="form-input"
              />
            </div>

            {/* 대표번호 */}
            <div className="form-group">
              <label className="form-label">대표번호</label>
              <input
                type="tel"
                id="representativeNumber"
                name="representativeNumber"
                value={formData.representativeNumber}
                onChange={handleInputChange}
                placeholder="대표번호를 입력해 주세요"
                className="form-input"
              />
            </div>
          </>
        )}

        {/* 공통: 이름 */}
        <div className="form-group">
          <label className="form-label">이름</label>
          <input
            type="text"
            id="name"
            name="memberName"
            value={formData.memberName}
            onChange={handleInputChange}
            placeholder="이름을 입력해 주세요"
            className="form-input"
          />
        </div>

        {/* 공통: 닉네임 */}
        <div className="form-group">
          <label className="form-label">닉네임</label>
          <input
            type="text"
            id="nickname"
            name="memberNickname"
            value={formData.memberNickname}
            onChange={handleInputChange}
            placeholder="닉네임을 입력해 주세요"
            className="form-input"
          />
        </div>

        {/* 공통: 휴대폰 */}
        <div className="form-group">
          <label className="form-label">휴대폰 번호</label>
          <input
            type="tel"
            id="phone"
            name="phone"
            value={formData.phone}
            onChange={handleInputChange}
            placeholder="휴대번호를 입력해 주세요"
            className="form-input"
          />
        </div>

        {/* Address Field */}
        <div className="form-group">
          <label className="form-label">주소</label>
          <div className="input-wrapper">
            <input
              type="text"
              id="postcode"
              name="postcode"
              value={formData.postcode}
              readOnly
              className="form-input"
              placeholder="우편 번호"
            />
            <button
              type="button"
              id="searchAddress"
              className="address-button"
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
            className="form-input address-detail"
            placeholder="주소를 검색해 주세요"
          />
          <input
            type="text"
            id="detailAddress"
            name="detailAddress"
            value={formData.detailAddress}
            onChange={handleInputChange}
            className="form-input address-detail"
            placeholder="상세 주소를 입력해 주세요"
          />
        </div>

        <button type="submit" className="submit-button">
          가입하기
        </button>
      </form>
    </div>
  );
}
