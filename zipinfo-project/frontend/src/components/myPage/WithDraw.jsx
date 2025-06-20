import React, { useContext, useState } from 'react';
import "../../css/myPage/withDraw.css";
import "../../css/myPage/menu.css";
import Menu from "./Menu";
import { useNavigate } from 'react-router-dom';
import { axiosAPI } from '../../api/axiosApi';
import { MemberContext } from '../member/MemberContext';

const PasswordChange = () => {
  const nav = useNavigate();

  const { member, setMember } = useContext(MemberContext);

  const [password, setPassword] = useState("");

  const [agreed, setAgreed] = useState(false);

  const handleCheck = (e) => {
    setAgreed(e.target.checked);
  };

  const handlePass = (e) =>{
    setPassword(e.target.value);
  }

  const handleWithdrawal= async ()=>{

    try {
      if(password.trim().length === 0){
        alert('비밀번호를 입력하세요.');
        return;
      }

      if(!agreed){
        alert('약관에 동의해주세요.');
        return;
      }

      const response = await axiosAPI.post("/myPage/checkPassword", {memberPw : password});
  
      if (response.status === 200 && response.data === 1) {
        const resp = await axiosAPI.get("/myPage/withDraw"
        );
        if(resp.status === 200 && resp.data === 1){
          alert('회원 탈퇴가 완료되었습니다.');
          setMember(null);
          localStorage.removeItem("loginMember");
          nav("/");
        }
      }else{
          alert('비밀번호가 일치하지 않습니다.');
          setPassword("");
        }
      
    } catch (error) {
      console.log(error);
    }
  }

  return (
    <div className="my-page">
        <div className="my-page-container">
      
          <Menu/>

        <div className='with-draw-container'>
        <div className="with-draw-password-section">
            <div className="with-draw-section-title">비밀번호</div>
            <input type="password" onChange={handlePass} value={password} className="with-draw-password-input" placeholder="비밀번호를 입력해주세요"/>
        </div>

        <div className="with-draw-work-section">
            <div className="with-draw-section-title">회원 탈퇴 약관</div>
            <div className="with-draw-work-intro">
            <div className="with-draw-work-item">
              회원은 언제든지 탈퇴를 요청할 수 있으며, 탈퇴 즉시 서비스 이용이 중단됩니다.
            </div>
            <div className="with-draw-work-item">
              탈퇴 시 회원님의 개인정보는 관계 법령 및 개인정보처리방침에 따라 일정 기간 후 안전하게 파기됩니다.
            </div>
            <div className="with-draw-work-item">
              회원 탈퇴 후 작성한 게시물 및 댓글은 삭제되지 않으며, 삭제를 원하실 경우 반드시 사전에 별도 요청하셔야 합니다.
            </div>
            <div className="with-draw-work-item">
              탈퇴 시 보유 중인 포인트, 쿠폰, 구독 정보 등은 즉시 소멸되며, 복구가 불가능합니다.
            </div>
            <div className="with-draw-work-item">
              탈퇴한 계정은 동일 이메일로 일정 기간 재가입이 제한될 수 있습니다.
            </div>
            <div className="with-draw-work-item">
              탈퇴 후에는 기존 데이터 및 이용 기록(예: 구매내역, 작성글 등)에 대한 열람이 불가능합니다.
            </div>
            <div className="with-draw-work-item">
              진행 중인 거래나 문의가 있는 경우, 해당 처리가 완료되기 전에는 탈퇴가 제한될 수 있습니다.
            </div>
            <div className="with-draw-work-item">
              회원 탈퇴는 즉시 처리되며 철회가 불가능하므로 신중하게 결정해 주시기 바랍니다.
          </div>
        </div>
        </div>

        <div className="with-draw-agreement-section">
            <div className="with-draw-agreement-item">
            <input
              type="checkbox"
              id="agree"
              className="with-draw-agreement-checkbox"
              checked={agreed}
              onChange={handleCheck}
            />
            <label htmlFor="agree" className="with-draw-agreement-label"></label>
            <span className='withdraw-span'>위 약관에 동의합니다</span>
            </div>
        </div>

        <button className="withdrawal-btn" onClick={handleWithdrawal}>탈퇴하기</button>
        </div>
        </div>
    </div>
  );
};

export default PasswordChange;