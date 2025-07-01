import { Link, useNavigate } from "react-router-dom";
import { useContext } from "react";
import logo from "../../assets/logo.svg";
import "../../css/common/Header.css";
import { MemberContext } from "../member/MemberContext";
import { axiosAPI } from "../../api/axiosApi";
const naverLogout = () => {
  return new Promise((resolve) => {
    try {
      const iframe = document.createElement("iframe");
      iframe.style.display = "none";
      iframe.src = "https://nid.naver.com/nidlogin.logout";
      document.body.appendChild(iframe);

      // 3초 후 오류로 발생하는 iframe 제거 및 완료 처리
      setTimeout(() => {
        if (document.body.contains(iframe)) {
          document.body.removeChild(iframe);
        }
        resolve();
      }, 3000);
    } catch (error) {
      console.error("네이버 로그아웃 처리 중 오류:", error);
      resolve(); // 에러가 발생해도 계속 진행
    }
  });
};

const Header = () => {
  const { member, setMember } = useContext(MemberContext);
  const navigate = useNavigate();

  // 로그아웃 => 로컬스토리지 초기화
  const handleLogout = async () => {
    // 1) 백엔드 세션 = 제이세션 아이디 무효화
    try {
      if (member?.memberLogin === "N") await naverLogout();
      // Spring 컨트롤러에 만든 로그아웃 엔드포인트 (예: /member/logout)
      await axiosAPI.post("/member/logout");
      localStorage.removeItem("com.naver.nid.access_token");
      localStorage.removeItem("com.naver.nid.oauth.state_token");
    } catch (_) {}
    // 2) 카카오 SDK 로그아웃
    if (window.Kakao && window.Kakao.Auth) {
      window.Kakao.Auth.logout(() => {
        console.log("Kakao SDK 로그아웃 완료");
      });
    }

    // 3) 스토리지 초기화
    setMember(null);
    localStorage.removeItem("loginMember");
    localStorage.removeItem("com.naver.nid.access_token");
    localStorage.removeItem("com.naver.nid.oauth.state_token");
    alert("로그아웃 되었습니다");
    navigate("/");
  };

  const handleNavMyStock = () => {
    if (localStorage.getItem("loginMember") === null) {
      alert("로그인 후 이용하시길 바랍니다.");
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
          <li>
            <Link to="/stock">실거래가</Link>
          </li>
          <li>
            <Link to="/sale">분양</Link>
          </li>
          <li>
            <Link to="/announce">공지사항</Link>
          </li>
          <li>
            <Link to="/neighborhoodBoard">우리동네</Link>
          </li>
          <li onClick={handleNavMyStock}>관심목록</li>
          {member?.memberAuth == 0 ? (
            <>
              <li>
                <Link to="/gonggong">공공데이터 샘플 삽입</Link>
              </li>
            </>
          ) : null}
        </ul>
      </div>

      <div className="Header-navbar-right">
        <ul className="Header-member">
          {member ? (
            member.memberAuth == 0 ? (
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
