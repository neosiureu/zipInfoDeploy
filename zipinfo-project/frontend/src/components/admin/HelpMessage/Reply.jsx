// src/components/admin/HelpMessage/Reply.jsx
import React, { useState, useEffect } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import axios from "axios";
import "../../../css/admin/HelpMessage/Reply.css";
import { axiosAPI } from "./../../../api/axiosApi";

const Reply = () => {
  const { messageNo } = useParams();
  const navigate = useNavigate();

  const [message, setMessage] = useState(null); // 문의 상세
  const [reply, setReply] = useState(""); // 답변 내용
  const [loading, setLoading] = useState(true);

  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const receiverNo = queryParams.get("senderNo");

  const messageNumber = parseInt(messageNo);

  // 문의 상세 불러오기
  useEffect(() => {
    const fetchMessage = async () => {
      try {
        setLoading(true);
        const res = await axiosAPI.get(
          `/api/help/detail?messageNo=${messageNumber}`
        );
        console.log(
          "첨부파일 URL:",
          res.data.fileUrl,
          "파일명:",
          res.data.fileOriginName
        );
        console.log("문의 내용 HTML:", res.data.messageContent); // 🔍 이거 꼭 확인!
        setMessage(res.data);
        console.log("문의 내용 HTML:", res.data.messageContent);
        setReply(res.data.replyContent || "");
      } catch (err) {
        console.error("문의 불러오기 실패:", err);
        alert("문의 상세를 불러오는데 실패했습니다.");
      } finally {
        setLoading(false);
      }
    };
    fetchMessage();
  }, [messageNo]);

  // 답변 제출
  const handleSubmit = async (e) => {
    try {
      await axiosAPI.post("/api/help/reply", {
        messageContent: reply,
        receiverNo: parseInt(receiverNo),
        inquiredNo: parseInt(messageNo),
      });
      alert("답변이 등록되었습니다.");
      navigate("/admin/helpMessage"); // 답변 후 목록 페이지로 이동
    } catch (err) {
      console.error("답변 등록 실패:", err);
      alert("답변 등록 중 오류가 발생했습니다.");
    }
  };

  if (loading) {
    return <div className="form-container">로딩 중...</div>;
  }

  if (!message) {
    return <div className="form-container">문의 정보를 찾을 수 없습니다.</div>;
  }

  return (
    <div className="form-container">
      <div className="form-header">
        <h2 className="form-title">문의 상세 및 답변</h2>
      </div>

      <div className="form-content">
        {/* 제목 */}
        <div className="form-group">
          <label className="form-label">제목</label>
          <input
            type="text"
            value={message.messageTitle}
            readOnly
            className="form-input"
          />
        </div>

        {/* 문의 내용 출력 */}
        <div className="form-group">
          <label className="form-label">문의 내용</label>
          <div
            className="form-textarea"
            style={{
              whiteSpace: "pre-wrap",
              border: "1px solid #ccc",
              padding: "1rem",
            }}
            dangerouslySetInnerHTML={{ __html: message.messageContent }}
          />
        </div>

        {/* 첨부 파일 */}
        {message.fileUrl && (
          <div className="form-group">
            <label className="form-label">첨부 파일</label>
            <a
              href={message.fileUrl || "#"}
              download={message.fileOriginName || "파일명없음"}
              className="download-link"
              rel="noopener noreferrer"
            >
              {message.fileOriginName || "업로드된 파일"}
            </a>
          </div>
        )}

        {/* 답변 작성 */}
        <div className="form-group">
          <label className="form-label">답변 작성</label>
          <textarea
            name="reply"
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            placeholder="답변을 입력하세요"
            className="form-textarea"
            rows={10}
            required
          />
        </div>

        <div className="form-actions">
          <button onClick={() => handleSubmit()} className="submit-btn">
            답변 하기
          </button>
        </div>
      </div>
    </div>
  );
};

export default Reply;
