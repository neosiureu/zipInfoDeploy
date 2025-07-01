import React, { useState, useContext, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { AuthContext } from "../admin/AuthContext";
import "../../css/announce/AnnounceWrite.css";
import { createPost, updatePost, fetchPostDetail } from "../../api/AnnounceApi";

const AnnounceWrite = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  // 수정 여부 및 postId
  const postId = location.state?.id;
  const isEdit = !!postId;

  // 제목/내용 상태
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");

  // 관리자 여부
  const isAdmin = user && String(user.memberAuth) === "0";

  // 권한 확인
  // useEffect(() => {
  //   if (!user) {
  //     alert("로그인이 필요합니다.");
  //     navigate("/login");
  //   } else if (!isAdmin) {
  //     alert("관리자만 접근할 수 있습니다.");
  //     navigate("/announce");
  //   }
  // }, [user, isAdmin, navigate]);

  // 수정일 경우 기존 데이터 가져오기
  useEffect(() => {
    if (isEdit) {
      console.log("location.state:", location.state);
      if (!location.state?.announceTitle || !location.state?.announce) {
        fetchPostDetail(postId)
          .then((data) => {
            setTitle(data.announceTitle || "");
            setContent(data.announce || "");
          })
          .catch(() => {
            alert("공지사항을 불러오지 못했습니다.");
            navigate("/announce");
          });
      } else {
        setTitle(location.state.announceTitle);
        setContent(location.state.announce);
      }
    }
  }, [isEdit, postId, location.state, navigate]);

  // 등록 또는 수정 요청
  const handleSubmit = async () => {
    if (!title.trim() || !content.trim()) {
      alert("제목과 내용을 입력해주세요.");
      return;
    }

    if (!user?.memberNo) {
      alert("회원 정보가 없습니다.");
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
    } catch (err) {
      console.error("공지사항 등록/수정 실패:", err);
      alert("작업에 실패했습니다.");
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

      {/* 버튼 */}
      <div className="announce-write-buttons">
        <button type="button" onClick={handleSubmit}>
          {isEdit ? "수정 완료" : "등록"}
        </button>
      </div>
    </div>
  );
};

export default AnnounceWrite;
