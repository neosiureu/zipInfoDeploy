import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

export default function Menu() {
  const nav = useNavigate();

  const location = useLocation();

  const tabs = [
    { label: "문의하기", path: "/myMessage" },
    { label: "문의 내역", path: "/seeMyMessage" },
  ];

  const getInitialTab = () => {
    if (location.pathname.startsWith("/detailMessage")) {
      return "문의 내역"; // 직접 지정
    }

    const currentTab = tabs.find((tab) =>
      location.pathname.startsWith(tab.path)
    );
    return currentTab ? currentTab.label : "문의하기";
  };

  const [activeTab, setActiveTab] = useState(getInitialTab);

  useEffect(() => {
    if (location.pathname.startsWith("/detailMessage")) {
      setActiveTab("문의 내역");
      return;
    }

    const currentTab = tabs.find((tab) =>
      location.pathname.startsWith(tab.path)
    );
    if (currentTab) {
      setActiveTab(currentTab.label);
    }
  }, [location.pathname]);

  return (
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
  );
}
