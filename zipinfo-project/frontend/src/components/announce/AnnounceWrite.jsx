import React, { useState, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom"; // 수정 모드 데이터 접근용
import axios from "axios";
import SummernoteEditor from "../neighborhood/SummernoteEditor";
import "../../css/Announce/AnnounceWrite.css";
import { axiosAPI } from "../../api/axiosApi";
import { toast } from "react-toastify";

export default function AnnounceWrite() {
  const navigate = useNavigate();
  const location = useLocation(); // 수정 모드일 경우 데이터 접근용

  // 현재 URL 경로에 "/edit"가 있으면 수정 모드로 간주
  const isEditMode = location.pathname.includes("/edit");

  // 수정 모드로 들어왔을 때 기존 데이터(state로 전달된) 초기화
  const {
    id,
    title: initTitle = "",
    content: initContent = "",
  } = location.state || {};

  const [title, setTitle] = useState(initTitle);
  const [content, setContent] = useState(initContent);
  const [isSubmitting, setIsSubmitting] = useState(false);

  /**
   * 공지사항 내용 내 이미지 URL을 절대 경로로 변환하는 함수
   * (서버에서 이미지는 /images/announceImg/ 경로에 저장됨)
   * @param {string} html 공지사항 내용(HTML)
   * @returns {string} 서버 절대 경로가 포함된 HTML 문자열
   */
  const fixImageUrls = (html) => {
    if (!html) return html;
    // src="/images/announceImg/ -> src="http://localhost:8080/images/announceImg/
    return html.replace(
      /src="\/images\/announceImg\//g,
      'src="http://localhost:8080/images/announceImg/'
    );
  };

  // 공지사항 등록 또는 수정 처리 함수
  const onSubmit = async () => {
    if (!title.trim()) {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">제목을 입력해주세요.</div>
        </div>
      );
      return;
    }
    if (!content || content.trim() === "<p><br></p>") {
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">내용을 입력해주세요.</div>
        </div>
      );
      return;
    }

    setIsSubmitting(true);

    try {
      // 이미지 경로가 깨지는 문제 방지를 위해 내용 내 이미지 경로 절대경로로 변경
      const fixedContent = fixImageUrls(content);

      // 서버에 보낼 데이터 payload 구성
      const payload = {
        announceTitle: title,
        announce: fixedContent,
      };

      // 수정 모드이면 PUT 요청, 아니면 POST 요청
      if (isEditMode) {
        await axios.put(
          `http://localhost:8080/api/announce/edit/${id}`,
          payload,
          {
            withCredentials: true, // 쿠키 포함 옵션
          }
        );
        toast.success(
          <div>
            <div className="toast-success-title">공지사항 수정 알림!</div>
            <div className="toast-success-body">공지사항이 수정되었습니다.</div>
          </div>
        );
      } else {
        await axiosAPI.post(
          "http://localhost:8080/api/announce/write",
          payload,
          {
            withCredentials: true,
          }
        );
        toast.success(
          <div>
            <div className="toast-success-title">공지사항 등록 알림!</div>
            <div className="toast-success-body">공지사항이 등록되었습니다.</div>
          </div>
        );
        await axiosAPI.post("http://localhost:8080/announce");
      }

      // 완료 후 공지사항 목록 페이지로 이동
      navigate("/announce");
    } catch (error) {
      console.error(error);
      toast.error(
        <div>
          <div className="toast-error-title">오류 알림!</div>
          <div className="toast-error-body">
            공지사항 제출 중 오류가 발생했습니다.
          </div>
        </div>
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="announce-write-container">
      <h2>{isEditMode ? "공지사항 수정" : "공지사항 작성"}</h2>

      {/* 제목 입력 */}
      <input
        type="text"
        placeholder="제목을 입력하세요"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        className="announce-write-title-input"
        disabled={isSubmitting}
      />

      {/* Summernote 에디터 (내용 입력) */}
      <SummernoteEditor
        value={content}
        onChange={setContent}
        disabled={isSubmitting}
      />

      {/* 등록/수정 버튼 */}
      <button
        onClick={onSubmit}
        disabled={isSubmitting}
        className="announce-write-submit-button"
      >
        {isSubmitting
          ? isEditMode
            ? "수정 중..."
            : "등록 중..."
          : isEditMode
          ? "공지사항 수정"
          : "공지사항 등록"}
      </button>
    </div>
  );
}
