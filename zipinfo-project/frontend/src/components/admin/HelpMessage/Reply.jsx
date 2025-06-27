import React, { useState } from "react";
import "../../../css/admin/HelpMessage/Reply.css";

const Reply = () => {
  const [formData, setFormData] = useState({
    category: "",
    title: "",
    content: "",
    tags: "",
  });

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    console.log("제출된 데이터:", formData);
    // 여기에 제출 로직 추가
  };

  const handleAddTag = () => {
    // 태그 추가 로직
    console.log("태그 추가");
  };

  const handleDownload = () => {
    // 다운로드 로직
    console.log("다운로드");
  };

  const handleClose = () => {
    // 닫기 로직
    console.log("닫기");
  };

  return (
    <div className="form-container">
      <div className="form-header">
        <h2 className="form-title">게시글 작성하기</h2>
      </div>

      <form onSubmit={handleSubmit} className="form-content">
        <div className="form-group">
          <label className="form-label">카테고리</label>
          <select
            name="category"
            value={formData.category}
            onChange={handleInputChange}
            className="form-select"
          >
            <option value="">카테고리를 선택하세요</option>
            <option value="notice">공지사항</option>
            <option value="general">일반</option>
            <option value="question">질문</option>
          </select>
        </div>

        <div className="form-group">
          <label className="form-label">제목 입력</label>
          <div className="title-input-container">
            <input
              type="text"
              name="title"
              value={formData.title}
              onChange={handleInputChange}
              placeholder="제목을 입력하세요"
              className="form-input"
            />
            <div className="title-actions">
              <button
                type="button"
                onClick={handleAddTag}
                className="action-btn"
              >
                +
              </button>
              <span className="action-text">태그 추가 하기</span>
              <button
                type="button"
                onClick={handleDownload}
                className="action-btn"
              >
                ↓
              </button>
              <button
                type="button"
                onClick={handleClose}
                className="action-btn"
              >
                ×
              </button>
            </div>
          </div>
        </div>

        <div className="form-group">
          <label className="form-label">내용 입력</label>
          <textarea
            name="content"
            value={formData.content}
            onChange={handleInputChange}
            placeholder="내용을 입력하세요"
            className="form-textarea"
            rows="10"
          />
        </div>

        <div className="form-group">
          <label className="form-label">태그</label>
          <textarea
            name="tags"
            value={formData.tags}
            onChange={handleInputChange}
            placeholder="태그를 입력하세요"
            className="form-textarea small"
            rows="3"
          />
        </div>

        <div className="form-actions">
          <button type="submit" className="submit-btn">
            업로드
          </button>
        </div>
      </form>
    </div>
  );
};

export default Reply;
