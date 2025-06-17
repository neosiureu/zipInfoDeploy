import React, { useState } from 'react';
import '../../css/myPage/myInfo.css';
import { useNavigate } from 'react-router-dom';

const MyPage = () => {
  const nav = useNavigate;

  const [activeTab, setActiveTab] = useState('내 정보');

  const tabs = ['내 정보', '내 매물', '문의하기', '내가 쓴 글', '비밀번호 재설정', '회원탈퇴'];

  const userInfo = {
    id: 'always_lily1009',
    nickname: '죽음을 먹는 자',
    phone: '0101234567',
    address: '부평이·123',
    description: '영국 스크랩북도 고치며 크리번드로 탈, 호그와트 마법학교',
    interests: '치어 1종 교수형'
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
                  onClick={() => setActiveTab(tab)}
                  className={`tab-button ${activeTab === tab ? 'active' : 'inactive'}`}
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
              <div className="info-value">{userInfo.id}</div>
            </div>

            {/* Nickname */}
            <div className="info-field">
              <label className="info-label">이름</label>
              <div className="info-value">세종박스 스마이프</div>
            </div>

            {/* Additional Info */}
            <div className="info-field">
              <label className="info-label">닉네임</label>
              <input value={userInfo.nickname} className="info-value"/>
            </div>

            {/* Phone */}
            <div className="info-field">
              <label className="info-label">전화번호</label>
              <input value={userInfo.phone} className="info-value"/>
            </div>

            {/* Address */}
            <div className="info-field">
              <label className="info-label">주소</label>
              <input value={userInfo.address} className="info-value"/>
            </div>

            {/* Description */}
            <div className="info-field">
              <label className="info-label">설명</label>
              <input value={userInfo.description} className="info-value"/>
            </div>

            {/* Interests */}
            <div className="info-field">
              <label className="info-label">관심사</label>
              <input value={userInfo.interests} className="info-value"/>
            </div>
          </div>

          {/* Edit Button */}
          <div className="edit-button-container">
            <button className="edit-button">편집하기</button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MyPage;