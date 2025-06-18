import React, { useEffect, useState } from "react";
import "../../css/myPage/myInfo.css";
import { useNavigate } from "react-router-dom";
import { axiosAPI } from "../../api/axiosApi";
import Menu from "./Menu";


const MyPage = () => {
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

  const handleNav = () => {
    nav("/myPage/updateInfo" , {user, setUser})
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
              <div className="info-value">{user.Nickname != null ? user.Nickname:"닉네임을 설정하지 않았습니다"}</div>
            </div>

            {/* Phone */}
            <div className="info-field">
              <label className="info-label">사무소 이름</label>
              <div className="info-value">{user.companyName}</div>
            </div>

            {/* Address */}
            <div className="info-field">
              <label className="info-label">사무소 주소</label>
              <div className="info-value">{user.companyLocation}</div>
            </div>

            {/* Description */}
            <div className="info-field">
              <label className="info-label">대표명</label>
              <div className="info-value">{user.presidentName}</div>
            </div>

            {/* Interests */}
            <div className="info-field">
              <label className="info-label">대표 번호</label>
              <div className="info-value">{user.presidentPhone}</div>
            </div>

                        {/* Interests */}
            <div className="info-field">
              <label className="info-label">중개등록번호</label>
              <div className="info-value">{user.brokerNo}</div>
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
              <div className="info-value">{user.Nickname != null ? user.Nickname:"닉네임을 설정하지 않았습니다"}</div>
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

export default MyPage;
