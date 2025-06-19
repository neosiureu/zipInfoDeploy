import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import "../../css/neighborhood/NeighborhoodWrite.css";

const NeighborhoodWrite = () => {
  const navigate = useNavigate();

  // 시/도, 구/군, 주제분류 상태
  const [city, setCity] = useState(""); // 시/도
  const [district, setDistrict] = useState(""); // 구/군
  const [category, setCategory] = useState(""); // 주제분류

  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");

  // 구/군은 시/도에 따라 동적으로 바뀔 수 있으니 데이터 예시
  const districtsByCity = {
    서울: ["강남구", "서초구", "마포구"],
    경기: ["수원시", "성남시", "용인시"],
    부산: ["해운대구", "부산진구", "동래구"],
  };

  const categories = ["정보", "질문", "거래"];

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!city || !district || !category) {
      alert("시/도, 구/군, 주제 분류를 모두 선택해주세요.");
      return;
    }

    console.log("게시글 작성:", { city, district, category, title, content });
    // TODO: 서버 저장 로직

    // 작성 후 게시판 목록으로 이동
    navigate("/neighborhood");
  };

  const handleCancel = () => {
    setCity("");
    setDistrict("");
    setCategory("");
    setTitle("");
    setContent("");
  };

  return (
    <div className="neighborhood-container">
      <div className="neighborhood-form">
        <h2 className="neighborhood-title">동네 게시판에 글을 작성해보세요</h2>

        <form onSubmit={handleSubmit}>
          {/* 드롭다운 3개 한 줄로 감싸는 컨테이너 */}
          <div className="dropdown-row">
            {/* 시/도 선택 */}
            <div className="input-group">
              <label htmlFor="city">시/도</label>
              <select
                id="city"
                value={city}
                onChange={(e) => {
                  setCity(e.target.value);
                  setDistrict(""); // 시/도가 바뀌면 구/군 초기화
                }}
                required
              >
                <option value="">시/도</option>
                {Object.keys(districtsByCity).map((cityName) => (
                  <option key={cityName} value={cityName}>
                    {cityName}
                  </option>
                ))}
              </select>
            </div>

            {/* 구/군 선택 (시/도 선택에 따라 옵션 변경) */}
            <div className="input-group">
              <label htmlFor="district">구/군</label>
              <select
                id="district"
                value={district}
                onChange={(e) => setDistrict(e.target.value)}
                required
                disabled={!city} // 시/도가 선택되어야 활성화
              >
                <option value="">구/군</option>
                {city &&
                  districtsByCity[city].map((districtName) => (
                    <option key={districtName} value={districtName}>
                      {districtName}
                    </option>
                  ))}
              </select>
            </div>

            {/* 주제 분류 선택 */}
            <div className="input-group">
              <label htmlFor="category">주제 분류</label>
              <select
                id="category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                required
              >
                <option value="">주제 분류</option>
                {categories.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* 제목 입력 */}
          <div className="input-group">
            <label htmlFor="title">제목</label>
            <input
              type="text"
              id="title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="제목을 입력해주세요"
              required
            />
          </div>

          {/* 내용 입력 */}
          <div className="input-group">
            <label htmlFor="content">내용</label>
            <textarea
              id="content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="내용을 입력해주세요"
              rows="8"
              required
            />
          </div>

          <p className="info-text">
            이웃들과 따뜻한 소통을 위해 예의를 지켜주세요
          </p>

          <div className="button-group">
            {/* 사진 첨부 버튼 */}
            <label
              htmlFor="file-upload"
              className="btn btn-secondary"
              style={{ cursor: "pointer" }}
            >
              사진 첨부
            </label>
            <input
              id="file-upload"
              type="file"
              style={{ display: "none" }}
              onChange={(e) => {
                const file = e.target.files[0];
                console.log("선택된 파일:", file);
                // 파일 처리 로직 추가 가능
              }}
            />

            {/* 등록하기 버튼 */}
            <button type="submit" className="btn btn-primary">
              등록하기
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default NeighborhoodWrite;
