import { Link, useNavigate, useLocation } from "react-router-dom";
import { useContext } from "react";
import logo from "../../assets/logo.svg";
import "../../css/common/Header.css";
import { MemberContext } from "../member/MemberContext";
import { axiosAPI } from "../../api/axiosApi";
import { toast } from "react-toastify";

const naverLogout = () => {
  return new Promise((resolve) => {
    try {
      const iframe = document.createElement("iframe");
      iframe.style.display = "none";
      iframe.src = "https://nid.naver.com/nidlogin.logout";
      document.body.appendChild(iframe);
      setTimeout(() => {
        if (document.body.contains(iframe)) {
          document.body.removeChild(iframe);
        }
        resolve();
      }, 3000);
    } catch (error) {
      console.error("네이버 로그아웃 처리 중 오류:", error);
      resolve();
    }
  });
};

const Header = () => {
  const { member, setMember } = useContext(MemberContext);
  const navigate = useNavigate();
  const location = useLocation(); // 현재 경로 확인

  const handleLogout = async () => {
    try {
      navigate("/");
      /* 1) 서버에 access-token 그대로 들고 로그아웃 요청 */
      const { data } = await axiosAPI.post("/member/logout"); // 헤더에 Bearer 토큰 자동 첨부

      const tasks = [];
      // if (data.naverLogoutRequired === "true") tasks.push(naverLogout());
      if (data.kakaoLogoutRequired === "true" && window.Kakao?.Auth)
        tasks.push(new Promise((r) => window.Kakao.Auth.logout(r)));
      Promise.allSettled(tasks); // 기다리지 않음
    } catch (e) {
      console.warn("logout api fail", e);
    }
    /* 3) 로컬 스토리지·컨텍스트 정리 */
    if (window.stompClient?.connected) window.stompClient.disconnect();

    localStorage.removeItem("accessToken");
    localStorage.removeItem("loginMember");
    localStorage.removeItem("com.naver.nid.access_token");
    localStorage.removeItem("com.naver.nid.oauth.state_token");

    // kakao_ 로 시작하는 모든 키 삭제
    Object.keys(localStorage)
      .filter((key) => key.startsWith("kakao_"))
      .forEach((key) => localStorage.removeItem(key));
    setMember(null);
  };

  const handleNavMyStock = () => {
    if (localStorage.getItem("loginMember") === null) {
      toast.error("로그인 후 이용하시길 바랍니다.");
      return;
    }
    navigate("/myStock");
  };

  return (
    <header className="Header-navbar">
      <div className="Header-navbar-left">
        <span className="Header-logo">
          <Link to="/">
            <img src={logo} alt="로고이미지" />
          </Link>
        </span>
        <ul className="Header-menu">
          <li
            className={location.pathname.startsWith("/stock") ? "active" : ""}
          >
            <Link to="/stock">실거래가</Link>
          </li>
          <li className={location.pathname.startsWith("/sale") ? "active" : ""}>
            <Link to="/sale">분양</Link>
          </li>
          <li
            className={
              location.pathname.startsWith("/neighborhoodBoard") ? "active" : ""
            }
          >
            <Link to="/neighborhoodBoard?cp=1">우리동네</Link>
          </li>
          <li
            className={
              location.pathname.startsWith("/announce") ? "active" : ""
            }
          >
            <Link to="/announce">공지사항</Link>
          </li>
          {member?.memberAuth !== 0 && (
            <li
              className={
                location.pathname.startsWith("/myStock") ? "active" : ""
              }
            >
              {member?.memberAuth === 3 ? (
                <Link to="/myStock">관심목록</Link>
              ) : (
                <Link to="/sawStock">최근 본 매물</Link>
              )}
            </li>
          )}
          {member?.memberAuth === 0 && (
            <li
              className={
                location.pathname.startsWith("/gonggong") ? "active" : ""
              }
            >
              <Link to="/gonggong">공공데이터 샘플 삽입</Link>
            </li>
          )}
        </ul>
      </div>

      <div className="Header-navbar-right">
        <ul className="Header-member">
          {member ? (
            member.memberAuth === 0 ? (
              <>
                <li id="admin-page" className="Header-admin-page">
                  <Link to="/admin">관리자 페이지</Link>
                </li>
                <li id="logout-btn">
                  <button className="Header-logout-btn" onClick={handleLogout}>
                    로그아웃
                  </button>
                </li>
              </>
            ) : (
              <>
                {member.memberAuth === 3 && (
                  <li id="add-stock" className="Header-stock-add-page">
                    <Link to="/addStock">매물 등록</Link>
                  </li>
                )}
                <li id="my-page" className="Header-my-page">
                  <Link to="/myPage">마이페이지</Link>
                </li>
                <li id="logout-btn">
                  <button className="Header-logout-btn" onClick={handleLogout}>
                    로그아웃
                  </button>
                </li>
              </>
            )
          ) : (
            <>
              <li id="signup-btn">
                <Link to="/signUp" className="Header-signup-btn">
                  회원가입
                </Link>
              </li>
              <li id="login-btn">
                <Link to="/login" className="Header-login-btn">
                  로그인
                </Link>
              </li>
            </>
          )}
        </ul>
      </div>
    </header>
  );
};

export default Header;
