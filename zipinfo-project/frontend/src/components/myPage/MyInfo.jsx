import React, { useEffect, useState } from "react";
import "../../css/myPage/myInfo.css";
import { useNavigate } from "react-router-dom";
import { axiosAPI } from "../../api/axiosApi";

const MyPage = () => {
  const nav = useNavigate();

  const [activeTab, setActiveTab] = useState("내 정보");

  const tabs = [
    "내 정보",
    "관심 매물",
    "등록하기",
    "내가 쓴 글",
    "바람직한 제품관",
    "마켓컬처",
  ];

  const [user, setUser] = useState([]);

  async function getMemberInfo() {
    try {
      const resp = await axiosAPI.get("/myPage/memberInfo");

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

  const userInfo = {
    id: "always_lily1009",
    nickname: "죽음을 먹는 자",
    phone: "0101234567",
    address: "부평이·123",
    description: "영국 스크랩북도 고치며 크리번드로 탈, 호그와트 마법학교",
    interests: "치어 1종 교수형",
  };

  return (
    <div className="my-page">
      <div className="my-page-container">
        {/* Page Title */}
        <div className="page-title">
          <h1>마이페이지</h1>

          {/* Tab Navigation */}
          <div className="tab-navigation">
            <div className="tab-container">
              {tabs.map((tab) => (
                <button
                  key={tab}
                  onClick={() => {
                    setActiveTab(tab);
                  }}
                  className={`tab-button ${
                    activeTab === tab ? "active" : "inactive"
                  }`}
                >
                  {tab}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* User Profile Card */}
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
              <div className="info-value">{user.Nickname}</div>
            </div>

            {/* Phone */}
            <div className="info-field">
              <label className="info-label">전화번호</label>
              <div className="info-value">{userInfo.phone}</div>
            </div>

            {/* Address */}
            <div className="info-field">
              <label className="info-label">주소</label>
              <div className="info-value">{userInfo.address}</div>
            </div>

            {/* Description */}
            <div className="info-field">
              <label className="info-label">설명</label>
              <div className="info-value">{userInfo.description}</div>
            </div>

            {/* Interests */}
            <div className="info-field">
              <label className="info-label">관심사</label>
              <div className="info-value">{userInfo.interests}</div>
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
      </div>
    </div>
  );
};

export default MyPage;
