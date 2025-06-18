import React, { useEffect, useState } from 'react';
// import '../../css/myPage/myInfo.css';
import { useLocation, useNavigate } from 'react-router-dom';
import Menu from "./Menu";
import { axiosAPI } from '../../api/axiosApi';

const UpdateInfo = () => {
  const nav = useNavigate();

  const location = useLocation();
  const { user } = location.state || {}; // 안전하게 fallback 처리

  const memberAuth = user.memberAuth;

  const [updateUser, setUpdateUser] = useState(user || {});

  const handleUserInfo = (e) => {
    const { name, value } = e.target;

    setUpdateUser((prev) => ({
      ...prev,
      [name]: value
    }));
  };

  async function updateInfo() {

    try {
      const response = await axiosAPI.post("/myPage/updateInfo", updateUser
      );

      if (response.status === 200) {
        alert(
          '수정이 완료되었습니다.'
        );
      }

      nav("/myPage")
    } catch (error) {
      alert(error);
      // 409, 500 일 때 응답 받은 body 내용이 반영되어 alert 출력할 수 있게 한다
    }

  }




  const frag = memberAuth == 3 ?  

        <div className="my-page-profile-card">
          <div className="my-page-profile-info">
            {/* User ID */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">아이디</label>
              <div className="my-page-info-value">{updateUser.memberEmail}</div>
            </div>

            {/* Nickname */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">이름</label>
              <div className="my-page-info-value">{updateUser.memberName}</div>
            </div>

            {/* Additional Info */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">닉네임</label>
              <input onChange={handleUserInfo} name='memberNickname' value={updateUser.memberNickname != null ? updateUser.memberNickname:'닉네임을 설정하지 않았습니다'} className="my-page-input"/>
            </div>

            {/* Phone */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">사무소 이름</label>
              <input onChange={handleUserInfo} name='companyName' value={updateUser.companyName} className="my-page-input"/>
            </div>

            {/* Address */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">사무소 주소</label>
              <input onChange={handleUserInfo} name='companyLocation' value={updateUser.companyLocation} className="my-page-input"/>
            </div>

            {/* Description */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">대표명</label>
              <input onChange={handleUserInfo} name='presidentName' value={updateUser.presidentName} className="my-page-input"/>
            </div>

            {/* Interests */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">대표 번호</label>
              <input onChange={handleUserInfo} name='presidentPhone' value={updateUser.presidentPhone} className="my-page-input"/>
            </div>

                        {/* Interests */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">중개등록번호</label>
              <input onChange={handleUserInfo} name='brokerNo' value={updateUser.brokerNo} className="my-page-input"/>
            </div>
          </div>

          {/* Edit Button */}
          <div className="my-page-edit-button-container">
            <button
              onClick={() => updateInfo()}
              className="my-page-edit-button"
            >
              편집하기
            </button>
          </div>
        </div>

    :  // ------------------------------------------------------------------------------------------여기부터 삼항 뒷 부분
        <div className="my-page-profile-card">
          <div className="my-page-profile-info">
            {/* User ID */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">아이디</label>
              <div className="my-page-info-value">{updateUser.memberEmail}</div>
            </div>

            {/* Nickname */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">이름</label>
              <div className="my-page-info-value">{updateUser.memberName}</div>
            </div>

            {/* Additional Info */}
            <div className="my-page-info-field">
              <label className="my-page-info-label">닉네임</label>
              <input onChange={handleUserInfo} name='memberNickname' value={updateUser.memberNickname != null ? updateUser.memberNickname:"닉네임을 설정하지 않았습니다"} className="my-page-input"/>
            </div>

                      {/* Edit Button */}
          <div className="my-page-edit-button-container">
            <button
              onClick={() => updateInfo()}
              className="my-page-edit-button"
            >
              편집하기
            </button>
          </div>
          </div>
        </div>

  return (
    <div className="my-page-my-page">
      <div className="my-page-my-page-container">
        <Menu/>
        {frag}
      </div>
    </div>
  );
};

export default UpdateInfo;