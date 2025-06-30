import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

export default function Menu(){

    const nav = useNavigate();

    const location = useLocation();

    const tabs = [
      {label: "내 정보", path: "/myPage"},
      {label: "관심 매물", path: "/myPage/myStock"},
      {label: "문의내역", path: "/myPage/myMessage"},
      {label: "내가 쓴 글", path: "/myPage/myPost"},
      {label: "비밀번호 재설정", path: "/myPage/updatePassword"},
      {label: "회원탈퇴", path: "/myPage/withDraw"}
    ];

    
    const getInitialTab = () => {
      if (
        location.pathname.startsWith("/myPage/myMessage") ||
        location.pathname.startsWith("/myPage/seeMyMessage") ||
        location.pathname.startsWith("/myPage/detailMessage")
      ) {
        return "문의내역";
      }
      
      const currentTab = tabs.find(tab => tab.path === location.pathname);
      return currentTab ? currentTab.label : "관심 매물";
    };

    const [activeTab, setActiveTab] = useState(getInitialTab);
    
useEffect(() => {
  if (
    location.pathname.startsWith("/myPage/myMessage") ||
    location.pathname.startsWith("/myPage/seeMyMessage") ||
    location.pathname.startsWith("/myPage/detailMessage")
  ) {
    setActiveTab("문의내역");
    return;
  }

  const currentTab = tabs.find(tab => tab.path === location.pathname);
  if (currentTab) {
    setActiveTab(currentTab.label);
  }
}, [location.pathname]);

  return(
          <div className="my-page-page-title">
            <h1>마이페이지</h1>

            {/* Tab Navigation */}
            <div className="my-page-tab-navigation">
              <div className="my-page-tab-container">
                {tabs.map((tab) => (
                  <button
                    key={tab.label}
                    onClick={() => {
                      setActiveTab(tab.label);
                      nav(tab.path);
                    }}
                    className={`my-page-tab-button ${
                      activeTab === tab.label ? "active" : "inactive"
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>
          </div>
  )
}
