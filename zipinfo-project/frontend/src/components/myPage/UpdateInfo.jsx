import React, { useEffect, useState } from 'react';
import '../../css/myPage/myInfo.css';
import { useNavigate } from 'react-router-dom';
import Menu from "./Menu";

const UpdateInfo = () => {
  const nav = useNavigate();


  const [user, setUser] = useState([]);

  async function getMemberInfo() {
    try {
      const resp = await axiosAPI.get("/myPage/memberInfo",{withCredentials: true});

      if (resp.status === 200) {
        setUser(resp.data);
      }
    } catch (error) {
      console.log("Member Info 불러오는 중 에러 발생 : ", error);
    }
  }

  useEffect(() => {
    getMemberInfo();
  }, []);

  const memberAuth = user.memberAuth;

  const frag = memberAuth == 3 ?  

        <div className="profile-card">
          <div className="profile-info">
            {/* User ID */}
            <div className="info-field">
              <label className="info-label">아이디</label>
              <div className="info-value">{user.memberEmail}</div>
            </div>

            {/* Nickname */}
            <div className="info-field">
              <label className="info-label">이름</label>
              <div className="info-value">{user.memberName}</div>
            </div>

            {/* Additional Info */}
            <div className="info-field">
              <label className="info-label">닉네임</label>
              <input value={user.Nickname != null ? user.Nickname:'닉네임을 설정하지 않았습니다'} className="info-value"/>
            </div>

            {/* Phone */}
            <div className="info-field">
              <label className="info-label">사무소 이름</label>
              <input value={user.companyName} className="info-value"/>
            </div>

            {/* Address */}
            <div className="info-field">
              <label className="info-label">사무소 주소</label>
              <input value={user.companyLocation} className="info-value"/>
            </div>

            {/* Description */}
            <div className="info-field">
              <label className="info-label">대표명</label>
              <input value={user.presidentName} className="info-value"/>
            </div>

            {/* Interests */}
            <div className="info-field">
              <label className="info-label">대표 번호</label>
              <input value={user.presidentPhone} className="info-value"/>
            </div>

                        {/* Interests */}
            <div className="info-field">
              <label className="info-label">중개등록번호</label>
              <input value={user.brokerNo} className="info-value"/>
            </div>
          </div>

          {/* Edit Button */}
          <div className="edit-button-container">
            <button
              onClick={() => nav("/myPage/updateInfo")}
              className="edit-button"
            >
              편집하기
            </button>
          </div>
        </div>

    :  // ------------------------------------------------------------------------------------------여기부터 삼항 뒷 부분
        <div className="profile-card">
          <div className="profile-info">
            {/* User ID */}
            <div className="info-field">
              <label className="info-label">아이디</label>
              <div className="info-value">{user.memberEmail}</div>
            </div>

            {/* Nickname */}
            <div className="info-field">
              <label className="info-label">이름</label>
              <div className="info-value">{user.memberName}</div>
            </div>

            {/* Additional Info */}
            <div className="info-field">
              <label className="info-label">닉네임</label>
              <input value={user.Nickname != null ? user.Nickname:"닉네임을 설정하지 않았습니다"} className="info-value"/>
            </div>

                      {/* Edit Button */}
          <div className="edit-button-container">
            <button
              onClick={() => nav("/myPage/updateInfo")}
              className="edit-button"
            >
              편집하기
            </button>
          </div>
          </div>
        </div>

  return (
    <div className="my-page">
      <div className="my-page-container">
        <Menu/>
        {frag}
      </div>
    </div>
  );
};

export default UpdateInfo;