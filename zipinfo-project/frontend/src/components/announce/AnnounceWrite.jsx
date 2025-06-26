import React, { useState, useContext, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/announce/AnnounceWrite.css";
import { createPost, updatePost } from "../../api/AnnounceApi";

const AnnounceWrite = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  // 수정 모드 여부 및 글 ID
  const isEdit = !!location.state?.id;
  const postId = location.state?.id;

  // 제목과 내용 상태
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");

  // ✅ 관리자 여부 체크: memberAuth === 0
  const isAdmin = user && String(user.memberAuth) === "0";

  // 페이지 접근 시 관리자 아니면 자동 리디렉션
  useEffect(() => {
    if (!user) {
      alert("로그인이 필요합니다.");
      navigate("/login"); // 로그인 페이지로
    } else if (!isAdmin) {
      alert("관리자만 접근할 수 있습니다.");
      navigate("/announce"); // 목록으로
    }
  }, [user, isAdmin, navigate]);

  // 수정 모드일 경우 초기값 설정
  useEffect(() => {
    if (isEdit) {
      setTitle(location.state?.title || "");
      setContent(location.state?.content || "");
    }
  }, [isEdit, location.state]);

  // 등록/수정 처리
  const handleSubmit = async () => {
    if (!title.trim() || !content.trim()) {
      alert("제목과 내용을 입력해주세요.");
      return;
    }

    if (!user?.memberNo) {
      alert("회원 정보가 올바르지 않습니다.");
      return;
    }

    const postData = {
      announceTitle: title,
      announce: content,
      memberNo: user.memberNo,
    };

    try {
      if (isEdit) {
        await updatePost(postId, postData);
        alert("공지사항이 수정되었습니다.");
        navigate(`/announce/detail/${postId}`);
      } else {
        const newPost = await createPost(postData);
        alert("공지사항이 등록되었습니다.");
        navigate(`/announce/detail/${newPost.announceNo || newPost.id}`);
      }
    } catch (error) {
      console.error("공지사항 등록/수정 실패:", error);
      alert("오류가 발생했습니다.");
    }
  };

  return (
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

      <div className="announce-write-buttons">
        <button type="button" onClick={handleSubmit}>
          {isEdit ? "수정 완료" : "등록"}
        </button>
      </div>
    </div>
  );
};

export default AnnounceWrite;
