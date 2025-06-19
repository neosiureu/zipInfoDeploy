import React, { useState, useContext, useRef, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import { fetchPostById, deletePost } from "./noticeApi"; // boardApi ❌ → noticeApi ✅
import "../../css/notice/NoticeWrite.css";

// 공통 헤더, 푸터 import (경로는 프로젝트 구조에 맞게 수정하세요)
import Header from "../../components/common/Header";
import Footer from "../../components/common/Footer";

const NoticeWrite = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  // 수정 모드인지 판단 (location.state로 id가 넘어오면 수정)
  const isEdit = location.state?.id !== undefined;

  // 수정할 기존 데이터가 있으면 초기값으로 세팅
  const [id, setId] = useState(location.state?.id || null);
  const [title, setTitle] = useState(location.state?.title || "");
  const [content, setContent] = useState(location.state?.content || "");
  const [images, setImages] = useState([]); // 새로 추가할 이미지들 (파일 객체)
  const [previewUrls, setPreviewUrls] = useState([]);

  const fileInputRef = useRef(null);

  // 수정 시 기존 이미지 URL (서버에 저장된 이미지 주소들) 있으면 미리보기로 보여주기
  useEffect(() => {
    if (isEdit && location.state?.images?.length) {
      setPreviewUrls(location.state.images);
    }
  }, [isEdit, location.state]);

  const handleImageSelect = (e) => {
    const files = Array.from(e.target.files);

    // 새로 선택한 파일을 images 상태에 추가
    setImages((prev) => [...prev, ...files]);

    // 파일을 Blob URL로 변환해 미리보기 추가
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

    images.forEach((img) => formData.append("images", img)); // 새로 첨부한 이미지만 보내기

    try {
      if (isEdit) {
        // 수정 API 호출 (id 포함)
        await updatePostWithImage(id, formData);
        alert("공지사항이 수정되었습니다.");
        navigate(`/notice/${id}`);
      } else {
        // 새 글쓰기 API 호출
        const newPost = await createPostWithImage(formData);
        alert("공지사항이 등록되었습니다.");
        navigate(`/notice/${newPost.id}`);
      }
    } catch (error) {
      console.error("등록/수정 실패", error);
      alert("오류가 발생했습니다.");
    }
  };

  console.log("AuthContext user:", user);

  if (!user) {
    return <div>로그인 정보가 없습니다.</div>;
  }

  if (user.memberRole !== "ADMIN") {
    return <div>권한이 없습니다.</div>;
  }

  return (
    <>
      <Header />

      <div className="notice-write-container">
        <h2>{isEdit ? "공지사항 수정" : "공지사항 작성"}</h2>

        <input
          type="text"
          className="notice-title-input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
        />

        <textarea
          className="notice-content-input"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="내용을 입력하세요"
        />

        {/* 파일 입력 숨김 */}
        <input
          type="file"
          accept="image/*"
          multiple
          ref={fileInputRef}
          style={{ display: "none" }}
          onChange={handleImageSelect}
        />

        <div className="notice-write-buttons">
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

export default NoticeWrite;
