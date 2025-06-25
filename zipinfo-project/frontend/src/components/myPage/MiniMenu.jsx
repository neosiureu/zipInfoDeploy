import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

export default function Menu(){

    const nav = useNavigate();

    const location = useLocation();

    const tabs = [
      {label: "등록한 매물", path: "/myPage/myStock"},
      {label: "매물 등록", path: "/myPage/addStock"},
      {label: "최근 본 매물", path: "/myPage/sawStock"},
      {label: "찜 매물", path: "/myPage/lickStock"}
    ];
    
    const getInitialTab = () => {
      const currentTab = tabs.find(tab => tab.path === location.pathname);
      return currentTab ? currentTab.label : "등록한 매물";
    };

    const [activeTab, setActiveTab] = useState(getInitialTab);
  

    useEffect(() => {
      const currentTab = tabs.find(tab => tab.path === location.pathname);
      if (currentTab) {
        setActiveTab(currentTab.label);
      }
    }, [location.pathname]);

  return(
          <div className="my-page-stock-sub-tab-container">
            <div className="my-page-stock-sub-tab-nav">
              {tabs.map((tab) => (
                <button
                  key={tab.label}
                  onClick={() => {
                    setActiveTab(tab.label);
                    nav(tab.path);
                  }}
                  className={`my-page-stock-sub-tab-btn ${
                    activeTab === tab.label ? "active" : "inactive"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

  )
}