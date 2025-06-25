import React, { useState, useContext, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/announce/AnnounceWrite.css";
import { createPost, updatePost } from "../../api/AnnounceApi";

const AnnounceWrite = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  // 수정 모드 여부 및 수정 대상 글 ID 확인
  const isEdit = !!location.state?.id;
  const postId = location.state?.id;

  // 제목과 내용 상태 관리
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");

  /**
   * 관리자 권한 확인 함수
   * @param {Object} user - 로그인 유저 객체
   * @returns {boolean} 관리자 권한 여부
   */
  const checkAdmin = (user) => {
    if (!user) return false;
    return (
      user.memberAuth === 0 || // memberAuth가 숫자 0이면 관리자
      user.memberAuth === "0" || // memberAuth가 문자 '0'이면 관리자
      user.role === "ADMIN" || // role이 ADMIN인 경우
      user.authority === "ADMIN" || // authority가 ADMIN인 경우
      (Array.isArray(user.roles) && user.roles.includes("ROLE_ADMIN")) // roles 배열에 ROLE_ADMIN 포함
    );
  };
  const isAdmin = checkAdmin(user);

  // 수정 모드일 경우 초기값 세팅 (title, content)
  useEffect(() => {
    if (isEdit) {
      setTitle(location.state?.title || "");
      setContent(location.state?.content || "");
    }
  }, [isEdit, location.state]);

  /**
   * 등록 또는 수정 처리 함수
   * 제목과 내용이 비어있으면 경고 후 종료
   * 로그인 정보 및 권한이 없으면 경고
   * API 호출 후 성공 시 알림 및 상세페이지로 이동
   * 실패 시 오류 알림
   */
  const handleSubmit = async () => {
    if (!title.trim() || !content.trim()) {
      alert("제목과 내용을 입력해주세요.");
      return;
    }

    if (!user?.memberNo) {
      alert("회원 정보가 올바르지 않습니다.");
      return;
    }

    // 서버에 보낼 데이터 객체 (필드명은 백엔드 DTO에 맞춤)
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
        // 새로 생성된 공지사항의 번호를 받아서 상세페이지로 이동
        navigate(`/announce/detail/${newPost.announceNo || newPost.id}`);
      }
    } catch (error) {
      console.error("공지사항 등록/수정 실패:", error);
      alert("오류가 발생했습니다.");
    }
  };

  // 로그인 정보 없으면 표시
  if (!user) return <div>로그인 정보가 없습니다.</div>;
  // 관리자 권한 없으면 표시
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
