// AnnounceWrite.jsx
import React, { useState, useContext } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/announce/AnnounceWrite.css";
import { createPost } from "/src/api/AnnounceApi.js";

const AnnounceWrite = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  const isEdit = location.state?.id !== undefined;

  const [title, setTitle] = useState(location.state?.title || "");
  const [content, setContent] = useState(location.state?.content || "");

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

  const handleSubmit = async () => {
    if (!title || !content) {
      alert("제목과 내용을 입력해주세요.");
      return;
    }

    const postData = {
      announceTitle: title,
      announce: content,
      memberNo: user.memberNo, // 회원 번호 반드시 포함
    };

    try {
      if (isEdit) {
        await updatePostWithImage(location.state.id, postData); // 수정 API 필요시 맞게 바꾸세요
        alert("공지사항이 수정되었습니다.");
        navigate(`/announce/detail/${location.state.id}`);
      } else {
        const newPost = await createPost(postData); // 이미지 없이 글 등록용 API 호출
        alert("공지사항이 등록되었습니다.");
        navigate(`/announce/detail/${newPost.boardNo || newPost.id}`);
      }
    } catch (error) {
      console.error("등록/수정 실패", error);
      alert("오류가 발생했습니다.");
    }
  };

  if (!user) return <div>로그인 정보가 없습니다.</div>;
  if (!isAdmin) return <div>권한이 없습니다.</div>;

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
