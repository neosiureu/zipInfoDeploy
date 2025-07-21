import { useContext, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { MemberContext } from "../member/MemberContext";

export default function Menu() {
  const nav = useNavigate();

  const location = useLocation();

  const { member } = useContext(MemberContext);

  const tabs = [
    { label: "등록한 매물", path: "/myStock" },
    { label: "매물 등록", path: "/addStock" },
    { label: "최근 본 매물", path: "/sawStock" },
    { label: "찜 매물", path: "/likeStock" },
  ];

  const tabss = [
    { label: "최근 본 매물", path: "/sawStock" },
    { label: "찜 매물", path: "/likeStock" },
  ];

  const getInitialTab = () => {
    const currentTab = tabs.find((tab) => tab.path === location.pathname);
    return currentTab ? currentTab.label : "등록한 매물";
  };

  const [activeTab, setActiveTab] = useState(getInitialTab);

  useEffect(() => {
    const currentTab = tabs.find((tab) => tab.path === location.pathname);
    if (currentTab) {
      setActiveTab(currentTab.label);
    }
  }, [location.pathname]);

  return member.memberAuth === 3 ? (
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
  ) : (
    <div className="my-page-stock-sub-tab-container">
      <div className="my-page-stock-sub-tab-nav">
        {tabss.map((tab) => (
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
  );
}
