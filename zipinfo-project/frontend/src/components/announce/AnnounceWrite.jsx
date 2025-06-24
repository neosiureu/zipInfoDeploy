// AnnounceWrite.jsx
import React, { useState, useContext, useRef, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import {
  fetchPostById,
  updatePostWithImage,
  createPostWithImage,
} from "./AnnounceApi";
import "../../css/announce/AnnounceWrite.css";

import Header from "../common/Header";
import Footer from "../common/Footer";

const AnnounceWrite = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  const isEdit = location.state?.id !== undefined;

  const [title, setTitle] = useState(location.state?.title || "");
  const [content, setContent] = useState(location.state?.content || "");
  const [images, setImages] = useState([]);
  const [previewUrls, setPreviewUrls] = useState([]);

  const fileInputRef = useRef(null);

  // 디버깅용 콘솔 출력
  console.log("로그인 사용자 정보:", user);

  // 권한 체크 함수
  const checkAdmin = (user) => {
    if (!user) return false;

    return (
      user.memberAuth === 0 ||
      user.memberAuth === "0" ||
      user.role === "ADMIN" ||
      user.authority === "ADMIN" ||
      user.roles?.includes("ROLE_ADMIN")
    );
  };

  const isAdmin = checkAdmin(user);

  useEffect(() => {
    if (isEdit && location.state?.images?.length) {
      setPreviewUrls(location.state.images);
    }
  }, [isEdit, location.state]);

  // 이미지 선택 시 처리 (최대 5장 제한)
  const handleImageSelect = (e) => {
    const files = Array.from(e.target.files);

    // 5장 제한 체크
    if (images.length + files.length > 5) {
      alert("이미지는 최대 5장까지만 첨부할 수 있습니다.");
      return;
    }

    setImages((prev) => [...prev, ...files]);

    const newPreviews = files.map((file) => URL.createObjectURL(file));
    setPreviewUrls((prev) => [...prev, ...newPreviews]);
  };

  // 이미지 삭제 처리
  const handleImageRemove = (index) => {
    setImages((prev) => prev.filter((_, i) => i !== index));
    setPreviewUrls((prev) => prev.filter((_, i) => i !== index));
  };

  const handleAttachClick = () => {
    fileInputRef.current.click();
  };

  const handleSubmit = async () => {
    if (!title || !content) {
      alert("제목과 내용을 입력해주세요.");
      return;
    }

    const formData = new FormData();
    formData.append("title", title);
    formData.append("content", content);
    images.forEach((img) => formData.append("images", img));

    try {
      if (isEdit) {
        await updatePostWithImage(location.state.id, formData);
        alert("공지사항이 수정되었습니다.");
        navigate(`/announce/detail/${location.state.id}`);
      } else {
        const newPost = await createPostWithImage(formData);
        alert("공지사항이 등록되었습니다.");
        navigate(`/announce/detail/${newPost.id}`);
      }
    } catch (error) {
      console.error("등록/수정 실패", error);
      alert("오류가 발생했습니다.");
    }
  };

  if (!user) return <div>로그인 정보가 없습니다.</div>;

  if (!isAdmin) {
    return <div>권한이 없습니다.</div>;
  }

  return (
    <>
      <Header />

      <div className="announce-write-container">
        <h2>{isEdit ? "공지사항 수정" : "공지사항 작성"}</h2>

        <input
          type="text"
          className="announce-title-input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
        />

        <textarea
          className="announce-content-input"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="내용을 입력하세요"
        />

        <input
          type="file"
          accept="image/*"
          multiple
          ref={fileInputRef}
          style={{ display: "none" }}
          onChange={handleImageSelect}
        />

        <div className="announce-write-buttons">
          <button type="button" onClick={handleAttachClick}>
            📎 사진 첨부
          </button>
          <button type="button" onClick={handleSubmit}>
            {isEdit ? "수정 완료" : "등록"}
          </button>
        </div>

        {previewUrls.length > 0 && (
          <div className="attached-images">
            <h4>첨부된 사진:</h4>
            <div
              className="image-preview-grid"
              style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}
            >
              {previewUrls.map((url, idx) => (
                <div
                  key={idx}
                  className="image-wrapper"
                  style={{ position: "relative", display: "inline-block" }}
                >
                  <img
                    src={url}
                    alt={`미리보기${idx}`}
                    style={{
                      width: "100px",
                      height: "100px",
                      objectFit: "cover",
                      borderRadius: "4px",
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => handleImageRemove(idx)}
                    className="remove-image-btn"
                    style={{
                      position: "absolute",
                      top: "2px",
                      right: "2px",
                      background: "rgba(0,0,0,0.6)",
                      color: "white",
                      border: "none",
                      borderRadius: "50%",
                      width: "22px",
                      height: "22px",
                      cursor: "pointer",
                      fontWeight: "bold",
                      lineHeight: "22px",
                      textAlign: "center",
                      padding: 0,
                    }}
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <Footer />
    </>
  );
};

export default AnnounceWrite;
