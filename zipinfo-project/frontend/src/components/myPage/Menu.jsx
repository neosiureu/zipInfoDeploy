import { useContext, useEffect, useState, useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { MemberContext } from "../member/MemberContext";

export default function Menu() {
  const nav = useNavigate();
  const location = useLocation();
  const { member } = useContext(MemberContext);

  // 카카오 로그인 여부
  const isKakao = Object.keys(localStorage).some((k) => k.startsWith("kakao_"));
  const isNaver  = member?.memberLogin === "OAuth" || member?.memberLogin === "N";
 const isSocial = isKakao || isNaver;  // 네이버랑 카카오 말고 확장성을 위해
  // 전체 탭
  const tabs = [
    { label: "내 정보", path: "/myPage" },
    { label: "관심 매물", path: "/myPage/myStock" },
    { label: "문의내역", path: "/myPage/myMessage" },
    { label: "내가 쓴 글", path: "/myPage/myPost" },
    { label: "비밀번호 재설정", path: "/myPage/updatePassword" },
    { label: "회원탈퇴", path: "/myPage/withDraw" },
  ];

  // 카카오 사용자는 비밀번호 탭 제외
 const visibleTabs = useMemo(
    () => (isSocial ? tabs.filter(t => t.label !== "비밀번호 재설정") : tabs),
    [isSocial]
  );

  /** 현재 URL 에 맞춰 초기 활성 탭 선택 */
  const [activeTab, setActiveTab] = useState(() => {
    const cur = visibleTabs.find((t) => t.path === location.pathname);
    return cur ? cur.label : "내 정보";
  });

  /** 주소가 바뀌면 활성 탭 동기화 */
  useEffect(() => {
    const cur = visibleTabs.find((t) => t.path === location.pathname);
    if (cur) setActiveTab(cur.label);
  }, [location.pathname, visibleTabs]);

  return (
    <div className="my-page-page-title">
      <h1>마이페이지</h1>

      <div className="my-page-tab-navigation">
        <div className="my-page-tab-container">
          {visibleTabs.map((tab) => (
            <button
              key={tab.label}
              className={`my-page-tab-button ${
                activeTab === tab.label ? "active" : "inactive"
              }`}
              onClick={() => {
                setActiveTab(tab.label);
                nav(
                  tab.label === "관심 매물"
                    ? member?.memberAuth !== 3
                      ? "/myPage/sawStock"
                      : "/myPage/myStock"
                    : tab.path
                );
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
