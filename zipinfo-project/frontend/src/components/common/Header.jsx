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
    console.log("로그아웃 전 saveId:", localStorage.getItem("saveId"));

    try {
      // 1) 서버 통보
      const { data } = await axiosAPI.post("/member/logout"); // access-token 첨부됨

      const tasks = [];
      if (data.naverLogoutRequired === "true") tasks.push(naverLogout());
      if (data.kakaoLogoutRequired === "true" && window.Kakao?.Auth)
        tasks.push(new Promise((r) => window.Kakao.Auth.logout(r)));
      Promise.allSettled(tasks); // 기다릴 필요 없음
    } catch (e) {
      console.warn("logout api fail", e);
    }

    // 2) **먼저** 홈으로 이동 – replace 옵션을 주면 히스토리를 깔끔하게 덮어씁니다
    sessionStorage.setItem("justLoggedOut", "yes");
    navigate("/", { replace: true });

    // 3) 다음 tick(= ProtectedRoute가 언마운트된 이후)에 member 를 비워서
    //    ProtectedRoute의 useEffect가 더 이상 실행될 일이 없게 만듭니다.
    setTimeout(() => {
      if (window.stompClient?.connected) window.stompClient.disconnect();

      delete axiosAPI.defaults.headers.common.Authorization;
      localStorage.removeItem("accessToken");
      localStorage.removeItem("loginMember");
      localStorage.removeItem("com.naver.nid.access_token");
      localStorage.removeItem("com.naver.nid.oauth.state_token");

      setMember(null);
    }, 0);
  };

  const handleNavMyStock = () => {
    if (localStorage.getItem("loginMember") === null) {
      toast.error("로그인 후 이용하시길 바랍니다.");
      return;
    }
    navigate("/myPage/myStock");
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
            <Link to="/neighborhoodBoard">우리동네</Link>
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
                location.pathname.startsWith("/myPage/myStock") ? "active" : ""
              }
            >
              <Link to="/myPage/myStock">관심목록</Link>
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
