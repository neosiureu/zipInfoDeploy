import React, { useState } from 'react';
import "../../css/myPage/updatePassword.css";
import Menu from "./Menu";
import { useNavigate } from 'react-router-dom';
import { axiosAPI } from '../../api/axiosApi';

const PasswordChange = () => {
  const nav = useNavigate();

  const [password, setPassword] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setPassword(prev => ({
      ...prev,
      [name]: value
    }));
  };


  const handleSubmit = async () => {

    try {
      if(password.newPassword !== password.confirmPassword){
        
        alert('새로운 비밀번호가 일치하지 않습니다.');

        setPassword({
          currentPassword: '',
          newPassword: '',
          confirmPassword: ''
        });
        return;
      }

      const response = await axiosAPI.post("/myPage/checkPassword", {memberPw : password.currentPassword}
      );
  
      if (response.status === 200 && response.data == 1) {
        const resp = await axiosAPI.post("/myPage/updatePassword", {memberPw : password.newPassword}
        );
        if(resp.status === 200){
          alert('비밀번호 변경이 완료되었습니다.');
          nav("/myPage")
        }
      }else{
          alert('현재 비밀번호가 일치하지 않습니다.');
          setPassword({
            currentPassword: '',
            newPassword: '',
            confirmPassword: ''
          });
        }
    } catch (error) {
      alert(error);
    }

  };

  return (
    <div className="my-page">
        <div className="my-page-container">
      
          <Menu/>

          {/* Password Change Form */}
          <div className="pass-password-form">
            <div className="pass-form-group">
              <label className="pass-form-label">기존 비밀번호</label>
              <input
                type="password"
                name="currentPassword"
                value={password.currentPassword}
                onChange={handleInputChange}
                placeholder="기존 비밀번호를 입력해주세요"
                className="pass-form-input"
              />
            </div>

            <div className="pass-form-group">
              <label className="pass-form-label">새 비밀번호</label>
              <input
                type="password"
                name="newPassword"
                value={password.newPassword}
                onChange={handleInputChange}
                placeholder="새 비밀번호를 입력해주세요"
                className="pass-form-input"
              />
            </div>

            <div className="pass-form-group">
              <label className="pass-form-label">새 비밀번호 확인</label>
              <input
                type="password"
                name="confirmPassword"
                value={password.confirmPassword}
                onChange={handleInputChange}
                placeholder="새 비밀번호를 확인해주세요"
                className="pass-form-input"
              />
            </div>

            <button onClick={handleSubmit} className="pass-submit-button">
              변경하기
            </button>
          </div>
        </div>
    </div>
  );
};

export default PasswordChange;