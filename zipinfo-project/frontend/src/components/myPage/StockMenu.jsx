import { useContext, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { MemberContext } from "../member/MemberContext";

export default function Menu() {
  const nav = useNavigate();

  const location = useLocation();

  const { member } = useContext(MemberContext);
  const isKakao = Object.keys(localStorage).some((k) => k.startsWith("kakao_"));
 const isNaver  = member?.memberLogin === "OAuth" || member?.memberLogin === "N";
 const isSocial = isKakao || isNaver;  // 네이버랑 카카오 말고 확장성을 위해
  const tabs = [
    { label: "내 정보", path: "/myPage" },
    { label: "관심 매물", path: "/myPage/myStock" },
    { label: "문의내역", path: "/myPage/myMessage" },
    { label: "내가 쓴 글", path: "/myPage/myPost" },
    { label: "비밀번호 재설정", path: "/myPage/updatePassword" },
    { label: "회원탈퇴", path: "/myPage/withDraw" },
  ];

  const getInitialTab = () => {
    if (
      location.pathname.startsWith("/myPage/myMessage") ||
      location.pathname.startsWith("/myPage/seeMyMessage") ||
      location.pathname.startsWith("/myPage/detailMessage")
    ) {
      return "문의내역";
    }

    const currentTab = tabs.find((tab) => tab.path === location.pathname);
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

    const currentTab = tabs.find((tab) => tab.path === location.pathname);
    if (currentTab) {
      setActiveTab(currentTab.label);
    }
  }, [location.pathname]);

  return (
    <div className="my-page-page-title">
      <h1>마이페이지</h1>

      {/* Tab Navigation */}
      <div className="my-page-tab-navigation">
        <div className="my-page-tab-container">
          {tabs.map((tab) =>
            isSocial && tab.label  === "비밀번호 재설정" ? null : ( // 소셜 사용자는 숨김
              <button
                key={tab.label}
                onClick={() => {
                  setActiveTab(tab.label);

                  if (tab.label === "관심 매물") {
                    if (member.memberAuth !== 3) {
                      nav("/myPage/sawStock");
                    } else {
                      nav("/myPage/myStock");
                    }
                  } else {
                    nav(tab.path);
                  }
                }}
                className={`my-page-tab-button ${
                  activeTab === tab.label ? "active" : "inactive"
                }`}
              >
                {tab.label}
              </button>
            )
          )}
        </div>
      </div>
    </div>
  );
}
