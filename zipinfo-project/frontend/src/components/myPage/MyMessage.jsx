import { useEffect, useState } from 'react';
import "../../css/myPage/myMessage.css";
import StockMenu from "./StockMenu";
import MessageMenu from "./MessageMenu";
import { useNavigate } from 'react-router-dom';
import { axiosAPI } from '../../api/axiosAPI';


export default function MyStock() {

  const [message, setMessage] = useState({
    messageTitle: '',
    messageContent: '',
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setMessage(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e) => {
    const response = await axiosAPI.post('/myPage/sendMessage',message)
    if(response.status === 200){
      console.log(response.data);
      alert('문의가 성공적으로 전송되었습니다.');
    }
  };


  return (
    <div className="my-page-my-stock">
      <div className="my-page-my-stock-container">
        <StockMenu />
        <MessageMenu />

      <div className="my-message-contact-container">
      <div className="my-message-contact-title"><span className="my-message-span">zipInfo에 궁금하신 점을 문의해주세요.</span></div>
      <div className='my-message-title'>
        <span className="my-message-span">문의내용과 답변은</span>
        <span className="my-message-span-blue">'문의내역'</span>
        <span className="my-message-span">에서 확인하실 수 있습니다.</span>
      </div>
      
      <div className="my-message-contact-form" onSubmit={handleSubmit}>
        <div className="my-message-form-section">
            <div className="my-message-form-label">제목</div>
            <input
              name="messageTitle"
              value={message.messageTitle}
              onChange={handleChange}
              placeholder="제목을 입력하세요"
              className="my-message-form-input"
            />
        </div>

          <div className="my-message-content">
            <div className="my-message-content-label">내용</div>
            <div className="my-message-textarea-container">
              <textarea
                name="messageContent"
                value={message.messageContent}
                onChange={handleChange}
                placeholder="내용을 입력하세요"
                className="my-message-form-textarea"
              />
            </div>
          </div>
        <div>
              <div className="my-message-file">
                <div className='my-message-form-label'>첨부파일</div>
                <div className="my-message-file-help">
                <span className="my-message-file-info">+ 첨부파일</span>
                  <div>지원 확장자: jpg, gif, png, zip, doc, hwp(최대5M)</div>
                  <div>악성코드나 개인정보가 포함된 파일은 업로드하지 마세요.</div>
                </div>
              </div>
        </div>

      </div>
        <button type="submit" className="my-message-submit-button">
          문의하기
        </button>
    </div>

      </div>
    </div>
  );

}
