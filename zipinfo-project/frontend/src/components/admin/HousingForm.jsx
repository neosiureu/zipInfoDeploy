import React, { useState } from "react";
import "../../css/admin/HousingForm.css";

const HousingForm = () => {
  // 상태 추가
  const [thumbnailFile, setThumbnailFile] = useState(null);
  const [layoutImageFile, setLayoutImageFile] = useState(null);

  // 파일 선택 핸들러
  const handleThumbnailChange = (e) => {
    setThumbnailFile(e.target.files[0]);
  };

  const handleLayoutImageChange = (e) => {
    setLayoutImageFile(e.target.files[0]);
  };

  return (
    <div className="admin-form-container">
      <div className="header">
        <div className="logo">중개회원권한발급</div>
        <div className="header-right">관리자 페이지</div>
      </div>

      <div className="main-container">
        <div className="title-section">
          <h1 className="main-title">분양 등록 폼</h1>
        </div>

        <form className="form-content">
          {/* 기본정보 */}
          <div className="admin-info">
            <div className="admin-status">기본정보</div>
          </div>

          <div className="form-fields">
            <div className="form-group">
              <label className="form-label">분양명</label>
              <input
                type="text"
                placeholder="분양명을 입력하세요"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">가격</label>
              <input
                type="text"
                placeholder="가격을 입력하세요"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">분양 상태</label>
              <input
                type="text"
                placeholder="분양 상태를 입력하세요 (예: 분양중)"
                className="form-input"
              />
            </div>
          </div>

          {/* 상세정보 */}
          <div className="admin-info" style={{ marginTop: "40px" }}>
            <div className="admin-status">상세정보</div>
          </div>
          <div className="form-fields">
            <div className="form-group">
              <label className="form-label">분양 주소</label>
              <input
                type="text"
                placeholder="분양 주소를 입력하세요"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">단지 규모</label>
              <input
                type="text"
                placeholder="단지 규모를 입력하세요 (예: 500세대)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">청약 접수일</label>
              <input
                type="text"
                placeholder="청약 접수일을 입력하세요 (예: 2025-07-01)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">당첨자 발표일</label>
              <input
                type="text"
                placeholder="당첨자 발표일을 입력하세요 (예: 2025-07-10)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">건설사명</label>
              <input
                type="text"
                placeholder="건설사명을 입력하세요"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">분양 문의 연락처</label>
              <input
                type="text"
                placeholder="분양 문의 연락처를 입력하세요"
                className="form-input"
              />
            </div>
          </div>

          {/* 상세정보 추가 */}
          <div className="admin-info" style={{ marginTop: "40px" }}>
            <div className="admin-status">상세정보 (추가)</div>
          </div>
          <div className="form-fields">
            <div className="form-group">
              <label className="form-label">분양가</label>
              <input
                type="text"
                placeholder="분양가를 입력하세요 (예: 3억 5천만원)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">취득세 금액</label>
              <input
                type="text"
                placeholder="취득세 금액을 입력하세요 (예: 500만원)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">공급면적</label>
              <input
                type="text"
                placeholder="공급면적을 입력하세요 (예: 84㎡)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">전용면적</label>
              <input
                type="text"
                placeholder="전용면적을 입력하세요 (예: 59㎡)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">대지지분 면적</label>
              <input
                type="text"
                placeholder="대지지분 면적을 입력하세요 (예: 30㎡)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">방/욕실 개수</label>
              <input
                type="text"
                placeholder="방/욕실 개수를 입력하세요 (예: 3룸 / 2욕실)"
                className="form-input"
              />
            </div>
          </div>

          {/* 납입 정보 */}
          <div className="admin-info" style={{ marginTop: "40px" }}>
            <div className="admin-status">납입 정보</div>
          </div>
          <div className="form-fields">
            <div className="form-group">
              <label className="form-label">계약금 금액</label>
              <input
                type="text"
                placeholder="계약금 금액을 입력하세요 (예: 5,000만원)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">중도금 금액</label>
              <input
                type="text"
                placeholder="중도금 금액을 입력하세요 (예: 1억)"
                className="form-input"
              />
            </div>
            <div className="form-group">
              <label className="form-label">잔금 금액</label>
              <input
                type="text"
                placeholder="잔금 금액을 입력하세요 (예: 1억 5천만원)"
                className="form-input"
              />
            </div>
          </div>

          {/* 이미지 업로드 */}
          <div className="admin-info" style={{ marginTop: "40px" }}>
            <div className="admin-status">이미지 업로드</div>
          </div>
          <div
            className="form-fields"
            style={{ gridTemplateColumns: "1fr 1fr" }}
          >
            <div className="form-group">
              <label className="form-label">썸네일 업로드</label>
              <input
                type="file"
                accept="image/*"
                className="form-input"
                onChange={handleThumbnailChange}
              />
              <p
                style={{ fontSize: "12px", color: "#6b7280", marginTop: "4px" }}
              >
                사진 용량은 사진 한 장당 10MB 까지 등록이 가능합니다.
                <br />
                사진은 최대 1장까지 등록이 가능합니다.
              </p>
              {thumbnailFile && (
                <p style={{ fontSize: "12px", color: "#374151" }}>
                  선택된 파일: {thumbnailFile.name}
                </p>
              )}
            </div>
            <div className="form-group">
              <label className="form-label">평형 이미지 업로드</label>
              <input
                type="file"
                accept="image/*"
                className="form-input"
                onChange={handleLayoutImageChange}
              />
              <p
                style={{ fontSize: "12px", color: "#6b7280", marginTop: "4px" }}
              >
                사진 용량은 사진 한 장당 10MB 까지 등록이 가능합니다.
                <br />
                사진은 최대 1장까지 등록이 가능합니다.
              </p>
              {layoutImageFile && (
                <p style={{ fontSize: "12px", color: "#374151" }}>
                  선택된 파일: {layoutImageFile.name}
                </p>
              )}
            </div>
          </div>

          {/* 등록 버튼 */}
          <div className="submit-section">
            <button type="submit" className="submit-btn">
              등록하기
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default HousingForm;
