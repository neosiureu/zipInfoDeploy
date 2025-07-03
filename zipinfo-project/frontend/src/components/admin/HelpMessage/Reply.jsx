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

  const [inquiry, setInquiry] = useState(null); // 문의 내용
  const [reply, setReply] = useState(""); // 답변 내용
  const [loading, setLoading] = useState(true);
  const [isEdit, setIsEdit] = useState(false); // 답변 수정 모드 여부
  const [replyMessageNo, setReplyMessageNo] = useState(null); // 수정 시 답변 메시지 번호

  useEffect(() => {
    if (!messageNo) {
      alert("잘못된 접근입니다: messageNo가 없습니다.");
      navigate("/admin/helpMessage");
      return;
    }

    const fetchMessage = async () => {
      try {
        setLoading(true);

        const res = await axiosAPI.get(
          `/api/help/reply?messageNo=${messageNo}`
        );

        if (res.data.replyYn === "Y" && res.data.replyMessageNo) {
          const viewRes = await axiosAPI.post("/api/help/reply/view", {
            messageNo: res.data.replyMessageNo,
          });

          setInquiry(viewRes.data.original);
          setReply(viewRes.data.reply.messageContent);
          setIsEdit(true);
          setReplyMessageNo(viewRes.data.reply.messageNo);

          console.log("API original data:", viewRes.data.original);
        } else {
          // 답변 없으면 원글 그대로
          setInquiry(res.data);
          setReply("");
          setIsEdit(false);
          setReplyMessageNo(null);
        }
      } catch (err) {
        alert("문의 상세를 불러오는데 실패했습니다.");
        navigate("/admin/helpMessage");
      } finally {
        setLoading(false);
      }
    };

    fetchMessage();
  }, [messageNo, navigate]);

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
      if (isEdit && replyMessageNo) {
        // 수정 API 호출 (PUT 또는 POST - 수정용)
        await axiosAPI.put("/api/help/reply", {
          messageNo: replyMessageNo,
          messageContent: reply,
        });
        alert("답변이 수정되었습니다.");
      } else {
        // 새 답변 등록 API 호출
        await axiosAPI.post("/api/help/reply", {
          messageTitle: inquiry.messageTitle, // 추가
          messageContent: reply,
          receiverNo: parseInt(receiverNo, 10),
          inquiredNo: parseInt(messageNo, 10),
        });
        alert("답변이 등록되었습니다.");
      }
      navigate("/admin/helpMessage");
    } catch (err) {
      alert(
        isEdit
          ? "답변 수정 중 오류가 발생했습니다."
          : "답변 등록 중 오류가 발생했습니다."
      );
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
      <h2 className="form-title">문의 상세 및 답변</h2>

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
          <textarea
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            className="form-textarea"
            placeholder="답변을 입력하세요"
            rows={8}
          />
        </div>
      </div>

      <div className="form-actions">
        <button className="submit-btn" onClick={handleSubmit}>
          {isEdit ? "답변 수정" : "답변하기"}
        </button>
      </div>
    </div>
  );
};

export default Reply;
