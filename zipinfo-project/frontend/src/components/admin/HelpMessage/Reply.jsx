// src/components/admin/HelpMessage/Reply.jsx
import React, { useState, useEffect } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import "../../../css/admin/HelpMessage/Reply.css";
import { axiosAPI } from "./../../../api/axiosApi";

const Reply = () => {
  const { messageNo } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const receiverNo = queryParams.get("senderNo");

  const [message, setMessage] = useState(null); // 문의 상세 데이터
  const [reply, setReply] = useState(""); // 답변 내용
  const [loading, setLoading] = useState(true);

  // 문의 상세 및 답변 데이터 불러오기
  useEffect(() => {
    if (!messageNo) {
      alert("잘못된 접근입니다: messageNo가 없습니다.");
      navigate("/admin/helpMessage");
      return;
    }

    const fetchMessage = async () => {
      try {
        setLoading(true);
        // 문의 상세 조회 API (detail 대신 reply 경로)
        const res = await axiosAPI.get(
          `/api/help/reply?messageNo=${messageNo}`
        );
        console.log("📦 API 응답 전체:", res.data);
        setMessage(res.data);
        setReply(res.data.replyContent || "");
        console.log("📦 message 상태 설정 완료:", res.data);
        console.log("📎 첨부파일 정보:", {
          fileNo: res.data.fileNo,
          fileOriginName: res.data.fileOriginName,
          fileRename: res.data.fileRename,
          fileUrl: res.data.fileUrl,
        });
      } catch (err) {
        console.error("문의 불러오기 실패:", err);
        alert("문의 상세를 불러오는데 실패했습니다.");
        navigate("/admin/helpMessage");
      } finally {
        setLoading(false);
      }
    };

    fetchMessage();
  }, [messageNo, navigate]);

  // 답변 제출 함수
  const handleSubmit = async () => {
    if (!reply.trim()) {
      alert("답변 내용을 입력하세요.");
      return;
    }
    if (!receiverNo) {
      alert("수신자 정보가 없습니다.");
      return;
    }

    try {
      await axiosAPI.post("/api/help/reply", {
        messageContent: reply,
        receiverNo: parseInt(receiverNo, 10),
        inquiredNo: parseInt(messageNo, 10),
      });
      alert("답변이 등록되었습니다.");
      navigate("/admin/helpMessage"); // 답변 후 문의 목록으로 이동
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
          <label className="form-label col-label">제목</label>
          <input
            type="text"
            value={message.messageTitle}
            readOnly
            className="form-input"
          />
        </div>

        {/* 문의 내용 */}
        <div className="form-group">
          <label className="form-label">문의 내용</label>
          <div
            className="form-textarea col-content"
            style={{ whiteSpace: "pre-wrap" }}
            dangerouslySetInnerHTML={{ __html: message.messageContent }}
          />
        </div>

        {/* 첨부 파일 */}
        <div className="form-group">
          <label className="form-label">첨부 파일</label>
          <div className="col-content">
            {message.fileOriginName ? (
              <>
                <span>업로드된 파일 : </span>
                <a href={message.fileUrl} download={message.fileOriginName}>
                  {message.fileOriginName}
                </a>
              </>
            ) : null}
          </div>
        </div>

        {/* 답변 작성 */}
        <div className="form-group row">
          <label className="form-label col-label">답변 작성</label>
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
          <button onClick={handleSubmit} className="submit-btn">
            답변 하기
          </button>
        </div>
      </div>
    </div>
  );
};

export default Reply;
