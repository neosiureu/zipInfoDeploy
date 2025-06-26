import React, { useState, useContext, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/announce/AnnounceWrite.css";
import { createPost, updatePost, fetchPostDetail } from "../../api/AnnounceApi";

const AnnounceWrite = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  // 수정 모드 여부 및 공지사항 ID (location.state에서 받음)
  const isEdit = !!location.state?.id;
  const postId = location.state?.id;

  // 제목과 내용 상태 관리
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");

  // 관리자 여부 체크 (memberAuth가 문자열 "0"일 때 관리자)
  const isAdmin = user && String(user.memberAuth) === "0";

  // 로그인 및 관리자 권한 체크 - 페이지 접근 제어
  useEffect(() => {
    if (!user) {
      alert("로그인이 필요합니다.");
      navigate("/login"); // 로그인 페이지로 이동
    } else if (!isAdmin) {
      alert("관리자만 접근할 수 있습니다.");
      navigate("/announce"); // 공지사항 목록 페이지로 이동
    }
  }, [user, isAdmin, navigate]);

  // 수정 모드일 경우, location.state에 데이터 없으면 API 호출해서 초기값 세팅
  useEffect(() => {
    if (isEdit) {
      // location.state에 제목, 내용이 없으면 fetch
      if (!location.state?.title || !location.state?.content) {
        fetchPostDetail(postId)
          .then((data) => {
            setTitle(data.announceTitle || "");
            setContent(data.announce || "");
          })
          .catch(() => {
            alert("공지사항 데이터를 불러오지 못했습니다.");
            navigate("/announce"); // 오류 시 목록 페이지로 이동
          });
      } else {
        // location.state에 데이터 있으면 바로 세팅
        setTitle(location.state.title);
        setContent(location.state.content);
      }
    }
  }, [isEdit, postId, location.state, navigate]);

  // 등록/수정 처리 함수
  const handleSubmit = async () => {
    // 제목, 내용 빈 값 체크
    if (!title.trim() || !content.trim()) {
      alert("제목과 내용을 입력해주세요.");
      return;
    }

    // 로그인 회원 번호 체크
    if (!user?.memberNo) {
      alert("회원 정보가 올바르지 않습니다.");
      return;
    }

    // 서버에 보낼 데이터 구성 (키 이름은 서버 DTO에 맞게)
    const postData = {
      announceTitle: title,
      announce: content,
      memberNo: user.memberNo,
    };

    try {
      if (isEdit) {
        // 수정 API 호출
        await updatePost(postId, postData);
        alert("공지사항이 수정되었습니다.");
        navigate(`/announce/detail/${postId}`); // 상세 페이지 이동
      } else {
        // 등록 API 호출
        const newPost = await createPost(postData);
        alert("공지사항이 등록되었습니다.");
        // 새로 등록된 공지사항 번호로 상세 페이지 이동
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

      {/* 제목 입력 */}
      <input
        type="text"
        className="announce-title-input"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="제목을 입력하세요"
      />

      {/* 내용 입력 */}
      <textarea
        className="announce-content-input"
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="내용을 입력하세요"
      />

      {/* 등록/수정 버튼 */}
      <div className="announce-write-buttons">
        <button type="button" onClick={handleSubmit}>
          {isEdit ? "수정 완료" : "등록"}
        </button>
      </div>
    </div>
  );
};

export default AnnounceWrite;
