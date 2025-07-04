import React, { useState, useEffect } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import "../../../css/admin/HelpMessage/Reply.css";
import { axiosAPI } from "./../../../api/axiosApi";
import { toast } from "react-toastify";

const Reply = () => {
  const { messageNo } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const receiverNo = queryParams.get("senderNo");
  const viewOnly = queryParams.get("viewOnly") === "true";

  const [inquiry, setInquiry] = useState(null);
  const [reply, setReply] = useState("");
  const [loading, setLoading] = useState(true);
  const [isEdit, setIsEdit] = useState(false);
  const [replyMessageNo, setReplyMessageNo] = useState(null);

  useEffect(() => {
    console.log("URL에서 받은 messageNo: ", messageNo); // ✅ 확인용 출력
    if (!messageNo) {
      toast.error("잘못된 접근입니다.");
      navigate("/admin/helpMessage");
      return;
    }

    setLoading(true);
    axiosAPI
      .get(`/api/help/reply?messageNo=${messageNo}`)
      .then((res) => {
        console.log("서버 응답: ", res.data);
        setInquiry(res.data); // 문의글 세팅
        setReply(res.data.replyContent || ""); // 답변 내용 세팅
      })
      .catch(() => {
        toast.error("문의 정보를 불러오는 중 오류가 발생했습니다.");
        navigate("/admin/helpMessage");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [messageNo]);

  const handleSubmit = async () => {
    if (!reply.trim()) {
      toast.error("답변 내용을 입력하세요.");
      return;
    }
    if (!receiverNo) {
      toast.error("수신자 정보가 없습니다.");
      return;
    }

    try {
      await axiosAPI.post("/api/help/reply", {
        messageTitle: inquiry.messageTitle,
        messageContent: reply,
        senderNo: 1, // 관리자 번호 1로 고정
        receiverNo: parseInt(receiverNo, 10),
        inquiredNo: parseInt(messageNo, 10),
      });
      toast.success("답변이 등록되었습니다.");
      navigate("/admin/helpMessage");
    } catch (err) {
      toast.error("제출 중 오류가 발생하였습니다.");
    }
  };

  if (loading) {
    return <div className="form-container">로딩 중...</div>;
  }
  if (!inquiry) {
    return <div className="form-container">문의 정보를 찾을 수 없습니다.</div>;
  }

  return (
    <div className="form-container">
      <h2 className="form-title">문의 상세</h2>

      <div className="form-box">
        <div className="form-label-col">제목</div>
        <div className="form-content-col">
          <input
            type="text"
            value={inquiry.messageTitle}
            readOnly
            className="form-input"
          />
        </div>
      </div>

      <div className="form-box">
        <div className="form-label-col">첨부 파일</div>
        <div className="form-content-col">
          {inquiry.fileOriginName ? (
            <a
              href={inquiry.fileUrl}
              download={inquiry.fileOriginName}
              className="download-link"
            >
              {inquiry.fileOriginName}
            </a>
          ) : (
            <span>첨부 파일이 없습니다.</span>
          )}
        </div>
      </div>

      <div className="form-box">
        <div className="form-label-col">문의내용</div>
        <div className="form-content-col">
          <div
            className="form-textarea-view"
            dangerouslySetInnerHTML={{ __html: inquiry.messageContent }}
          />
        </div>
      </div>

      <div className="form-box">
        <div className="form-label-col">답변</div>
        <div className="form-content-col">
          {viewOnly ? (
            <div style={{ whiteSpace: "pre-wrap" }}>
              {reply || "등록된 답변이 없습니다."}
            </div>
          ) : (
            <textarea
              value={reply}
              onChange={(e) => setReply(e.target.value)}
              className="form-textarea"
              placeholder="답변을 입력하세요"
              rows={8}
            />
          )}
        </div>
      </div>

      {!viewOnly && (
        <div className="form-actions">
          <button className="submit-btn" onClick={handleSubmit}>
            {isEdit ? "답변 수정" : "답변하기"}
          </button>
        </div>
      )}
    </div>
  );
};

export default Reply;
