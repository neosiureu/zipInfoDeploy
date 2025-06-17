// 관리자 문의 답변
import React, { useEffect, useState } from "react";
import axios from "axios";
import "../../css/admin/Inquiry.css";

const Inquiry = ({ admin, memberId, inquiryId }) => {
  const [inquiry, setInquiry] = useState(null);
  const [answer, setAnswer] = useState("");

  useEffect(() => {
    // 문의 내용 불러오기
    axios
      .get(`/api/inquiries/${inquiryId}`)
      .then((res) => {
        setInquiry(res.data);
      })
      .catch((err) => {
        console.error("문의 내용을 불러오는 데 실패했습니다.", err);
      });
  }, [inquiryId]);

  const handleSubmit = () => {
    // 답변 제출 로직 (예: POST /api/inquiries/{id}/answer)
    axios
      .post(`/api/inquiries/${inquiryId}/answer`, { answer })
      .then(() => {
        alert("답변이 등록되었습니다.");
      })
      .catch((err) => {
        console.error("답변 등록 실패:", err);
        alert("답변 등록 중 오류가 발생했습니다.");
      });
  };

  if (!inquiry) {
    return <div className="loading-text">문의 내용을 불러오는 중입니다...</div>;
  }

  return (
    <div className="inquiry-container">
      <h2 className="inquiry-title">중개 회원 권한 발급</h2>

      <p className="inquiry-info">
        현재 <span>{admin}</span> 으로 접속 중입니다.
      </p>
      <p className="inquiry-id">
        접속 ID : <span>{memberId}</span>
      </p>

      <div className="border-box">
        <label>제목</label>
        <input
          type="text"
          placeholder="제목을 입력하세요"
          className="input-text"
          value={inquiry.title}
          readOnly
        />
      </div>

      <div className="border-box">
        <label>첨부파일</label>
        {inquiry.attachment ? (
          <a
            href={inquiry.attachment.url}
            target="_blank"
            rel="noopener noreferrer"
            className="attachment-link"
          >
            {inquiry.attachment.name}
          </a>
        ) : (
          <p className="no-attachment">업로드된 파일이 없습니다.</p>
        )}
      </div>

      <div className="border-box">
        <label>문의 내용</label>
        <div className="content-text">{inquiry.content}</div>
      </div>

      <div className="border-box">
        <label>답변</label>
        <textarea
          placeholder="내용을 입력하세요."
          className="textarea-answer"
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
        />
      </div>

      <div className="flex justify-end">
        <button onClick={handleSubmit} className="button-submit">
          답변하기
        </button>
      </div>
    </div>
  );
};

export default Inquiry;
