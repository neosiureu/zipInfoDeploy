// AnnounceWrite.jsx
import React, { useState, useContext, useRef, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import {
  fetchPostById,
  updatePostWithImage,
  createPostWithImage,
} from "./AnnounceApi"; // API 함수 임포트
import "../../css/announce/AnnounceWrite.css"; // CSS 경로 변경

import Header from "../common/Header";
import Footer from "../common/Footer";

const AnnounceWrite = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  const isEdit = location.state?.id !== undefined;

  const [id, setId] = useState(location.state?.id || null);
  const [title, setTitle] = useState(location.state?.title || "");
  const [content, setContent] = useState(location.state?.content || "");
  const [images, setImages] = useState([]);
  const [previewUrls, setPreviewUrls] = useState([]);

  const fileInputRef = useRef(null);
  const isAdmin = user && (user.memberAuth === 0 || user.memberAuth === "0");

  useEffect(() => {
    if (isEdit && location.state?.images?.length) {
      setPreviewUrls(location.state.images);
    }
  }, [isEdit, location.state]);

  const handleImageSelect = (e) => {
    const files = Array.from(e.target.files);
    setImages((prev) => [...prev, ...files]);
    const newPreviews = files.map((file) => URL.createObjectURL(file));
    setPreviewUrls((prev) => [...prev, ...newPreviews]);
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
        await updatePostWithImage(id, formData);
        alert("공지사항이 수정되었습니다.");
        navigate(`/announce/detail/${id}`);
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
            <div className="image-preview-grid">
              {previewUrls.map((url, idx) => (
                <img key={idx} src={url} alt={`미리보기${idx}`} />
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
