import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

export default function Menu(){

    const nav = useNavigate();

    const [activeTab, setActiveTab] = useState("내 정보");
  
    const tabs = [
      {label: "내 정보", path: "/myPage"},
      {label: "관심 매물", path: "/myPage/myStock"},
      {label: "문의내역", path: "/myPage/myAnnounce"},
      {label: "내가 쓴 글", path: "/myPage/myPost"},
      {label: "비밀번호 재설정", path: "/myPage/updatePassword"},
      {label: "회원탈퇴", path: "/myPage/withDraw"}
    ];

    useEffect(() => {
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